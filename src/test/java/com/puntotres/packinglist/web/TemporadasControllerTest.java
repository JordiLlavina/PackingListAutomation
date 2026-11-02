package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.ResumenTemporada;
import com.puntotres.packinglist.persistence.TemporadaGuardada;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;
import com.puntotres.packinglist.service.catalogo.ImportacionFotosTemporada;

/**
 * El archivo de temporadas: guardar los excels de una temporada (el de pedido
 * del cliente y el de producción de Puntotres) y sus fotos una vez, y lo que
 * eso hace por el envío y por el catálogo de modelos.
 *
 * Lo que de verdad hay que anclar no es la pantalla, sino que el envío acabe
 * trabajando con el excel guardado —y con el del cliente correcto—: un envío
 * generado con el pedido de otro cliente sale plausible y equivocado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TemporadasControllerTest {

    private static final String TIPO_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ArchivoTemporadas archivo;

    @Autowired
    private CatalogoModelos catalogo;

    @Autowired
    private FotosCatalogo fotosCatalogo;

    @Autowired
    private ImportacionFotosTemporada importacionFotos;

    private static byte[] recurso(String ruta) throws Exception {
        try (var entrada = TemporadasControllerTest.class.getResourceAsStream(ruta)) {
            return entrada.readAllBytes();
        }
    }

    private static MockMultipartFile pedido(String nombre, String contenido) {
        return new MockMultipartFile("pedido", nombre, TIPO_XLSX, contenido.getBytes());
    }

    private static MockMultipartFile produccion(String nombre, byte[] contenido) {
        return new MockMultipartFile("produccion", nombre, TIPO_XLSX, contenido);
    }

    /** Da de alta una temporada por la pantalla y devuelve su id. */
    private Long alta(String cliente, String temporada, String fichero) throws Exception {
        mvc.perform(multipart("/temporadas")
                        .file(pedido(fichero, "pedido de " + temporada))
                        .param("cliente", cliente)
                        .param("temporada", temporada))
                .andExpect(redirectedUrl("/temporadas"))
                // Un alta rechazada también redirige aquí: sin esto, el helper
                // devolvería el id de la temporada que ya existía.
                .andExpect(flash().attribute("mensaje", containsString("Guardada")));
        return idDe(cliente, temporada);
    }

    private Long idDe(String cliente, String temporada) {
        return archivo.de(cliente).stream()
                .filter(guardada -> guardada.temporada().equals(temporada))
                .map(ResumenTemporada::id)
                .findFirst().orElseThrow();
    }

    @Test
    void guardarUnaTemporadaLaDejaEnElArchivoConSuExcel() throws Exception {
        Long id = alta("APC", "FALL26", "APC_PEDIDO_FALL26.xlsx");

        TemporadaGuardada guardada = archivo.conFichero(id).orElseThrow();
        assertEquals("APC", guardada.getCliente());
        assertEquals("APC_PEDIDO_FALL26.xlsx", guardada.getNombreFicheroPedido());
        assertArrayEquals("pedido de FALL26".getBytes(), guardada.getExcelPedido());
        assertFalse(guardada.tieneProduccion(), "el de producción es opcional para APC");
    }

    @Test
    void laPantallaListaLasTemporadasGuardadasConElFormularioArriba() throws Exception {
        alta("AMI", "H29", "EAN H29.xlsx");

        String pagina = mvc.perform(get("/temporadas"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("H29")))
                .andExpect(content().string(containsString("EAN H29.xlsx")))
                .andExpect(content().string(containsString("Excel de Pedido de Cliente")))
                .andExpect(content().string(containsString("Excel de Producción de Puntotres")))
                .andExpect(content().string(containsString("data-campo-fotos")))
                .andReturn().getResponse().getContentAsString();
        assertTrue(pagina.indexOf("Añadir una temporada") < pagina.indexOf("Temporadas guardadas"),
                "el alta va arriba del todo");
    }

    @Test
    void editarCargaLaTemporadaEnElFormulario() throws Exception {
        Long id = alta("AMI", "H29-EDITAR", "EAN H29E.xlsx");

        mvc.perform(get("/temporadas").param("editar", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Editar la temporada H29-EDITAR")))
                .andExpect(content().string(containsString("name=\"id\" value=\"" + id + "\"")))
                .andExpect(content().string(containsString("Sin elegir otro, se queda este")));
    }

    @Test
    void editarElNombreSinSubirFicheroConservaElExcel() throws Exception {
        // Corregir una temporada mal tecleada no puede costar volver a buscar
        // el excel en el disco: es justo el trabajo que esto ahorra.
        // Clave propia: la base en memoria es la misma para toda la suite y
        // DocumentosCorteControllerTest ya guarda una "H27" de AMI.
        Long id = alta("AMI", "H27-EDITADA", "EAN H27.xlsx");

        mvc.perform(multipart("/temporadas")
                        .param("id", String.valueOf(id))
                        .param("cliente", "AMI")
                        .param("temporada", "H27-EDITADA CORREGIDA"))
                .andExpect(redirectedUrl("/temporadas"));

        TemporadaGuardada guardada = archivo.conFichero(id).orElseThrow();
        assertEquals("H27-EDITADA CORREGIDA", guardada.getTemporada());
        assertEquals("EAN H27.xlsx", guardada.getNombreFicheroPedido());
        assertArrayEquals("pedido de H27-EDITADA".getBytes(), guardada.getExcelPedido());
    }

    @Test
    void subirOtroExcelSustituyeElGuardado() throws Exception {
        Long id = alta("AMI", "H28", "EAN H28.xlsx");

        mvc.perform(multipart("/temporadas")
                        .file(pedido("EAN H28 v2.xlsx", "pedido corregido"))
                        .param("id", String.valueOf(id))
                        .param("cliente", "AMI")
                        .param("temporada", "H28"));

        TemporadaGuardada guardada = archivo.conFichero(id).orElseThrow();
        assertEquals("EAN H28 v2.xlsx", guardada.getNombreFicheroPedido());
        assertArrayEquals("pedido corregido".getBytes(), guardada.getExcelPedido());
    }

    @Test
    void elExcelDeProduccionSeAdjuntaDespuesSinTocarElDePedido() throws Exception {
        Long id = alta("APC", "PROD-DESPUES", "APC pedido.xlsx");

        mvc.perform(multipart("/temporadas")
                        .file(produccion("PROD_APC_SPRING27.xlsx", recurso("/ejemplos/corte/PROD_APC_SPRING27.xlsx")))
                        .param("id", String.valueOf(id))
                        .param("cliente", "APC")
                        .param("temporada", "PROD-DESPUES"))
                .andExpect(redirectedUrl("/temporadas"))
                .andExpect(flash().attribute("altas", hasSize(1)));

        TemporadaGuardada guardada = archivo.conFichero(id).orElseThrow();
        assertArrayEquals("pedido de PROD-DESPUES".getBytes(), guardada.getExcelPedido());
        assertEquals("PROD_APC_SPRING27.xlsx", guardada.getNombreFicheroProduccion());
        assertFalse(catalogo.codigosDeModeloEn("APC", id).isEmpty(),
                "los modelos del excel de producción entran al catálogo y a la temporada");
        mvc.perform(get("/temporadas/descargar/{id}/produccion", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("PROD_APC_SPRING27.xlsx")));
    }

    @Test
    void conLosDosExcelsElEmergenteDiceLoQueHaEntradoDeCadaUno() throws Exception {
        mvc.perform(multipart("/temporadas")
                        .file(new MockMultipartFile("pedido", "EAN PUNTOTRES H26.xlsx", TIPO_XLSX,
                                recurso("/ejemplos/EAN PUNTOTRES H26.xlsx")))
                        .file(produccion("prod.xlsx", recurso("/ejemplos/corte/PROD_APC_SPRING27.xlsx")))
                        .param("cliente", "AMI")
                        .param("temporada", "CAT-DOS-EXCELS"))
                .andExpect(redirectedUrl("/temporadas"))
                .andExpect(flash().attribute("altas", hasSize(2)));

        TemporadaGuardada guardada = archivo.conFichero(idDe("AMI", "CAT-DOS-EXCELS")).orElseThrow();
        assertTrue(guardada.tienePedido());
        assertTrue(guardada.tieneProduccion());
    }

    @Test
    void unClienteConPedidoNoSeGuardaSoloConElDeProduccion() throws Exception {
        mvc.perform(multipart("/temporadas")
                        .file(produccion("prod.xlsx", "x".getBytes()))
                        .param("cliente", "AMI")
                        .param("temporada", "SIN-PEDIDO"))
                .andExpect(redirectedUrl("/temporadas"))
                .andExpect(flash().attribute("mensajeError", containsString("obligatorio")));

        assertTrue(archivo.de("AMI").stream().noneMatch(t -> t.temporada().equals("SIN-PEDIDO")));
    }

    @Test
    void borrarUnaTemporadaLaQuitaDelArchivo() throws Exception {
        Long id = alta("APC", "E27", "APC E27.xlsx");

        mvc.perform(post("/temporadas/borrar").param("id", String.valueOf(id)))
                .andExpect(redirectedUrl("/temporadas"));

        assertTrue(archivo.conFichero(id).isEmpty());
    }

    @Test
    void unaTemporadaRepetidaDelMismoClienteNoSeGuardaDosVeces() throws Exception {
        // Dos entradas con el mismo nombre son indistinguibles en el
        // desplegable, y elegir la que no toca no se ve hasta el documento.
        alta("APC", "E28-REPETIDA", "APC E28.xlsx");

        mvc.perform(multipart("/temporadas")
                        .file(pedido("otro.xlsx", "otro"))
                        .param("cliente", "APC")
                        .param("temporada", "e28-repetida"))
                .andExpect(redirectedUrl("/temporadas"));

        List<ResumenTemporada> deApc = archivo.de("APC").stream()
                .filter(temporada -> temporada.temporada().equalsIgnoreCase("E28-REPETIDA"))
                .toList();
        assertEquals(1, deApc.size(), "el nombre no distingue mayúsculas");
    }

    @Test
    void unaTemporadaSinExcelNoSeDaDeAlta() throws Exception {
        mvc.perform(multipart("/temporadas")
                        .param("cliente", "AMI")
                        .param("temporada", "SIN FICHERO"))
                .andExpect(redirectedUrl("/temporadas"));

        assertTrue(archivo.de("AMI").stream()
                .noneMatch(temporada -> temporada.temporada().equals("SIN FICHERO")),
                "una temporada sin excel no ahorra nada y el desplegable no diría por qué");
    }

    @Test
    void unClienteSinExcelDePedidoGuardaElExcelDeProduccion() throws Exception {
        // Los genéricos no tienen pedido del cliente que leer: su temporada
        // guarda el excel de producción de Puntotres, de donde salen sus modelos.
        mvc.perform(multipart("/temporadas")
                        .file(produccion("prod.xlsx", "x".getBytes()))
                        .param("cliente", "ACKERMANN")
                        .param("temporada", "H26"))
                .andExpect(redirectedUrl("/temporadas"))
                .andExpect(flash().attribute("mensaje", containsString("Guardada")));

        ResumenTemporada guardada = archivo.de("ACKERMANN").stream()
                .filter(temporada -> temporada.temporada().equals("H26")).findFirst().orElseThrow();
        assertTrue(guardada.conProduccion());
        assertFalse(guardada.conPedido());
    }

    @Test
    void unClienteSinExcelDePedidoNoAceptaUno() throws Exception {
        mvc.perform(multipart("/temporadas")
                        .file(pedido("pedido.xlsx", "x"))
                        .file(produccion("prod.xlsx", "x".getBytes()))
                        .param("cliente", "ACKERMANN")
                        .param("temporada", "CON-PEDIDO"))
                .andExpect(redirectedUrl("/temporadas"))
                .andExpect(flash().attribute("mensajeError", containsString("no trabaja con excel de pedido")));
    }

    @Test
    void laEntradaOfreceElDesplegableDeTemporadasYElLapizParaMantenerlas() throws Exception {
        // El desplegable lo rellena el navegador con el mapa que va inline en la
        // página: si el atributo del modelo no llega, la constante sale nula y
        // el JS de la pantalla revienta entero, no solo esta parte.
        alta("AMI", "H31-LISTADA", "EAN H31.xlsx");

        mvc.perform(get("/packing-list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"temporadaGuardada\"")))
                .andExpect(content().string(containsString("href=\"/temporadas\"")))
                .andExpect(content().string(containsString("H31")));
    }

    @Test
    void elMenuLlevaAlArchivoDeTemporadas() throws Exception {
        mvc.perform(get("/menu"))
                .andExpect(content().string(containsString("href=\"/temporadas\"")))
                .andExpect(content().string(containsString("Guarda Temporadas y Ficheros")));
    }

    // --- Lo que hace por el envío ---

    @Test
    void elEnvioTrabajaConElExcelDeLaTemporadaElegidaSinVolverASubirlo() throws Exception {
        Long id = alta("AMI", "H30", "EAN PUNTOTRES H30.xlsx");
        MockHttpSession sesion = new MockHttpSession();

        importar(sesion, "AMI", id);

        EnvioEnCurso envio = (EnvioEnCurso) sesion.getAttribute("scopedTarget.envioEnCurso");
        assertEquals("EAN PUNTOTRES H30.xlsx", envio.getNombreExcelPedidoCliente());
        assertArrayEquals("pedido de H30".getBytes(), envio.getExcelPedidoCliente());
    }

    @Test
    void unaTemporadaDeOtroClienteNoLeCuelaSuExcelAlEnvio() throws Exception {
        // El desplegable se rellena en el navegador: cambiar de cliente después
        // de elegir temporada dejaría enviado el id de la de otro, y el envío
        // saldría con el pedido equivocado sin que nada avisara.
        Long deApc = alta("APC", "E29", "APC E29.xlsx");
        MockHttpSession sesion = new MockHttpSession();

        importar(sesion, "AMI", deApc);

        EnvioEnCurso envio = (EnvioEnCurso) sesion.getAttribute("scopedTarget.envioEnCurso");
        assertNull(envio.getExcelPedidoCliente());
        assertTrue(envio.getImportado().getAvisos().stream()
                        .anyMatch(aviso -> aviso.contains("temporada guardada")),
                "y se dice, que quedarse sin pedido en silencio es peor");
    }

    private void importar(MockHttpSession sesion, String cliente, Long temporadaGuardadaId)
            throws Exception {
        String json;
        try (var entrada = getClass()
                .getResourceAsStream("/client-packinglist/packing_list_ami_test.json")) {
            json = new String(entrada.readAllBytes());
        }
        mvc.perform(post("/importar").session(sesion)
                        .param("cliente", cliente)
                        .param("json", json)
                        .param("temporada", "H26")
                        .param("temporadaGuardadaId", String.valueOf(temporadaGuardadaId))
                        .param("numeroFactura", "FA-26-1189")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(redirectedUrl("/revision"));
    }

    // --- Lo que hace por el catálogo de modelos ---

    @Test
    void guardarUnaTemporadaConExcelRealDejaElAvisoEmergente() throws Exception {
        mvc.perform(multipart("/temporadas")
                        .file(new MockMultipartFile("pedido", "EAN PUNTOTRES H26.xlsx", TIPO_XLSX,
                                recurso("/ejemplos/EAN PUNTOTRES H26.xlsx")))
                        .param("cliente", "AMI")
                        .param("temporada", "CAT-H26"))
                .andExpect(redirectedUrl("/temporadas"))
                .andExpect(flash().attribute("altas", hasSize(1)))
                .andExpect(flash().attribute("altaCliente", "AMI"));

        Long id = idDe("AMI", "CAT-H26");
        assertTrue(!catalogo.codigosDeModeloEn("AMI", id).isEmpty(), "los modelos del pedido entran al catálogo");
    }

    @Test
    void unExcelQueNoSeDejaLeerGuardaLaTemporadaYAvisa() throws Exception {
        // La regla del proyecto: no bloquear por algo que se arregla después.
        // Se guarda, y el aviso dice que el catálogo no ha cogido nada.
        mvc.perform(multipart("/temporadas")
                        .file(pedido("raro.xlsx", "esto no es un excel"))
                        .param("cliente", "AMI")
                        .param("temporada", "CAT-ILEGIBLE"))
                .andExpect(redirectedUrl("/temporadas"))
                .andExpect(flash().attribute("mensaje", containsString("Guardada")))
                .andExpect(flash().attribute("avisosAlta", hasItem(containsString("no se ha podido extraer"))));

        assertTrue(archivo.de("AMI").stream().anyMatch(t -> t.temporada().equals("CAT-ILEGIBLE")));
    }

    @Test
    void borrarUnaTemporadaNoBorraNingunModelo() throws Exception {
        mvc.perform(multipart("/temporadas")
                        .file(new MockMultipartFile("pedido", "EAN PUNTOTRES H26.xlsx", TIPO_XLSX,
                                recurso("/ejemplos/EAN PUNTOTRES H26.xlsx")))
                        .param("cliente", "AMI")
                        .param("temporada", "CAT-BORRABLE"))
                .andExpect(redirectedUrl("/temporadas"));
        Long id = idDe("AMI", "CAT-BORRABLE");
        String modelo = catalogo.codigosDeModeloEn("AMI", id).iterator().next();

        mvc.perform(post("/temporadas/borrar").param("id", String.valueOf(id)))
                .andExpect(redirectedUrl("/temporadas"));

        assertTrue(catalogo.codigosDeModeloEn("AMI", id).isEmpty());
        assertTrue(catalogo.modelo("AMI", modelo).isPresent(), "el modelo sobrevive a su temporada");
    }

    @Test
    void llevarUnaTemporadaAUnGenericoLeQuitaElPedidoYSueltaSusModelos() throws Exception {
        mvc.perform(multipart("/temporadas")
                        .file(new MockMultipartFile("pedido", "EAN PUNTOTRES H26.xlsx", TIPO_XLSX,
                                recurso("/ejemplos/EAN PUNTOTRES H26.xlsx")))
                        .param("cliente", "AMI")
                        .param("temporada", "CAT-MUDA"))
                .andExpect(redirectedUrl("/temporadas"));
        Long id = idDe("AMI", "CAT-MUDA");
        assertTrue(!catalogo.codigosDeModeloEn("AMI", id).isEmpty());

        // Un genérico no tiene pedido: pide el excel de producción, y el de
        // pedido no se queda (su packing list lo leería como pedido).
        mvc.perform(multipart("/temporadas")
                        .param("id", String.valueOf(id))
                        .param("cliente", "KAMI")
                        .param("temporada", "CAT-MUDA"))
                .andExpect(redirectedUrl("/temporadas?editar=" + id))
                .andExpect(flash().attribute("mensajeError", containsString("obligatorio")));
        mvc.perform(multipart("/temporadas")
                        .file(produccion("prod.xlsx", recurso("/ejemplos/corte/PROD_APC_SPRING27.xlsx")))
                        .param("id", String.valueOf(id))
                        .param("cliente", "KAMI")
                        .param("temporada", "CAT-MUDA"))
                .andExpect(redirectedUrl("/temporadas"));

        TemporadaGuardada mudada = archivo.conFichero(id).orElseThrow();
        assertEquals("KAMI", mudada.getCliente());
        assertFalse(mudada.tienePedido());
        assertTrue(mudada.tieneProduccion());
        assertTrue(catalogo.codigosDeModeloEn("AMI", id).isEmpty(), "los modelos de AMI ya no son de esta temporada");
        assertFalse(catalogo.codigosDeModeloEn("KAMI", id).isEmpty());
    }

    @Test
    void elDesplegableDeLaEntradaNoOfreceTemporadasSinPedido() throws Exception {
        mvc.perform(multipart("/temporadas")
                        .file(produccion("prod.xlsx", "x".getBytes()))
                        .param("cliente", "KAMI")
                        .param("temporada", "CAT-PROD"))
                .andExpect(redirectedUrl("/temporadas"));
        Long id = idDe("KAMI", "CAT-PROD");

        assertTrue(!archivo.paraElDesplegable().containsKey("KAMI"),
                "el packing list lee el excel como pedido: una sin pedido no sirve ahí");
        List<Map<String, String>> delCorte = archivo.paraElDesplegableDelCorte().get("KAMI");
        assertTrue(delCorte.stream().anyMatch(t -> t.get("id").equals(String.valueOf(id))
                && "false".equals(t.get("pedido")) && "true".equals(t.get("produccion"))));
        assertTrue(archivo.conPedidoParaElEnvio(id, "KAMI").isEmpty());
        assertTrue(archivo.conProduccionParaElEnvio(id, "KAMI").isPresent());
        assertTrue(archivo.paraElEnvio(id, "KAMI").isPresent(), "por el nombre sí sigue valiendo");
    }

    // --- Las fotos de la temporada ---

    @Test
    void lasFotosDeUnZipEntranAlCatalogoEnSuModelo() throws Exception {
        catalogo.asegurarArticulo("AMI", "ULL940.AL0001");

        mvc.perform(multipart("/temporadas")
                        .file(pedido("p.xlsx", "x"))
                        .file(new MockMultipartFile("fotos", "fotos.zip", "application/zip",
                                zip("H26/ULL940/otra.jpg", "H26/ULL940.AL0001/principal.jpg")))
                        .param("cliente", "AMI")
                        .param("temporada", "FOTOS-ZIP"))
                .andExpect(redirectedUrl("/temporadas"))
                .andExpect(flash().attributeExists("fotosAlta"));
        Long id = idDe("AMI", "FOTOS-ZIP");

        ImportacionFotosTemporada.Estado estado = esperarFotos(id);
        assertEquals(2, estado.guardadas(), "avisos: " + estado.avisos());
        var fotos = fotosCatalogo.de("AMI", "ULL940");
        assertEquals(2, fotos.size());
        assertTrue(fotos.stream().anyMatch(foto -> foto.principal() && foto.nombreFichero().equals("principal.jpg")),
                "la que se llama principal es la principal");
        mvc.perform(get("/temporadas/{id}/fotos", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminada").value(true))
                .andExpect(jsonPath("$.guardadas").value(2));
    }

    @Test
    void lasFotosDeUnaCarpetaEntranIgualQueLasDelZip() throws Exception {
        catalogo.asegurarArticulo("AMI", "ULL941.AL0001");

        mvc.perform(multipart("/temporadas")
                        .file(pedido("p.xlsx", "x"))
                        .file(new MockMultipartFile("carpeta", "H26/ULL941/a.jpg", "image/jpeg", jpeg()))
                        .param("cliente", "AMI")
                        .param("temporada", "FOTOS-CARPETA"))
                .andExpect(redirectedUrl("/temporadas"));

        esperarFotos(idDe("AMI", "FOTOS-CARPETA"));
        assertEquals(1, fotosCatalogo.de("AMI", "ULL941").size());
    }

    @Test
    void unZipQueNoLoEsGuardaLaTemporadaYAvisa() throws Exception {
        mvc.perform(multipart("/temporadas")
                        .file(pedido("p.xlsx", "x"))
                        .file(new MockMultipartFile("fotos", "fotos.zip", "application/zip", "no".getBytes()))
                        .param("cliente", "AMI")
                        .param("temporada", "FOTOS-MAL"))
                .andExpect(redirectedUrl("/temporadas"))
                .andExpect(flash().attribute("mensaje", containsString("Guardada")))
                .andExpect(flash().attribute("avisosAlta", hasItem(containsString("zip"))));
    }

    private ImportacionFotosTemporada.Estado esperarFotos(Long id) throws InterruptedException {
        long limite = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < limite) {
            ImportacionFotosTemporada.Estado estado = importacionFotos.estado(id).orElseThrow();
            if (estado.terminada()) {
                return estado;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("las fotos no han acabado en 30 s");
    }

    private static byte[] jpeg() throws Exception {
        BufferedImage imagen = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "jpg", salida);
        return salida.toByteArray();
    }

    private static byte[] zip(String... rutas) throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (String ruta : rutas) {
                zip.putNextEntry(new ZipEntry(ruta));
                zip.write(jpeg());
                zip.closeEntry();
            }
        }
        return salida.toByteArray();
    }
}
