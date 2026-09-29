package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.testutil.FotosDePrueba;
import com.puntotres.packinglist.testutil.PedidoAmiExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel.Fila;

/**
 * El flujo web de los documentos del corte con los beans reales. Cada test
 * usa referencias propias: la memoria de pieles es la misma base de datos en
 * memoria para toda la suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentosCorteControllerTest {

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ArchivoTemporadas archivoTemporadas;

    private final MockHttpSession sesion = new MockHttpSession();

    @AfterEach
    void borrarLoDeLaSesion() throws Exception {
        mvc.perform(get("/documentos-corte/nuevo").session(sesion));
    }

    @Test
    void elMenuEnlazaLosDocumentosDelCorte() throws Exception {
        mvc.perform(get("/menu"))
                .andExpect(content().string(containsString("href=\"/documentos-corte\"")));
    }

    @Test
    void laEntradaOfreceAmiYApcYLosGenericosEnDesarrollo() throws Exception {
        mvc.perform(get("/documentos-corte"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ackermann — en desarrollo")))
                .andExpect(content().string(containsString("A.P.C.")))
                .andExpect(content().string(not(containsString("AMI — en desarrollo"))));
    }

    @Test
    void unClienteGenericoNoPasa() throws Exception {
        mvc.perform(cargar("ACKERMANN", "H26", pedidoAmi("ULL100"), zipFotos("ULL100")))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("en desarrollo")));
    }

    @Test
    void sinFotosNoPasa() throws Exception {
        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("pedido", "pedido.xlsx", XLSX, pedidoAmi("ULL101")))
                        // Lo que manda el selector de carpeta cuando no se ha elegido ninguna.
                        .file(new MockMultipartFile("carpeta", "", "application/octet-stream", new byte[0]))
                        .param("cliente", "AMI").param("temporada", "H26").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("carpeta de fotos")));
    }

    @Test
    void unaCarpetaSubidaSinComprimirLlevaALaTablaConSusFotos() throws Exception {
        // El navegador manda cada fichero con su ruta relativa como nombre.
        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("pedido", "pedido.xlsx", XLSX, pedidoAmi("ULL110")))
                        .file(new MockMultipartFile("carpeta", "H26/ULL110.AL103/b.png", "image/png",
                                FotosDePrueba.jpeg(300, 200, Color.BLUE)))
                        .file(new MockMultipartFile("carpeta", "H26/ULL110.AL103/a.jpg", "image/jpeg",
                                FotosDePrueba.jpeg(200, 300, Color.RED)))
                        .param("cliente", "AMI").param("temporada", "H26").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte/pieles"));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("a.jpg")))
                .andExpect(content().string(containsString("b.png")))
                .andExpect(content().string(not(containsString("no es ningún modelo"))));
    }

    @Test
    void laCarpetaYSuZipALaVezNoPasan() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL111"), zipFotos("ULL111"))
                        .file(new MockMultipartFile("carpeta", "H26/ULL111/a.jpg", "image/jpeg",
                                FotosDePrueba.jpeg(20, 20, Color.RED))))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("no los dos")));
    }

    @Test
    void losModelosSinCarpetaNoSeAvisanPorqueYaLoDiceLaTabla() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL112"), zipFotos("ULL112")));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("sin fotos")))
                .andExpect(content().string(not(containsString("sin carpeta de fotos"))));
    }

    @Test
    void sinTemporadaNiExcelNoPasa() throws Exception {
        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("fotos", "H26.zip", "application/zip", zipFotos("ULL102")))
                        .param("cliente", "AMI").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("temporada guardada")));
    }

    @Test
    void cargarLlevaALaTablaDePieles() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL103"), zipFotos("ULL103")))
                .andExpect(redirectedUrl("/documentos-corte/pieles"));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ULL103")))
                .andExpect(content().string(containsString("AL0103")))
                .andExpect(content().string(containsString("001 BLACK")))
                .andExpect(content().string(containsString("value=\"10\"")))
                .andExpect(content().string(containsString("718 VANILLA CREAM")))
                .andExpect(content().string(containsString("a.jpg")))
                .andExpect(content().string(containsString("sin fotos")))
                .andExpect(content().string(containsString("ULL999")))
                .andExpect(content().string(containsString("ULL745")));
    }

    @Test
    void anadirUnaCombinacionConservaLoTecleado() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL104"), zipFotos("ULL104")));

        mvc.perform(post("/documentos-corte/pieles/combinacion").session(sesion)
                        .param("filas[0].nombrePiel", "Box calf"))
                .andExpect(redirectedUrl("/documentos-corte/pieles"));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("value=\"Box calf\"")))
                .andExpect(content().string(containsString("name=\"filas[0].combinaciones[0]\"")))
                .andExpect(content().string(containsString("Combinación 1")));
    }

    @Test
    void generarDejaLasDescargas() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL105"), zipFotos("ULL105")));
        esperarFotos();

        mvc.perform(post("/documentos-corte/generar").session(sesion)
                        .param("filas[0].nombrePiel", "Box calf")
                        .param("filas[0].bolsos[0]", "10")
                        .param("filas[0].bolsos[1]", "0")
                        .param("filas[0].fotoPrincipal", "1")
                        .param("filas[1].bolsos[0]", "5"))
                .andExpect(redirectedUrl("/documentos-corte/resultados"));

        mvc.perform(get("/documentos-corte/resultados").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ordenes de corte AMI H26.docx")))
                .andExpect(content().string(containsString("Fotos ULL105.AL0103.docx")));

        byte[] word = mvc.perform(get("/documentos-corte/descargar/Ordenes de corte AMI H26.docx")
                        .session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals('P', word[0]);
        assertEquals('K', word[1]);

        byte[] zip = mvc.perform(get("/documentos-corte/descargar-todo").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals(List.of("Ordenes de corte AMI H26.docx", "Fotos ULL105.AL0103.docx"), entradas(zip));
    }

    @Test
    void unNumeroDeBolsosQueNoEsNumeroVuelveALaTablaSinGenerar() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL106"), zipFotos("ULL106")));

        mvc.perform(post("/documentos-corte/generar").session(sesion)
                        .param("filas[0].bolsos[0]", "diez"))
                .andExpect(redirectedUrl("/documentos-corte/pieles"))
                .andExpect(flash().attribute("errores", org.hamcrest.Matchers.hasItem(containsString("diez"))));
    }

    @Test
    void conTemporadaGuardadaNoHaceFaltaSubirElExcel() throws Exception {
        Long id = archivoTemporadas.guardar(null, "AMI", "H27", "pedido.xlsx", pedidoAmi("ULL107")).getId();

        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("fotos", "H27.zip", "application/zip", zipFotos("ULL107")))
                        .param("cliente", "AMI").param("temporadaGuardadaId", String.valueOf(id))
                        .session(sesion))
                .andExpect(redirectedUrl("/documentos-corte/pieles"));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("H27")))
                .andExpect(content().string(containsString("ULL107")));
    }

    @Test
    void unaTemporadaGuardadaDeOtroClienteNoSeUsa() throws Exception {
        Long id = archivoTemporadas.guardar(null, "APC", "E28", "apc.xlsx", pedidoAmi("ULL108")).getId();

        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("fotos", "H26.zip", "application/zip", zipFotos("ULL108")))
                        .param("cliente", "AMI").param("temporadaGuardadaId", String.valueOf(id))
                        .session(sesion))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("no es de este cliente")));
    }

    @Test
    void losNombresDePielSeRecuerdanParaLaSiguienteVez() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL109", "AL0909"), zipFotos("ULL109")));
        esperarFotos();
        mvc.perform(post("/documentos-corte/generar").session(sesion)
                        .param("filas[0].nombrePiel", "Vachette recordada")
                        .param("filas[0].forro", "Cabretilla recordada"))
                .andExpect(redirectedUrl("/documentos-corte/resultados"));
        mvc.perform(get("/documentos-corte/nuevo").session(sesion));

        mvc.perform(cargar("AMI", "H27", pedidoAmi("ULL109", "AL0909"), zipFotos("ULL109")));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("value=\"Vachette recordada\"")))
                .andExpect(content().string(containsString("value=\"Cabretilla recordada\"")));
    }

    @Test
    void siLaSesionHaCaducadoSeDiceEnVezDeVolverALaEntradaSinMas() throws Exception {
        // Rellenar la tabla de una temporada no hace ninguna petición: tras
        // media hora sin actividad la sesión caducaba, y generar mandaba a la
        // entrada sin decir por qué, con todo lo tecleado perdido.
        mvc.perform(post("/documentos-corte/generar").session(sesion)
                        .param("filas[0].nombrePiel", "Box"))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("caducado")));
        mvc.perform(post("/documentos-corte/pieles/combinacion").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("caducado")));
    }

    @Test
    void laTablaDePielesMantieneVivaLaSesionMientrasEstaAbierta() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL111"), zipFotos("ULL111")));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("MANTENER_SESION_MS")));
    }

    @Test
    void elProgresoDeLasFotosSeConsultaSinRecargar() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL110"), zipFotos("ULL110")));

        mvc.perform(get("/documentos-corte/progreso").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"total\":2")));
    }

    /**
     * Generar ya no espera a las fotos dentro de la petición (el botón se
     * enciende al acabar), así que el test espera como lo haría la pantalla:
     * preguntando por el progreso.
     */
    private void esperarFotos() throws Exception {
        for (int intento = 0; intento < 300; intento++) {
            String estado = mvc.perform(get("/documentos-corte/progreso").session(sesion))
                    .andReturn().getResponse().getContentAsString();
            if (estado.contains("\"terminada\":true")) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("las fotos no han acabado de prepararse en 30 s");
    }

    private MockMultipartHttpServletRequestBuilder cargar(String cliente, String temporada,
                                                         byte[] pedido, byte[] fotos) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/documentos-corte/cargar")
                .file(new MockMultipartFile("pedido", "pedido.xlsx", XLSX, pedido))
                .file(new MockMultipartFile("fotos", temporada + ".zip", "application/zip", fotos))
                .param("cliente", cliente)
                .param("temporada", temporada)
                .session(sesion);
    }

    /** Un modelo con dos colores y ULL745, que no trae carpeta de fotos. */
    static byte[] pedidoAmi(String modelo) {
        return pedidoAmi(modelo, "AL0103");
    }

    static byte[] pedidoAmi(String modelo, String piel) {
        return PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", modelo + "." + piel, "001", "BLACK", "U", "07704 CH", null, null, 4),
                new Fila("SPAIN", modelo + "." + piel, "001", "BLACK", "U", 7665, null, null, 6),
                new Fila("SPAIN", modelo + "." + piel, "718", "VANILLA CREAM", "U", 7665, null, null, 3),
                new Fila("SPAIN", "ULL745." + piel, "001", "BLACK", "U", 7665, null, null, 5));
    }

    /** Dos fotos del modelo y una carpeta que no está en el pedido. */
    static byte[] zipFotos(String modelo) throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("H26/" + modelo + "/b.jpg", FotosDePrueba.jpeg(300, 200, Color.BLUE));
        entradas.put("H26/" + modelo + "/a.jpg", FotosDePrueba.jpeg(200, 300, Color.RED));
        entradas.put("H26/ULL999/x.jpg", FotosDePrueba.jpeg(10, 10, Color.GRAY));
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (Map.Entry<String, byte[]> entrada : entradas.entrySet()) {
                zip.putNextEntry(new ZipEntry(entrada.getKey()));
                zip.write(entrada.getValue());
                zip.closeEntry();
            }
        }
        return salida.toByteArray();
    }

    private static List<String> entradas(byte[] zip) throws IOException {
        List<String> nombres = new ArrayList<>();
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry actual;
            while ((actual = entrada.getNextEntry()) != null) {
                nombres.add(actual.getName());
            }
        }
        assertTrue(!nombres.isEmpty());
        return nombres;
    }
}
