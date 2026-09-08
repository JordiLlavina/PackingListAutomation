package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.service.etiquetas.AvisoEtiqueta;
import com.puntotres.packinglist.testutil.PedidoAmiExcel;

/**
 * Tests del asistente web con los beans reales (los servicios de dominio no
 * tienen dependencias externas y la plantilla AMI está en el classpath).
 * Usa el mismo JSON de fixture que EnvioImportServiceTest.
 *
 * Las taras van FIJADAS aquí y no se leen de application.yml: varios de
 * estos tests comprueban la aritmética de la inferencia (neto = bruto −
 * tara) y con las del yml el número esperado cambiaría cada vez que en el
 * almacén se pesa un cartón. La tabla de taras es un dato del negocio, no
 * una constante del programa; que se enlaza bien lo comprueba
 * PackingListApplicationTest. Los demás tamaños del yml siguen ahí: esto
 * solo sobrescribe el peso de estos dos, y "99x99x99" sigue sin tara.
 */
@SpringBootTest(properties = {
        "packing-list.taras.[60x40x40]=0.6",
        "packing-list.taras.[60x40x30]=0.2"})
@AutoConfigureMockMvc
class PackingListControllerTest {

    private static final String FICHERO_PARIS_USL728 =
            "2026.07.24_PUN_07685_USL728.AL217.NOIR_H26_FR.xlsx";

    @Autowired
    private MockMvc mvc;

    private String jsonDePrueba() throws Exception {
        try (var in = getClass().getResourceAsStream("/client-packinglist/packing_list_ami_test.json")) {
            return new String(in.readAllBytes());
        }
    }

    /** POST /importar con el JSON de fixture y cabecera válida. */
    private void importar(MockHttpSession sesion) throws Exception {
        mvc.perform(post("/importar").session(sesion)
                        .param("cliente", "AMI")
                        .param("json", jsonDePrueba())
                        .param("temporada", "H26")
                        .param("numeroFactura", "FA-26-1189")
                        .param("numeroComanda", "12345")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/revision"));
    }

    @Test
    void laPantallaDeEntradaRenderizaElFormularioConElDesplegableDeClientes() throws Exception {
        mvc.perform(get("/packing-list"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"))
                .andExpect(content().string(containsString("JSON del envío")))
                // El desplegable lista el catálogo de application.yml.
                .andExpect(content().string(containsString("selecciona un cliente")))
                .andExpect(content().string(containsString("A.P.C.")))
                .andExpect(content().string(containsString("Sonia Rykiel")));
    }

    @Test
    void importarConJsonValidoLlevaALaRevisionConSusAvisos() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importar(sesion);

        mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("revision"))
                // Los avisos de descuadre que el fixture contiene a propósito.
                .andExpect(content().string(containsString("1597")))
                .andExpect(content().string(containsString("1897")))
                // Las tres destinaciones con sus cajas.
                .andExpect(content().string(containsString("PARIS")))
                .andExpect(content().string(containsString("JAPAN")))
                .andExpect(content().string(containsString("CHINA")));
    }

    /**
     * El botón de recalcular peso solo sale donde ese peso sirve para calcular
     * los demás. La caja 1 mezcla dos artículos: se pinta en dos filas y
     * ninguna lo lleva; la caja 2 es de una línea y sí. Se comprueba sobre el
     * HTML renderizado porque es la capa donde vive la condición.
     */
    @Test
    void laRevisionNoOfreceRecalcularElPesoEnUnBultoConVariosArticulos() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        String json = """
                {"cliente":"AMI","destinos":[{"destino":"CHINA",
                 "palets":[{"palet":1,"cajaInicio":1,"cajaFin":2}],
                 "referencias":[
                  {"referencia":"ULL729.AL0103","color":"001","medidaCaja":"60x40x40",
                   "pedido":"07706","cantidadTotal":12,
                   "cajas":[{"caja":1,"unidades":12,"pesoBruto":6.3}]},
                  {"referencia":"ULL729.AL0103","color":"718","medidaCaja":"60x40x40",
                   "pedido":"07706","cantidadTotal":10,
                   "cajas":[{"caja":1,"unidades":10}]},
                  {"referencia":"USL737.AL0137","color":"001","medidaCaja":"60x40x40",
                   "pedido":"07713","cantidadTotal":25,
                   "cajas":[{"caja":2,"unidades":25,"pesoBruto":8.2}]}]}]}
                """;
        mvc.perform(post("/importar").session(sesion)
                        .param("cliente", "AMI").param("json", json)
                        .param("temporada", "H26").param("numeroFactura", "FA-26-1189")
                        .param("fechaFactura", "10/07/2026").param("fechaEnvio", "24/07/2026"))
                .andExpect(redirectedUrl("/revision"));

        String html = mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Tres filas (la caja mixta no se compacta) y un solo botón, el de la caja 2.
        assertEquals(1, contar(html, "recalcula peso"), html);
        // La líder del bulto mixto sigue editando su peso a mano: dos campos
        // de peso por caja física, tres cajas físicas... menos la no líder.
        assertEquals(4, contar(html, "cajas[0].pesoBrutoKg\"") + contar(html, "cajas[1].pesoBrutoKg\"")
                + contar(html, "cajas[2].pesoBrutoKg\"")
                + contar(html, "cajas[0].pesoNetoKg\"") + contar(html, "cajas[1].pesoNetoKg\"")
                + contar(html, "cajas[2].pesoNetoKg\""), html);
    }

    private static int contar(String texto, String fragmento) {
        int veces = 0;
        for (int i = texto.indexOf(fragmento); i >= 0; i = texto.indexOf(fragmento, i + 1)) {
            veces++;
        }
        return veces;
    }

    @Test
    void elClienteDelDesplegableMandaYNoSeAvisaDeQueElJsonDigaOtro() throws Exception {
        // El JSON dice "AMI" pero el desplegable selecciona ACKERMANN, y manda
        // el desplegable. Antes salía un aviso diciéndolo; se retiró porque el
        // cliente de los datos de entrada es informativo —de las fotos sale lo
        // que ponga el papel— y el aviso saltaba sin que hubiera nada que
        // hacer con él, empujando hacia abajo los que sí hay que leer.
        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(post("/importar").session(sesion)
                        .param("cliente", "ACKERMANN")
                        .param("json", jsonDePrueba())
                        .param("temporada", "SPRING 25")
                        .param("numeroFactura", "FA-1")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(redirectedUrl("/revision"));

        mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("has seleccionado"))));
    }

    @Test
    void importarEnModoFormularioUsaElJsonSerializadoEnElNavegador() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        // Lo que formulario.js serializa: siempre rangos (una caja suelta es
        // un rango de una sola caja) y solo los campos rellenados.
        String json = """
                {"cliente": "AMI", "destinos": [{"destino": "PARIS",
                  "palets": [{"palet": 1, "cajaInicio": 1, "cajaFin": 3}],
                  "referencias": [{"referencia": "USL728.AL217", "color": "NOIR",
                    "medidaCaja": "60x40x40", "cantidadTotal": 110,
                    "cajas": [{"cajaInicio": 1, "cajaFin": 2, "unidadesPorCaja": 50},
                              {"cajaInicio": 3, "cajaFin": 3, "unidadesPorCaja": 10}]}]}]}
                """;
        mvc.perform(post("/importar").session(sesion)
                        .param("modo", "FORMULARIO")
                        .param("cliente", "AMI")
                        .param("json", json)
                        .param("temporada", "H26")
                        .param("numeroFactura", "FA-1")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(redirectedUrl("/revision"));

        // Las 3 cajas expandidas (el rango de 1 incluido), sin avisos de
        // descuadre porque cantidadTotal cuadra (2*50 + 10 = 110).
        mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PARIS")))
                .andExpect(content().string(containsString("USL728.AL217")))
                .andExpect(content().string(not(containsString("no cuadra"))));
    }

    @Test
    void importarEnModoFormularioVacioExplicaElErrorSinHablarDeJson() throws Exception {
        mvc.perform(post("/importar")
                        .param("modo", "FORMULARIO")
                        .param("cliente", "AMI")
                        .param("temporada", "H26")
                        .param("numeroFactura", "FA-1")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"))
                .andExpect(model().attributeHasFieldErrors("envioForm", "json"))
                .andExpect(content().string(containsString("El formulario está vacío")));
    }

    @Test
    void importarSinClienteFallaLaValidacionDelCampo() throws Exception {
        mvc.perform(post("/importar")
                        .param("json", "{}")
                        .param("temporada", "H26")
                        .param("numeroFactura", "FA-1")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"))
                .andExpect(model().attributeHasFieldErrors("envioForm", "cliente"));
    }

    @Test
    void importarConJsonInvalidoVuelveALaEntradaConElTextoPreservado() throws Exception {
        mvc.perform(post("/importar")
                        .param("cliente", "AMI")
                        .param("json", "{esto no es json")
                        .param("temporada", "H26")
                        .param("numeroFactura", "FA-1")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"))
                .andExpect(model().attributeExists("errorJson"))
                .andExpect(content().string(containsString("{esto no es json")));
    }

    @Test
    void importarConFechaMalFormadaFallaLaValidacionDelCampo() throws Exception {
        mvc.perform(post("/importar")
                        .param("cliente", "AMI")
                        .param("json", "{}")
                        .param("temporada", "H26")
                        .param("numeroFactura", "FA-1")
                        .param("fechaFactura", "2026-07-10")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"))
                .andExpect(model().attributeHasFieldErrors("envioForm", "fechaFactura"))
                .andExpect(content().string(containsString("dd/MM/yyyy")));
    }

    @Test
    void revisionSinEnvioEnCursoRedirigeALaEntrada() throws Exception {
        mvc.perform(get("/revision"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/packing-list"));
    }

    @Test
    void recalcularConUnPesoManualInfiereElRestoDeSuReferencia() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importar(sesion);

        // Bruto de la caja 1 de PARIS (índice 0; 50 uds, 60x40x40, tara 0.6)
        // puesto a mano: peso unitario (50.6-0.6)/50 = 1.0 -> la caja 32
        // (47 uds) debe quedar con neto 47.0 y bruto 47.6.
        mvc.perform(post("/recalcular").session(sesion)
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "0")
                        .param("cajas[0].pesoBrutoKg", "50.6"))
                .andExpect(redirectedUrl("/revision"));

        mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"47.0\"")))
                .andExpect(content().string(containsString("value=\"47.6\"")));
    }

    @Test
    void recalcularConUnNetoManualTambienInfiereElResto() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importar(sesion);

        // Solo el NETO de la caja 1 de PARIS: unitario 50.0/50 = 1.0 ->
        // la caja 32 (47 uds) queda igual que en el caso del bruto, y la
        // propia caja 1 completa su bruto (50.0 + tara 0.6).
        mvc.perform(post("/recalcular").session(sesion)
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "0")
                        .param("cajas[0].pesoNetoKg", "50.0"))
                .andExpect(redirectedUrl("/revision"));

        mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"50.6\"")))
                .andExpect(content().string(containsString("value=\"47.0\"")));
    }

    @Test
    void editarUnaCajaMixtaDuplicadaAplicaElPesoALaFilaCorrecta() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importar(sesion);

        // En PARIS la caja 33 tiene DOS líneas (colores ROJO y NOIR): es UN
        // bulto con un solo peso, el de su primera línea (índice 32). El
        // formulario localiza la fila por posición, no por nº de caja.
        mvc.perform(post("/recalcular").session(sesion)
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "32")
                        .param("cajas[0].pesoNetoKg", "68.0"))
                .andExpect(redirectedUrl("/revision"));

        EnvioEnCurso envio = (EnvioEnCurso) sesion.getAttribute("scopedTarget.envioEnCurso");
        var cajasParis = envio.getImportado().getDestinos().get(0).getDestino().getCajas();
        assertEquals(30, cajasParis.get(32).getCantidad());       // la líder de la caja 33
        assertEquals(68.0, cajasParis.get(32).getPesoNetoKg());   // el valor manual
        // La segunda línea del mismo bulto no lleva peso propio.
        assertEquals(34, cajasParis.get(33).getCantidad());
        assertNull(cajasParis.get(33).getPesoNetoKg());
    }

    @Test
    void recalcularPropagaElPesoAOtrasDestinacionesConElMismoModelo() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        // El mismo modelo (R1/NOIR) en DOS destinaciones distintas. Se pesa
        // una caja en PARIS; como el peso por unidad es del producto (la
        // referencia), la caja de CHINA del mismo modelo también debe quedar
        // inferida. Antes la inferencia era por destinación y CHINA se
        // quedaba sin recalcular.
        String json = """
                {"cliente": "AMI", "destinos": [
                  {"destino": "PARIS",
                   "palets": [{"palet": 1, "cajaInicio": 1, "cajaFin": 1}],
                   "referencias": [{"referencia": "R1", "color": "NOIR",
                     "medidaCaja": "60x40x40", "pedido": "P1",
                     "cajas": [{"caja": 1, "unidades": 50}]}]},
                  {"destino": "CHINA",
                   "palets": [{"palet": 2, "cajaInicio": 10, "cajaFin": 10}],
                   "referencias": [{"referencia": "R1", "color": "NOIR",
                     "medidaCaja": "60x40x40", "pedido": "P1",
                     "cajas": [{"caja": 10, "unidades": 50}]}]}
                ]}
                """;
        mvc.perform(post("/importar").session(sesion)
                        .param("cliente", "AMI").param("json", json)
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "10/07/2026").param("fechaEnvio", "24/07/2026"))
                .andExpect(redirectedUrl("/revision"));

        // Neto a mano de la caja de PARIS (destino 0, índice 0): unitario
        // 50.0 / 50 = 1.0 kg.
        mvc.perform(post("/recalcular").session(sesion)
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "0")
                        .param("cajas[0].pesoNetoKg", "50.0"))
                .andExpect(redirectedUrl("/revision"));

        EnvioEnCurso envio = (EnvioEnCurso) sesion.getAttribute("scopedTarget.envioEnCurso");
        var cajaChina = envio.getImportado().getDestinos().get(1).getDestino().getCajas().get(0);
        assertEquals(50.0, cajaChina.getPesoNetoKg());   // inferida desde PARIS (50 uds * 1.0)
        assertEquals(50.6, cajaChina.getPesoBrutoKg());  // neto + tara 0.6 del 60x40x40
    }

    @Test
    void elEnvioConNumerosDeCajaRepetidosEntreDestinacionesInfiereCadaUna() throws Exception {
        // Regresión: el ejemplo real repite números de caja entre
        // destinaciones (caja 5 en CHINA y en JAPAN, cajas 1-4 en JAPAN y
        // FRANCE...). La inferencia debe tratar cada destinación por separado
        // y completar los netos de todas, no fusionarlas por nº de caja.
        MockHttpSession sesion = new MockHttpSession();
        String json;
        try (var in = getClass().getResourceAsStream("/ejemplos/envio-ami-bags-y-belts.json")) {
            json = new String(in.readAllBytes());
        }
        mvc.perform(post("/importar").session(sesion)
                        .param("cliente", "AMI").param("json", json)
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "10/07/2026").param("fechaEnvio", "24/07/2026"))
                .andExpect(redirectedUrl("/revision"));

        EnvioEnCurso envio = (EnvioEnCurso) sesion.getAttribute("scopedTarget.envioEnCurso");
        var cajasJapan = envio.getImportado().getDestinos().get(1).getDestino().getCajas();
        // JAPAN caja 5 (UBL214.AL0223 talla 75, bruto 5.2, tara 60x40x30 = 0.2):
        // su neto debe salir aunque CHINA también tenga una caja 5 del mismo
        // modelo (antes se perdía por la colisión de nº de caja).
        var japanCaja5 = cajasJapan.get(4);
        assertEquals(5, japanCaja5.getNumeroCaja());
        assertEquals(5.0, japanCaja5.getPesoNetoKg());   // 5.2 - 0.2
        // JAPAN caja 1 (ULL163, bruto 16.6) también, pese a que FRANCE tiene
        // sus propias cajas 1-5 del mismo modelo.
        assertEquals(16.0, cajasJapan.get(0).getPesoNetoKg());   // 16.6 - 0.6
    }

    @Test
    void enCajaMixtaPorTallaSoloLaPrimeraLineaMuestraCamposDePeso() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        // Cinturón con la caja 4 (una talla) y la caja 5 mixta (tres tallas):
        // 4 líneas pero solo 2 cajas físicas, luego 2 campos de peso.
        String json = """
                {"cliente": "AMI", "destinos": [{"destino": "PARIS",
                  "palets": [{"palet": 1, "cajaInicio": 4, "cajaFin": 5}],
                  "referencias": [
                    {"referencia": "UBL029.AL0216", "color": "001", "medidaCaja": "60x40x40",
                     "pedido": "07672", "talla": "75", "cajas": [{"caja": 4, "unidades": 45}]},
                    {"referencia": "UBL029.AL0216", "color": "001", "medidaCaja": "60x40x40",
                     "pedido": "07672", "talla": "85", "cajas": [{"caja": 5, "unidades": 3}]},
                    {"referencia": "UBL029.AL0216", "color": "001", "medidaCaja": "60x40x40",
                     "pedido": "07672", "talla": "95", "cajas": [{"caja": 5, "unidades": 31}]},
                    {"referencia": "UBL029.AL0216", "color": "001", "medidaCaja": "60x40x40",
                     "pedido": "07672", "talla": "105", "cajas": [{"caja": 5, "unidades": 3}]}
                  ]}]}
                """;
        mvc.perform(post("/importar").session(sesion)
                        .param("cliente", "AMI").param("json", json)
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "10/07/2026").param("fechaEnvio", "24/07/2026"))
                .andExpect(redirectedUrl("/revision"));

        String html = mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Un input "pesoNetoKg" por caja física líder (caja 4 y caja 5), no por línea.
        assertEquals(2, contarOcurrencias(html, "pesoNetoKg"));
        assertEquals(2, contarOcurrencias(html, "pesoBrutoKg"));
    }

    /**
     * Cinco cajas correlativas idénticas (1-5) de la misma referencia: se
     * compactan en una sola fila "1-5".
     *
     * El tamaño 99x99x99 no tiene tara configurada A PROPÓSITO: sin tara la
     * inferencia no puede derivar el peso unitario y por tanto no propaga nada
     * al resto de la referencia. Con una tara conocida (60x40x40) el peso
     * tecleado en la primera caja llegaría a las otras cuatro POR INFERENCIA, y
     * el test pasaría aunque el formulario solo aplicara el peso a una caja.
     */
    private static final String JSON_CINCO_CAJAS_IGUALES = """
            {"cliente": "AMI", "destinos": [{"destino": "PARIS",
              "palets": [{"palet": 1, "cajaInicio": 1, "cajaFin": 5}],
              "referencias": [{"referencia": "ULL163.AL217", "color": "NOIR",
                "medidaCaja": "99x99x99", "pedido": "07685",
                "cajas": [{"cajaInicio": 1, "cajaFin": 5, "unidadesPorCaja": 5}]}]}]}
            """;

    private void importarCincoCajasIguales(MockHttpSession sesion) throws Exception {
        mvc.perform(post("/importar").session(sesion)
                        .param("cliente", "AMI").param("json", JSON_CINCO_CAJAS_IGUALES)
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "10/07/2026").param("fechaEnvio", "24/07/2026"))
                .andExpect(redirectedUrl("/revision"));
    }

    @Test
    void cincoCajasEquivalentesSePintanEnUnaSolaFilaConSuRango() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importarCincoCajasIguales(sesion);

        String html = mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                // El rango en la columna CAJA, y el encabezado sigue contando
                // las 5 cajas físicas aunque solo haya una fila.
                .andExpect(content().string(containsString(">1-5<")))
                .andExpect(content().string(containsString("PARIS (5 cajas)")))
                .andReturn().getResponse().getContentAsString();

        // Una sola fila: un único par de campos de peso para las cinco cajas.
        assertEquals(1, contarOcurrencias(html, "pesoNetoKg"));
        assertEquals(1, contarOcurrencias(html, "pesoBrutoKg"));
        // ...y un índice de caja por cada una de las cinco.
        assertEquals(5, contarOcurrencias(html, "indicesCaja"));
    }

    @Test
    void elPesoDeUnaFilaCompactadaSeAplicaATodasSusCajas() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importarCincoCajasIguales(sesion);

        // Lo que manda la fila "1-5": un peso y los cinco índices.
        mvc.perform(post("/recalcular").session(sesion)
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "0", "1", "2", "3", "4")
                        .param("cajas[0].pesoBrutoKg", "5.6"))
                .andExpect(redirectedUrl("/revision"));

        EnvioEnCurso envio = (EnvioEnCurso) sesion.getAttribute("scopedTarget.envioEnCurso");
        var cajas = envio.getImportado().getDestinos().get(0).getDestino().getCajas();
        assertEquals(5, cajas.size());
        for (var caja : cajas) {
            assertEquals(5.6, caja.getPesoBrutoKg(), "caja " + caja.getNumeroCaja());
        }
    }

    @Test
    void laEdicionDeUnaFilaCompactadaLlegaATodasSusCajas() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importarCincoCajasIguales(sesion);

        mvc.perform(post("/recalcular").session(sesion)
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "0", "1", "2", "3", "4")
                        .param("cajas[0].codigoColor", "ROJO"))
                .andExpect(redirectedUrl("/revision"));

        EnvioEnCurso envio = (EnvioEnCurso) sesion.getAttribute("scopedTarget.envioEnCurso");
        for (var caja : envio.getImportado().getDestinos().get(0).getDestino().getCajas()) {
            assertEquals("ROJO", caja.getCodigoColor(), "caja " + caja.getNumeroCaja());
        }
    }

    @Test
    void editarLosCamposDeUnaCajaLosCambiaEnLaSesion() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importar(sesion);

        mvc.perform(post("/recalcular").session(sesion)
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "0")
                        .param("cajas[0].numeroCaja", "77")
                        .param("cajas[0].referencia", "USL999.AL217")
                        .param("cajas[0].codigoColor", "VERDE")
                        .param("cajas[0].numeroPedido", "PO-NUEVO")
                        .param("cajas[0].talla", "85")
                        .param("cajas[0].tamanoCaja", "60x40x30")
                        .param("cajas[0].cantidad", "7")
                        .param("cajas[0].numeroPalet", "9"))
                .andExpect(redirectedUrl("/revision"));

        EnvioEnCurso envio = (EnvioEnCurso) sesion.getAttribute("scopedTarget.envioEnCurso");
        var caja = envio.getImportado().getDestinos().get(0).getDestino().getCajas().get(0);
        assertEquals(77, caja.getNumeroCaja());
        assertEquals("USL999.AL217", caja.getReferencia());
        assertEquals("VERDE", caja.getCodigoColor());
        assertEquals("PO-NUEVO", caja.getNumeroPedido());
        assertEquals("85", caja.getTalla());
        assertEquals("60x40x30", caja.getTamanoCaja());
        assertEquals(7, caja.getCantidad());
        assertEquals(9, caja.getNumeroPalet());
    }

    @Test
    void unCampoEnviadoVacioNoBorraElValorQueYaHabia() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importar(sesion);

        EnvioEnCurso envio = (EnvioEnCurso) sesion.getAttribute("scopedTarget.envioEnCurso");
        var caja = envio.getImportado().getDestinos().get(0).getDestino().getCajas().get(0);
        String referenciaOriginal = caja.getReferencia();
        int cantidadOriginal = caja.getCantidad();

        mvc.perform(post("/recalcular").session(sesion)
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "0")
                        .param("cajas[0].referencia", "")
                        .param("cajas[0].codigoColor", "   ")
                        .param("cajas[0].cantidad", ""))
                .andExpect(redirectedUrl("/revision"));

        assertEquals(referenciaOriginal, caja.getReferencia());
        assertEquals(cantidadOriginal, caja.getCantidad());
    }

    @Test
    void corregirElTamanoDeCajaDesbloqueaLaInferenciaDelPeso() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        // 99x99x99 no tiene tara: con el bruto tecleado el neto no se puede
        // derivar. El tamaño está mal leído y se corrige desde la revisión.
        importarCincoCajasIguales(sesion);
        mvc.perform(post("/recalcular").session(sesion)
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "0")
                        .param("cajas[0].pesoBrutoKg", "11.0"))
                .andExpect(redirectedUrl("/revision"));

        EnvioEnCurso envio = (EnvioEnCurso) sesion.getAttribute("scopedTarget.envioEnCurso");
        var caja = envio.getImportado().getDestinos().get(0).getDestino().getCajas().get(0);
        assertNull(caja.getPesoNetoKg());

        mvc.perform(post("/recalcular").session(sesion)
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "0")
                        .param("cajas[0].tamanoCaja", "60x40x40"))
                .andExpect(redirectedUrl("/revision"));

        assertEquals(10.4, caja.getPesoNetoKg());   // 11.0 - tara 0.6
    }

    @Test
    void editarElPaletAManoQuitaLaCajaDeLaListaDeSinPalet() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        // El palet solo cubre la caja 1; la 2 se queda fuera de todo rango.
        String json = """
                {"cliente": "AMI", "destinos": [{"destino": "PARIS",
                  "palets": [{"palet": 1, "cajaInicio": 1, "cajaFin": 1}],
                  "referencias": [{"referencia": "R1", "color": "NOIR",
                    "medidaCaja": "60x40x40", "pedido": "P1",
                    "cajas": [{"caja": 1, "unidades": 10}, {"caja": 2, "unidades": 10}]}]}]}
                """;
        mvc.perform(post("/importar").session(sesion)
                        .param("cliente", "AMI").param("json", json)
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "10/07/2026").param("fechaEnvio", "24/07/2026"))
                .andExpect(redirectedUrl("/revision"));

        mvc.perform(get("/revision").session(sesion))
                .andExpect(content().string(containsString("Sin palet")));

        mvc.perform(post("/recalcular").session(sesion)
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "1")
                        .param("cajas[0].numeroPalet", "1"))
                .andExpect(redirectedUrl("/revision"));

        mvc.perform(get("/revision").session(sesion))
                .andExpect(content().string(not(containsString("Sin palet"))));
    }

    @Test
    void alternarUnaFilaLaDesplegaEnSusCajasYAplicaLoTecleadoEnElMismoEnvio() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importarCincoCajasIguales(sesion);

        mvc.perform(post("/alternar-fila").session(sesion)
                        .param("destino", "0").param("indice", "0")
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "0", "1", "2", "3", "4")
                        .param("cajas[0].codigoColor", "ROJO"))
                .andExpect(redirectedUrl("/revision"));

        String html = mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Cinco filas, cada una con su propio par de pesos.
        assertEquals(5, contarOcurrencias(html, "pesoNetoKg"));
        assertTrue(html.contains("ROJO"), "el color tecleado al desplegar se pierde");
    }

    @Test
    void alternarDosVecesLaMismaFilaLaVuelveAPlegar() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importarCincoCajasIguales(sesion);

        mvc.perform(post("/alternar-fila").session(sesion)
                        .param("destino", "0").param("indice", "0"));
        mvc.perform(post("/alternar-fila").session(sesion)
                        .param("destino", "0").param("indice", "0"));

        String html = mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">1-5<")))
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, contarOcurrencias(html, "pesoNetoKg"));
    }

    @Test
    void elDesplegableDeTamanosOfreceLasTarasYConservaUnTamanoSinTara() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        // El fixture trae 99x99x99, que NO está en las taras de application.yml.
        // Si el desplegable solo ofreciera el catálogo, guardar la fila
        // cambiaría el tamaño del envío en silencio por el primero de la lista.
        importarCincoCajasIguales(sesion);

        String html = mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .replaceAll("\\s+", " ");

        assertTrue(html.contains("<option value=\"99x99x99\" selected=\"selected\">"),
                "el tamaño sin tara debe seguir seleccionado en el desplegable");
        assertTrue(html.contains("<option value=\"60x40x40\">"),
                "el desplegable debe ofrecer las taras de application.yml");
    }

    @Test
    void alternarFilaSinEnvioEnCursoRedirigeALaEntrada() throws Exception {
        mvc.perform(post("/alternar-fila").param("destino", "0").param("indice", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/packing-list"));
    }

    private static int contarOcurrencias(String texto, String fragmento) {
        int total = 0;
        for (int i = texto.indexOf(fragmento); i >= 0; i = texto.indexOf(fragmento, i + fragmento.length())) {
            total++;
        }
        return total;
    }

    @Test
    void unTamanoDeCajaSinTaraSeAvisaEnLaRevision() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        String json = """
                {"cliente": "AMI", "destinos": [{"destino": "PARIS",
                  "palets": [{"palet": 1, "cajaInicio": 1, "cajaFin": 2}],
                  "referencias": [{"referencia": "R1", "color": "NOIR",
                    "medidaCaja": "99x99x99", "pedido": "P1",
                    "cajas": [{"caja": 1, "unidades": 10}, {"caja": 2, "unidades": 10}]}]}]}
                """;
        mvc.perform(post("/importar").session(sesion)
                        .param("cliente", "AMI")
                        .param("json", json)
                        .param("temporada", "H26")
                        .param("numeroFactura", "FA-1")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(redirectedUrl("/revision"));

        // Con un peso tecleado, el tamaño sin tara impide inferir: la
        // revisión debe decirlo, no callar.
        mvc.perform(post("/recalcular").session(sesion)
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "0")
                        .param("cajas[0].pesoBrutoKg", "11.0"))
                .andExpect(redirectedUrl("/revision"));

        mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("99x99x99")))
                // El aviso manda a la pantalla de taras, NO a application.yml:
                // la tabla de taras vive en la base de datos desde que se puede
                // pesar un cartón sin tocar ficheros ni recompilar.
                .andExpect(content().string(containsString("pésalo en la pantalla de taras")));
    }

    /**
     * Regresión del flujo real: la extracción por hojas puede no encontrar la
     * medida de un grupo de cajas (se escribe una sola vez, a veces de lado en
     * el margen) y el JSON llega sin "medidaCaja". Eso reventaba la generación
     * con un NullPointerException al sumar el volumen del resumen, y el
     * usuario solo veía "No se pudieron generar los excels" desde la revisión.
     * Es un dato que se completa en pantalla: tiene que avisar y generar.
     */
    @Test
    void unEnvioConUnaCajaSinMedidaAvisaEnLaRevisionPeroGeneraIgual() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        String json = """
                {"cliente": "AMI", "destinos": [{"destino": "PARIS",
                  "palets": [{"palet": 1, "cajaInicio": 1, "cajaFin": 2}],
                  "referencias": [
                    {"referencia": "R1", "color": "NOIR", "medidaCaja": "60x40x40",
                     "pedido": "P1", "cajas": [{"caja": 1, "unidades": 10, "pesoBruto": 9.0}]},
                    {"referencia": "R1", "color": "NOIR", "pedido": "P1",
                     "cajas": [{"caja": 2, "unidades": 10, "pesoBruto": 9.0}]}]}]}
                """;
        mvc.perform(post("/importar").session(sesion)
                        .param("cliente", "AMI")
                        .param("json", json)
                        .param("temporada", "H26")
                        .param("numeroFactura", "FA-1")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(redirectedUrl("/revision"));

        mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cajas sin medida")))
                .andExpect(content().string(containsString("TAMAÑO")));

        // Y generar NO falla: se va a resultados, no de vuelta con un error.
        mvc.perform(post("/generar").session(sesion))
                .andExpect(redirectedUrl("/resultados"))
                .andExpect(flash().attribute("error", (Object) null));
    }

    @Test
    void flujoCompletoGeneraDescargaYComprimeLosExcels() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importar(sesion);

        mvc.perform(post("/generar").session(sesion))
                .andExpect(redirectedUrl("/resultados"));

        // PARIS tiene 3 combinaciones referencia+color, JAPAN 2 y CHINA 1.
        mvc.perform(get("/resultados").session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("resultados"))
                .andExpect(content().string(containsString(FICHERO_PARIS_USL728)))
                .andExpect(content().string(containsString(
                        "2026.07.24_PUN_07685_USL737.ACO137.ROJO_PASION_69_H26_FR.xlsx")))
                .andExpect(content().string(containsString(
                        "2026.07.24_PUN_07713_USL737.ACO137.NOIR_H26_CHINA.xlsx")));

        byte[] excel = mvc.perform(get("/descargar/" + FICHERO_PARIS_USL728).session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet hoja = wb.getSheet("STANDARD PKL H26");
            assertEquals("PARIS", hoja.getRow(9).getCell(1).getStringCellValue());       // B10
            assertEquals("USL728.AL217", hoja.getRow(19).getCell(2).getStringCellValue()); // C20
        }

        byte[] zip = mvc.perform(get("/descargar-todo").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andReturn().getResponse().getContentAsByteArray();
        List<String> entradas = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entrada = in.getNextEntry(); entrada != null; entrada = in.getNextEntry()) {
                entradas.add(entrada.getName());
            }
        }
        assertEquals(6, entradas.size());
        assertTrue(entradas.contains(FICHERO_PARIS_USL728));
    }

    @Test
    void generarProduceElVolcadoErpDescargableDelEnvioCompleto() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importar(sesion);

        mvc.perform(post("/generar").session(sesion))
                .andExpect(redirectedUrl("/resultados"));

        // El volcado queda en sesión y expuesto a la vista de resultados.
        mvc.perform(get("/resultados").session(sesion))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("volcadoErp"));

        byte[] excel = mvc.perform(get("/descargar-volcado-erp").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        containsString("Volcado_ICSUITE_FA-26-1189.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet hoja = wb.getSheet("Volcado");
            assertEquals("Lin.", hoja.getRow(0).getCell(0).getStringCellValue());
            // Al menos una línea de datos agregada de todas las destinaciones.
            assertTrue(hoja.getLastRowNum() >= 1);
            // La comanda tecleada en la pantalla de entrada llega a todas las líneas.
            for (int fila = 1; fila <= hoja.getLastRowNum(); fila++) {
                assertEquals(12345, hoja.getRow(fila).getCell(2).getNumericCellValue());
            }
        }
    }

    @Test
    void descargarElVolcadoSinHaberGeneradoDevuelve404() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importar(sesion);
        mvc.perform(get("/descargar-volcado-erp").session(sesion))
                .andExpect(status().isNotFound());
    }

    // --- etiquetas de caja ---

    /** Llega hasta resultados con el fixture AMI (importar + generar). */
    private MockHttpSession sesionConEnvioGenerado() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importar(sesion);
        mvc.perform(post("/generar").session(sesion))
                .andExpect(redirectedUrl("/resultados"));
        return sesion;
    }

    /** Excel de pedido coherente con el fixture (PARIS/JAPAN/CHINA). */
    private static byte[] pedidoAmiDelFixture() {
        return PedidoAmiExcel.crear("EAN H26",
                new PedidoAmiExcel.Fila("SPAIN", "USL728.AL217", "NOIR", "BLACK", "U", 7685),
                new PedidoAmiExcel.Fila("SPAIN", "USL737.ACO137", "ROJO PASION 69", "RED", "U", 7685),
                new PedidoAmiExcel.Fila("SPAIN", "USL737.ACO137", "NOIR", "BLACK", "U", "07700 JP"),
                new PedidoAmiExcel.Fila("SPAIN", "USL737.ACO137", "NOIR", "BLACK", "U", "07713 CH"));
    }

    /**
     * El excel de pedido se pide en la pantalla de entrada, así que /generar
     * ya puede dejar las etiquetas hechas: no hay paso intermedio.
     */
    @Test
    void generarDejaLasEtiquetasListasEnResultados() throws Exception {
        MockHttpSession sesion = sesionConEnvioYPedidoSubido();

        mvc.perform(get("/resultados").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Etiquetas_AMI_PARIS_FA-26-1189.xlsx")));

        byte[] excel = mvc.perform(
                        get("/descargar-etiquetas/Etiquetas_AMI_PARIS_FA-26-1189.xlsx")
                                .session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertEquals("AMI FRANCE", wb.getSheetName(0));
        }
    }

    /**
     * El excel de pedido es opcional en la entrada: sin él las etiquetas no
     * salen, pero el packing list sí y el aviso dice qué falta. Nunca bloquear
     * por un dato que el usuario puede resolver.
     */
    @Test
    void sinElExcelDePedidoLasEtiquetasAvisanEnVezDeGenerarse() throws Exception {
        MockHttpSession sesion = sesionConEnvioGenerado();

        mvc.perform(get("/resultados").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Falta el excel del pedido de AMI")))
                .andExpect(content().string(not(containsString("Etiquetas_AMI_"))))
                // Los packing lists se generan igual.
                .andExpect(content().string(containsString(FICHERO_PARIS_USL728)));
    }

    /**
     * Sin el paso intermedio ya no hay pantalla que confirme qué excel de
     * pedido se subió, y el equivocado daría etiquetas malas sin decir nada:
     * la tarjeta de resultados nombra el fichero con el que se generaron.
     */
    @Test
    void laTarjetaDeEtiquetasDiceConQueExcelDePedidoSeGeneraron() throws Exception {
        MockHttpSession sesion = sesionConEnvioYPedidoSubido();
        mvc.perform(get("/resultados").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AMI EAN H26.xlsx")));
    }

    /** El paso intermedio que pedía el excel de pedido ya no existe. */
    @Test
    void elPasoIntermedioDeEtiquetasYaNoExiste() throws Exception {
        MockHttpSession sesion = sesionConEnvioYPedidoSubido();
        mvc.perform(get("/etiquetas").session(sesion))
                .andExpect(status().isNotFound());
    }

    /** Los avisos de etiquetas se pintan tal cual: sin el prefijo "Etiquetas:". */
    @Test
    void losAvisosDeEtiquetasNoLlevanPrefijo() throws Exception {
        MockHttpSession sesion = sesionConEnvioGenerado();
        mvc.perform(get("/resultados").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Etiquetas: "))));
    }

    /**
     * Los avisos de etiquetas llegan a la pantalla agrupados por destinación,
     * no como una lista plana de una línea por caja.
     *
     * El generador emite un aviso por caja —que es la verdad del envío—, pero
     * leídos así son la misma frase repetida tantas veces como cajas, y eso
     * empuja hacia abajo los avisos que sí hay que atender. La agrupación es
     * de pantalla: el generador y sus tests no se enteran.
     */
    @Test
    void losAvisosDeEtiquetasLleganAgrupadosPorDestinacion() throws Exception {
        MockHttpSession sesion = sesionConEnvioYPedidoSubido();

        String html = mvc.perform(get("/resultados").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("<details class=\"bloque-avisos\""),
                "cada destinación es un bloque con su detalle plegable");
        assertTrue(html.contains("class=\"destino-avisos\""),
                "y con su nombre de cabecera, que es lo que las separa");
        assertTrue(html.contains("class=\"resumen-aviso"),
                "el resumen se lee sin desplegar nada");
        assertTrue(html.contains("<strong"),
                "la consecuencia va resaltada: es lo que se busca de un vistazo");
    }

    /**
     * Varias cajas con el mismo problema son UNA línea con su rango, no una
     * por caja. Es el mismo criterio de la tabla de revisión, y por el mismo
     * motivo: una lista de veinte frases idénticas no informa de nada.
     */
    @Test
    void lasCajasConElMismoAvisoSeCompactanEnUnRangoEnLaPantalla() throws Exception {
        List<AvisoEtiqueta> avisos = List.of(
                AvisoEtiqueta.deCaja("CHINA", 1, "Sin número de pedido en la entrada",
                        "Etiqueta sin order number ni código de barras"),
                AvisoEtiqueta.deCaja("CHINA", 2, "Sin número de pedido en la entrada",
                        "Etiqueta sin order number ni código de barras"),
                AvisoEtiqueta.deCaja("CHINA", 3, "Sin número de pedido en la entrada",
                        "Etiqueta sin order number ni código de barras"));

        AgrupadorAvisosEtiquetas.AvisosAgrupados agrupados =
                AgrupadorAvisosEtiquetas.agrupar(avisos);

        assertEquals(1, agrupados.bloques().get(0).detalle().size());
        assertEquals("Cajas 1-3", agrupados.bloques().get(0).detalle().get(0).rotulo());
        assertEquals("3 cajas", agrupados.bloques().get(0).resumen().get(0).recuento());
    }

    /** Como sesionConEnvioGenerado, pero con el excel de pedido subido en el paso 1. */
    private MockHttpSession sesionConEnvioYPedidoSubido() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(multipart("/importar")
                        .file(new MockMultipartFile("pedidoCliente", "AMI EAN H26.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                pedidoAmiDelFixture()))
                        .session(sesion)
                        .param("cliente", "AMI").param("json", jsonDePrueba())
                        .param("temporada", "H26").param("numeroFactura", "FA-26-1189")
                        .param("fechaFactura", "10/07/2026").param("fechaEnvio", "24/07/2026"))
                .andExpect(redirectedUrl("/revision"));
        mvc.perform(post("/generar").session(sesion))
                .andExpect(redirectedUrl("/resultados"));
        return sesion;
    }

    /**
     * Volver a revisión y regenerar rehace también las etiquetas con el mismo
     * excel de pedido: sigue en sesión desde la pantalla de entrada.
     */
    @Test
    void regenerarRehaceLasEtiquetasConElPedidoDeLaSesion() throws Exception {
        MockHttpSession sesion = sesionConEnvioYPedidoSubido();

        mvc.perform(post("/generar").session(sesion))
                .andExpect(redirectedUrl("/resultados"));

        mvc.perform(get("/resultados").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Etiquetas_AMI_PARIS_FA-26-1189.xlsx")));
    }

    @Test
    void clienteSinGeneradorDeEtiquetasSigueEnDesarrollo() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        // El fixture es de AMI, pero generamos como ACKERMANN (genérico):
        // avisa sin bloquear y no tiene etiquetas implementadas.
        mvc.perform(post("/importar").session(sesion)
                        .param("cliente", "ACKERMANN")
                        .param("json", jsonDePrueba())
                        .param("temporada", "SPRING 25")
                        .param("numeroFactura", "FA-1")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(redirectedUrl("/revision"));
        mvc.perform(post("/generar").session(sesion))
                .andExpect(redirectedUrl("/resultados"));

        mvc.perform(get("/resultados").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("En desarrollo")));
    }

    /** JSON mínimo de APC con la destinación parametrizada (IVRY no tiene etiquetas, JAPAN sí). */
    private String jsonApcConDestino(String destino) {
        return """
                {"cliente": "APC", "destinos": [{"destino": "%s",
                  "palets": [{"palet": 1, "cajaInicio": 1, "cajaFin": 1}],
                  "referencias": [
                    {"referencia": "PXCBC-F67008", "modelo": "LE NEIGE", "color": "LAW-MARINE",
                     "medidaCaja": "60x40x40", "pedido": "690", "cantidadTotal": 10,
                     "cajas": [{"caja": 1, "unidades": 10, "pesoBruto": 6.4}]}
                  ]}]}
                """.formatted(destino);
    }

    private void importarApc(MockHttpSession sesion, String destino) throws Exception {
        mvc.perform(post("/importar").session(sesion)
                        .param("cliente", "APC")
                        .param("json", jsonApcConDestino(destino))
                        .param("temporada", "E25")
                        .param("numeroFactura", "FA-26-1")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/revision"));
        mvc.perform(post("/generar").session(sesion))
                .andExpect(redirectedUrl("/resultados"));
    }

    /** APC no pide ningún archivo extra: sus etiquetas salen sin más. */
    @Test
    void apcGeneraSusEtiquetasSinPedirNingunArchivo() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importarApc(sesion, "JAPAN");

        mvc.perform(get("/resultados").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Etiquetas_APC_JAPAN_FA-26-1.xlsx")));
    }

    @Test
    void unaDestinacionSinEtiquetasImplementadasAvisaEnResultados() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        // IVRY es una destinación válida de APC para el packing list, pero
        // sin etiquetas implementadas.
        importarApc(sesion, "IVRY");

        mvc.perform(get("/resultados").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("sin etiquetas de APC implementadas")))
                .andExpect(content().string(not(containsString("Etiquetas_APC_"))));
    }

    // --- destinaciones sin configurar (clientes con catálogo de destinos) ---

    /** Dos destinaciones APC: JAPAN (configurada) y AUSTRALIA (desconocida). */
    /**
     * La segunda destinación tiene que ser una que NO esté en el catálogo de
     * APC ni como clave ni como hija de nadie. "HONG KONG" lo cumple; ojo con
     * usar AUSTRALIA o RETAIL, que lo parecen pero hoy son destinaciones
     * configuradas (hijas de WHOLESALE y clave del catálogo).
     */
    private static final String JSON_APC_JAPAN_Y_DESCONOCIDA = """
            {"cliente": "APC", "destinos": [
              {"destino": "JAPAN",
               "palets": [{"palet": 1, "cajaInicio": 1, "cajaFin": 1}],
               "referencias": [{"referencia": "PXCBC-F67008", "modelo": "LE NEIGE",
                 "pedido": "4100128683", "canal": "JAPAN", "color": "LZZ-NOIR",
                 "medidaCaja": "60x40x40", "cantidadTotal": 10,
                 "cajas": [{"caja": 1, "unidades": 10, "pesoBruto": 3.5}]}]},
              {"destino": "HONG KONG",
               "palets": [{"palet": 1, "cajaInicio": 1, "cajaFin": 1}],
               "referencias": [{"referencia": "PXBHZ-H65077", "modelo": "CEINTURE PARIS",
                 "pedido": "4100128721", "canal": "HONG KONG", "color": "LZZ-NOIR",
                 "medidaCaja": "60x40x40", "talla": "95", "cantidadTotal": 3,
                 "cajas": [{"caja": 1, "unidades": 3, "pesoBruto": 1.4}]}]}
            ]}
            """;

    private void importarJsonApc(MockHttpSession sesion, String json) throws Exception {
        mvc.perform(post("/importar").session(sesion)
                        .param("cliente", "APC")
                        .param("json", json)
                        .param("temporada", "E25")
                        .param("numeroFactura", "FA-26-2")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(redirectedUrl("/revision"));
    }

    @Test
    void unaDestinacionSinConfigurarAvisaYGeneraLasDemas() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importarJsonApc(sesion, JSON_APC_JAPAN_Y_DESCONOCIDA);

        mvc.perform(post("/generar").session(sesion))
                .andExpect(redirectedUrl("/resultados"));

        // JAPAN se genera; HONG KONG sale como aviso, no como error.
        mvc.perform(get("/resultados").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PKL_APC_JAPAN_FA-26-2.xlsx")))
                .andExpect(content().string(containsString("HONG KONG")))
                .andExpect(content().string(containsString("packing no generado")));
    }

    @Test
    void sinNingunaDestinacionConfiguradaVuelveARevisionConElError() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        // Las dos destinaciones fuera del catálogo: no hay nada que generar.
        importarJsonApc(sesion, JSON_APC_JAPAN_Y_DESCONOCIDA
                .replace("\"JAPAN\"", "\"SINGAPORE\""));

        mvc.perform(post("/generar").session(sesion))
                .andExpect(redirectedUrl("/revision"))
                .andExpect(flash().attribute("error", containsString("packing no generado")))
                .andExpect(flash().attribute("error", containsString("HONG KONG")));
    }

    @Test
    void descargarUnFicheroQueNoExisteDevuelve404() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importar(sesion);
        mvc.perform(post("/generar").session(sesion));

        mvc.perform(get("/descargar/NO_EXISTE.xlsx").session(sesion))
                .andExpect(status().isNotFound());
    }

    // --- Excel de pedido del cliente y destinos padre de APC ---

    private static final String JSON_APC_AUSTRALIA = """
            {"cliente": "APC", "destinos": [{"destino": "Australia",
              "palets": [{"palet": 1, "cajaInicio": 1, "cajaFin": 1}],
              "referencias": [
                {"referencia": "PXBHZ-H65077", "color": "LZZ-NOIR",
                 "medidaCaja": "40x30x20", "pedido": "721",
                 "cajas": [{"caja": 1, "unidades": 3, "pesoBruto": 4.2}]}
              ]}]}
            """;

    /** POST /importar de un envío de APC a Australia, una hija de WHOLESALE. */
    private void importarApcAustralia(MockHttpSession sesion) throws Exception {
        mvc.perform(post("/importar").session(sesion)
                        .param("cliente", "APC").param("json", JSON_APC_AUSTRALIA)
                        .param("temporada", "E25").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "28/04/2026").param("fechaEnvio", "28/04/2026"))
                .andExpect(redirectedUrl("/revision"));
    }

    private String revision(MockHttpSession sesion) throws Exception {
        return mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @SuppressWarnings("unchecked")
    @Test
    void soloLosClientesConPedidoDeclaradoPidenSuExcelEnLaEntrada() throws Exception {
        Map<String, Map<String, String>> clientesJs =
                (Map<String, Map<String, String>>) mvc.perform(get("/packing-list"))
                        .andExpect(status().isOk())
                        .andReturn().getModelAndView().getModel().get("clientesJs");

        assertEquals("true", clientesJs.get("APC").get("pedidoCliente"));
        assertEquals("true", clientesJs.get("AMI").get("pedidoCliente"));
        assertEquals("false", clientesJs.get("ACKERMANN").get("pedidoCliente"));
    }

    @Test
    void unaHijaDeApcSeRevisaBajoSuDestinoPadre() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importarApcAustralia(sesion);

        // La sección de la revisión es la del padre; "Australia" solo
        // sobrevive en el canal, que esta tabla no muestra.
        assertTrue(revision(sesion).contains("WHOLESALE"));
    }

    @Test
    void sinExcelDePedidoElNumeroSeQuedaEnLosTresDigitosYSeAvisa() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importarApcAustralia(sesion);

        String html = revision(sesion);

        assertTrue(html.contains("721"));
        assertTrue(html.contains("No se ha subido el excel de pedido"));
    }

    @Test
    void conElExcelDePedidoSubidoElNumeroDePedidoSaleCompleto() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        byte[] pedido;
        try (var in = getClass().getResourceAsStream("/ejemplos/APC_PEDIDO_FALL26.xlsx")) {
            pedido = in.readAllBytes();
        }

        mvc.perform(multipart("/importar").file(new MockMultipartFile(
                                "pedidoCliente", "APC_PEDIDO_FALL26.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                pedido))
                        .session(sesion)
                        .param("cliente", "APC").param("json", JSON_APC_AUSTRALIA)
                        .param("temporada", "E25").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "28/04/2026").param("fechaEnvio", "28/04/2026"))
                .andExpect(redirectedUrl("/revision"));

        assertTrue(revision(sesion).contains("4100128721"));
    }

    @Test
    void laRevisionMuestraElLivraisonCodeGeneradoDeCadaDestinacion() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importarApcAustralia(sesion);

        assertTrue(revision(sesion).contains("PUN20260428WH1"));
    }

    @Test
    void editarElLivraisonCodeLoReescribeEnSuDestinacion() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importarApcAustralia(sesion);

        mvc.perform(post("/recalcular").session(sesion)
                        .param("destinos[0].indiceDestino", "0")
                        .param("destinos[0].livraisonCode", "PUN20260428WH3"))
                .andExpect(redirectedUrl("/revision"));

        String html = revision(sesion);
        assertTrue(html.contains("PUN20260428WH3"));
        assertFalse(html.contains("PUN20260428WH1"));
    }

    @Test
    void unLivraisonCodeVacioNoBorraElGenerado() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importarApcAustralia(sesion);

        mvc.perform(post("/recalcular").session(sesion)
                        .param("destinos[0].indiceDestino", "0")
                        .param("destinos[0].livraisonCode", ""))
                .andExpect(redirectedUrl("/revision"));

        assertTrue(revision(sesion).contains("PUN20260428WH1"));
    }

    @Test
    void unaDestinacionDeAmiNoMuestraCampoDeLivraisonCode() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importar(sesion);

        assertFalse(revision(sesion).contains("livraisonCode"));
    }

    /**
     * La banda de color por palet se comprueba sobre el HTML renderizado
     * porque es donde vive: el agrupador decide el número de banda y la
     * plantilla lo convierte en clase. PARIS tiene tres palets en el fixture,
     * así que sus filas tienen que traer tres matices distintos, y la banda
     * tiene que convivir con el marcador de pendiente en vez de desplazarlo.
     */
    @Test
    void laRevisionPintaCadaPaletDeUnaDestinacionConSuPropiaBandaDeColor() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importar(sesion);

        mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<tr class=\"palet-0")))
                .andExpect(content().string(containsString("<tr class=\"palet-1")))
                .andExpect(content().string(containsString("<tr class=\"palet-2")))
                // La banda no desplaza al amarillo de pendiente: conviven en
                // la misma fila y es el orden del CSS el que decide cuál gana.
                .andExpect(content().string(containsString("class=\"palet-0 pendiente\"")))
                // Y una caja sin palet no se tiñe: sale sin clase ninguna.
                .andExpect(content().string(containsString("<tr>")));
    }
}
