package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.puntotres.packinglist.persistence.MemoriaReferencias;
import com.puntotres.packinglist.testutil.PackingTallerExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel;

/**
 * El asistente de la entrada por taller: subir los dos excels, ajustar y
 * generar, con la vuelta atrás desde la revisión sin resubir nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PackingListTallerControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MemoriaReferencias memoria;

    /** Una sesión propia por test: el estado del taller es de sesión. */
    private MockHttpSession sesion;

    /**
     * Una referencia distinta por test. La memoria de referencias es una
     * tabla compartida por toda la clase, y una que se aprendiera en un test
     * dejaría de estar pendiente en el siguiente: el test pasaría por el
     * motivo equivocado.
     */
    private String referencia;

    @BeforeEach
    void nuevaSesion(org.junit.jupiter.api.TestInfo info) {
        sesion = new MockHttpSession();
        referencia = "REF-" + info.getTestMethod().orElseThrow().getName().toUpperCase();
    }

    private byte[] tallerConUnaReferencia(Integer unidadesPorCaja) {
        return PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", referencia, "KAKI", 31)
                        .conUnidadesPorCaja(unidadesPorCaja));
    }

    private byte[] pedidoDeAmi() {
        return PedidoAmiExcel.crear("EAN H26",
                PedidoAmiExcel.Fila.pedida(referencia, "KAKI", "U", "07001 CH", 20),
                PedidoAmiExcel.Fila.pedida(referencia, "KAKI", "U", "07002 JP", 10));
    }

    /**
     * El envío se comprueba por HTTP y con la misma sesión, como el resto de
     * tests de la web: el bean del envío en curso es de sesión, y uno
     * autoinyectado no sería el de la sesión de MockMvc.
     */
    private String revision() throws Exception {
        return mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void revisionVacia() throws Exception {
        mvc.perform(get("/revision").session(sesion))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/packing-list"));
    }

    private MockMultipartHttpServletRequestBuilder peticionDeDigerir(byte[] taller, byte[] pedido) {
        MockMultipartHttpServletRequestBuilder peticion =
                multipart("/packing-list/taller/digerir");
        peticion.session(sesion);
        if (taller != null) {
            peticion.file(new MockMultipartFile("excelTaller", "PACKING TALLER.xlsx",
                    null, taller));
        }
        if (pedido != null) {
            peticion.file(new MockMultipartFile("pedidoCliente", "EAN H26.xlsx", null, pedido));
        }
        return peticion;
    }

    private void digerirUnaEntrega(byte[] taller) throws Exception {
        mvc.perform(peticionDeDigerir(taller, pedidoDeAmi())
                        .param("modo", "TALLER").param("cliente", "AMI")
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "05/09/2026").param("fechaEnvio", "05/09/2026"))
                .andExpect(view().name("taller-ajuste"));
    }

    // --- Paso 1a ---

    @Test
    void laEntradaOfreceElModoTaller() throws Exception {
        mvc.perform(get("/packing-list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-modo=\"TALLER\"")));
    }

    @Test
    void digerirLlevaAlPasoDeAjuste() throws Exception {
        mvc.perform(peticionDeDigerir(tallerConUnaReferencia(8), pedidoDeAmi())
                        .param("modo", "TALLER").param("cliente", "AMI")
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "05/09/2026").param("fechaEnvio", "05/09/2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("taller-ajuste"))
                .andExpect(model().attributeExists("digestion"))
                .andExpect(content().string(containsString(referencia)))
                // La cabecera que dice de dónde salen esos números: sin ella
                // las columnas de destinación parecen un dato del taller.
                .andExpect(content().string(containsString("Cantidad para Cliente")));
    }

    @Test
    void unaReferenciaQueElPedidoNoReconoceTieneCasillasDondeTeclear() throws Exception {
        // El caso de la muestra o el color nuevo: no está en el excel de
        // pedido, pero se envía igual. Antes la fila salía sin ninguna
        // casilla editable y no había forma de mandar el género.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", referencia, "KAKI", 12).conUnidadesPorCaja(10));
        byte[] pedidoDeOtraCosa = PedidoAmiExcel.crear("EAN H26",
                PedidoAmiExcel.Fila.pedida("OTRA-REF", "KAKI", "U", "07001 CH", 20));

        mvc.perform(peticionDeDigerir(taller, pedidoDeOtraCosa)
                        .param("modo", "TALLER").param("cliente", "AMI")
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "05/09/2026").param("fechaEnvio", "05/09/2026"))
                .andExpect(view().name("taller-ajuste"))
                .andExpect(content().string(containsString("objetivos[CHINA]")));

        // Y lo tecleado en ellas se aplica: se genera con 12 a CHINA.
        mvc.perform(post("/packing-list/taller/generar").session(sesion)
                        .param("grupos[0].medidaCaja", "60x40x40")
                        .param("grupos[0].unidadesPorCaja", "10")
                        .param("grupos[0].filas[0].objetivos[CHINA]", "12"))
                .andExpect(redirectedUrl("/revision"));

        assertTrue(revision().contains(referencia));
    }

    @Test
    void cadaDestinacionEnsenaSuNumeroDePedidoYSePuedeCorregir() throws Exception {
        // El número sale del excel de pedido del cliente —el PO de AMI, el
        // Document d'achat de APC— y hasta ahora no se veía por ningún lado,
        // así que no había forma de corregirlo ni de ponerlo donde faltaba.
        mvc.perform(peticionDeDigerir(tallerConUnaReferencia(8), pedidoDeAmi())
                        .param("modo", "TALLER").param("cliente", "AMI")
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "05/09/2026").param("fechaEnvio", "05/09/2026"))
                .andExpect(view().name("taller-ajuste"))
                .andExpect(content().string(containsString("Cantidad taller")))
                .andExpect(content().string(containsString("pedidos[CHINA]")))
                .andExpect(content().string(containsString("pedidos[JAPAN]")))
                // El PO que trae el pedido de AMI, ya extraído y en pantalla.
                .andExpect(content().string(containsString("07001")))
                .andExpect(content().string(containsString("07002")))
                // Y rotulado con el vocabulario del cliente, no con el del
                // programa: quien teclea tiene delante el documento suyo.
                .andExpect(content().string(containsString("placeholder=")));
    }

    @Test
    void elPesoBrutoTecleadoEnElAjusteLlegaALaRevision() throws Exception {
        // El punto de tenerlo aquí: si se ha pesado una caja en el almacén, la
        // revisión ya no llega con las once casillas de peso en blanco.
        digerirUnaEntrega(tallerConUnaReferencia(8));

        // 20 a CHINA con 10 por caja: dos cajas llenas de 12,5 kg.
        mvc.perform(post("/packing-list/taller/generar").session(sesion)
                        .param("grupos[0].medidaCaja", "60x40x40")
                        .param("grupos[0].unidadesPorCaja", "10")
                        .param("grupos[0].pesoBrutoKg", "12.5"))
                .andExpect(redirectedUrl("/revision"));

        assertTrue(revision().contains("12.5"), "el peso bruto sale ya puesto en la revisión");
    }

    @Test
    void laPantallaDeAjusteOfreceElPesoBrutoYNoElOrigenDelDato() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(8));

        String html = mvc.perform(get("/packing-list/taller/ajuste").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("pesoBrutoKg"), "el campo de peso bruto, junto a las unidades");
        assertTrue(!html.contains("de memoria") && !html.contains("sin decidir"),
                "el indicador de origen se retiró: era ruido repetido en cada referencia");
    }

    @Test
    void elNumeroDePedidoTecleadoManda() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(8));

        // Solo el pedido, sin tocar la cantidad: un campo vacío es "no tocar".
        mvc.perform(post("/packing-list/taller/generar").session(sesion)
                        .param("grupos[0].medidaCaja", "60x40x40")
                        .param("grupos[0].unidadesPorCaja", "8")
                        .param("grupos[0].filas[0].pedidos[CHINA]", "09999"))
                .andExpect(redirectedUrl("/revision"));

        String html = revision();
        assertTrue(html.contains("09999"), "lo tecleado manda sobre lo que traía el excel");
        assertTrue(html.contains("07002"), "y no toca el de las demás destinaciones");
    }

    @Test
    void unaDestinacionSinPedidoEnElExcelAceptaElQueSeTeclea() throws Exception {
        // La referencia no está en el pedido, así que no trae ni cantidad ni
        // número: se teclean los dos y el envío sale con ellos.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", referencia, "KAKI", 12).conUnidadesPorCaja(10));
        byte[] pedidoDeOtraCosa = PedidoAmiExcel.crear("EAN H26",
                PedidoAmiExcel.Fila.pedida("OTRA-REF", "KAKI", "U", "07001 CH", 20));

        mvc.perform(peticionDeDigerir(taller, pedidoDeOtraCosa)
                        .param("modo", "TALLER").param("cliente", "AMI")
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "05/09/2026").param("fechaEnvio", "05/09/2026"))
                .andExpect(view().name("taller-ajuste"));

        mvc.perform(post("/packing-list/taller/generar").session(sesion)
                        .param("grupos[0].medidaCaja", "60x40x40")
                        .param("grupos[0].unidadesPorCaja", "10")
                        .param("grupos[0].filas[0].objetivos[CHINA]", "12")
                        .param("grupos[0].filas[0].pedidos[CHINA]", "07777"))
                .andExpect(redirectedUrl("/revision"));

        assertTrue(revision().contains("07777"));
    }

    @Test
    void lasFilasDeOtroClienteNoParanElEnvioNiEntranEnEl() throws Exception {
        // El taller manda una sola hoja con todos sus clientes mezclados.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", referencia, "KAKI", 31).conUnidadesPorCaja(10),
                PackingTallerExcel.Fila.de("PALOMA WOOL", "MOD-X", "ROUGE", 5)
                        .conUnidadesPorCaja(10));

        mvc.perform(peticionDeDigerir(taller, pedidoDeAmi())
                        .param("modo", "TALLER").param("cliente", "AMI")
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "05/09/2026").param("fechaEnvio", "05/09/2026"))
                .andExpect(view().name("taller-ajuste"))
                // Ni el material del otro cliente ni un aviso sobre él: que la
                // hoja traiga varios clientes es lo normal, y decirlo en cada
                // entrega solo tapaba los avisos que sí hay que leer.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("MOD-X"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("PALOMA WOOL"))));
    }

    @Test
    void sinExcelDeTallerSeVuelveALaEntradaConElError() throws Exception {
        mvc.perform(peticionDeDigerir(null, pedidoDeAmi())
                        .param("modo", "TALLER").param("cliente", "AMI")
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "05/09/2026").param("fechaEnvio", "05/09/2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"));
    }

    @Test
    void sinExcelDePedidoDeUnClienteQueLoUsaSeVuelveALaEntrada() throws Exception {
        mvc.perform(peticionDeDigerir(tallerConUnaReferencia(8), null)
                        .param("modo", "TALLER").param("cliente", "AMI")
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "05/09/2026").param("fechaEnvio", "05/09/2026"))
                .andExpect(view().name("entrada"));
    }

    @Test
    void sinLaHojaDeColisSeOfreceElegirlaAMano() throws Exception {
        mvc.perform(peticionDeDigerir(libroSinHojaDeColis(), pedidoDeAmi())
                        .param("modo", "TALLER").param("cliente", "AMI")
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "05/09/2026").param("fechaEnvio", "05/09/2026"))
                .andExpect(view().name("entrada"))
                .andExpect(model().attributeExists("hojasDelTaller"))
                .andExpect(content().string(containsString("FACTURA")));
    }

    // --- Paso 1b ---

    @Test
    void previsualizarDevuelveElResumenSinAvanzarDeEtapa() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(8));

        mvc.perform(post("/packing-list/taller/previsualizar").session(sesion)
                        .param("grupos[0].medidaCaja", "60x40x40")
                        .param("grupos[0].unidadesPorCaja", "8"))
                .andExpect(view().name("taller-ajuste"))
                .andExpect(model().attributeExists("resumen"));

        revisionVacia();
    }

    @Test
    void loTecleadoSeConservaAlPrevisualizar() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(8));

        mvc.perform(post("/packing-list/taller/previsualizar").session(sesion)
                        .param("grupos[0].medidaCaja", "60x40x30")
                        .param("grupos[0].unidadesPorCaja", "4"))
                .andExpect(content().string(containsString("60x40x30")))
                .andExpect(content().string(containsString("value=\"4\"")));
    }

    @Test
    void unaReferenciaSinUnidadesPorCajaBloqueaLaGeneracion() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(null));

        mvc.perform(post("/packing-list/taller/generar").session(sesion))
                .andExpect(view().name("taller-ajuste"))
                .andExpect(content().string(containsString(referencia)));

        revisionVacia();
    }

    // --- Generar y volver ---

    @Test
    void generarLlevaALaRevisionConElEnvioListo() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(8));

        mvc.perform(post("/packing-list/taller/generar").session(sesion)
                        .param("grupos[0].medidaCaja", "60x40x40")
                        .param("grupos[0].unidadesPorCaja", "8"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/revision"));

        // El pedido reparte esa referencia entre China y Japón: la revisión
        // tiene que enseñar las dos destinaciones.
        String html = revision();
        assertTrue(html.contains("CHINA"));
        assertTrue(html.contains("JAPAN"));
    }

    @Test
    void elEnvioDelTallerLlegaALaRevisionConLasFilasTenidasPorPalet() throws Exception {
        // La banda de color por palet es de la pantalla de revisión, no de una
        // vía de entrada concreta: el envío del taller tiene que llegar con
        // sus palets puestos igual que el JSON pegado a mano.
        digerirUnaEntrega(tallerConUnaReferencia(8));

        // Una unidad por caja: 20 cajas para CHINA, que no caben en un palet
        // (cuatro pilas de tres cajas de 40 cm en 147 cm útiles). Con dos
        // palets tienen que salir DOS bandas distintas; con una sola no se
        // vería si la banda va por palet o si está puesta a lo tonto.
        mvc.perform(post("/packing-list/taller/generar").session(sesion)
                        .param("grupos[0].medidaCaja", "60x40x40")
                        .param("grupos[0].unidadesPorCaja", "1"))
                .andExpect(redirectedUrl("/revision"));

        String html = revision();
        assertTrue(html.contains("palet-0"),
                "sin banda de color, el cambio de palet solo se ve leyendo la columna");
        assertTrue(html.contains("palet-1"), "el segundo palet cambia de matiz");
    }

    @Test
    void elEnvioGeneradoAvisaDeQueElRepartoNoEsDelTaller() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(8));

        mvc.perform(post("/packing-list/taller/generar").session(sesion)
                .param("grupos[0].medidaCaja", "60x40x40")
                .param("grupos[0].unidadesPorCaja", "8"));

        assertTrue(revision().contains("PACKING TALLER.xlsx"),
                "quien mira la revisión tiene que saber que el reparto lo ha hecho el programa");
    }

    @Test
    void generarRecuerdaLaReferenciaParaLaProximaEntrega() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(8));

        mvc.perform(post("/packing-list/taller/generar").session(sesion)
                        .param("grupos[0].medidaCaja", "60x40x45")
                        .param("grupos[0].unidadesPorCaja", "9"))
                .andExpect(redirectedUrl("/revision"));

        MemoriaReferencias.DatosCaja recordado =
                memoria.buscar("AMI", referencia).orElseThrow();
        assertEquals("60x40x45", recordado.medidaCaja());
        assertEquals(9, recordado.unidadesPorCaja());
    }

    @Test
    void seVuelveAlAjusteSinResubirLosFicheros() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(8));

        mvc.perform(get("/packing-list/taller/ajuste").session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("taller-ajuste"))
                .andExpect(model().attributeExists("digestion"));
    }

    @Test
    void volverAlAjusteConservaLoTecleado() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(8));
        mvc.perform(post("/packing-list/taller/previsualizar").session(sesion)
                .param("grupos[0].medidaCaja", "60x40x30")
                .param("grupos[0].unidadesPorCaja", "4"));

        mvc.perform(get("/packing-list/taller/ajuste").session(sesion))
                .andExpect(content().string(containsString("60x40x30")));
    }

    @Test
    void sinEntregaEnCursoElAjusteDevuelveALaEntrada() throws Exception {
        mvc.perform(get("/packing-list/taller/ajuste").session(new MockHttpSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/packing-list"));
    }

    // --- Fixtures ---

    private static byte[] libroSinHojaDeColis() {
        try (XSSFWorkbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            libro.createSheet("FACTURA");
            libro.createSheet("OTRA COSA");
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void repartirMasDeLoQueHaLlegadoNoDejaPasarDeLaPantallaDeAjuste() throws Exception {
        // Han llegado 31 y se mandan 20 + 20. No se puede empaquetar lo que no
        // está en el almacén, así que el envío se queda aquí.
        digerirUnaEntrega(tallerConUnaReferencia(8));

        mvc.perform(post("/packing-list/taller/generar").session(sesion)
                        .param("grupos[0].medidaCaja", "60x40x40")
                        .param("grupos[0].unidadesPorCaja", "8")
                        .param("grupos[0].filas[0].objetivos[CHINA]", "20")
                        .param("grupos[0].filas[0].objetivos[JAPAN]", "20"))
                .andExpect(status().isOk())
                .andExpect(view().name("taller-ajuste"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "no se puede enviar más de lo que ha llegado")))
                // El botón sigue vivo: el error se arregla tecleando en esta
                // misma pantalla y hay que poder volver a darle a Generar.
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("disabled"))));

        revisionVacia();
    }

    @Test
    void corregirElRepartoDeMasVuelveADejarGenerar() throws Exception {
        // El bloqueo se recalcula al pintar: si se guardara, seguiría ahí
        // después de corregirlo y la pantalla no tendría salida.
        digerirUnaEntrega(tallerConUnaReferencia(8));

        mvc.perform(post("/packing-list/taller/generar").session(sesion)
                        .param("grupos[0].medidaCaja", "60x40x40")
                        .param("grupos[0].unidadesPorCaja", "8")
                        .param("grupos[0].filas[0].objetivos[CHINA]", "20")
                        .param("grupos[0].filas[0].objetivos[JAPAN]", "20"))
                .andExpect(view().name("taller-ajuste"));

        mvc.perform(post("/packing-list/taller/generar").session(sesion)
                        .param("grupos[0].medidaCaja", "60x40x40")
                        .param("grupos[0].unidadesPorCaja", "8")
                        .param("grupos[0].filas[0].objetivos[CHINA]", "20")
                        .param("grupos[0].filas[0].objetivos[JAPAN]", "11"))
                .andExpect(redirectedUrl("/revision"));
    }
}
