package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.testutil.PedidoAmiExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel.Fila;

/**
 * Tests del flujo web de etiquetas de artículo con los beans reales.
 *
 * En particular cubre el saneado de nombreFichero: AmiEtiquetasArticuloGenerador
 * compone el nombre con la columna "Made in" del pedido y con la temporada
 * tecleada por el usuario SIN sanear (a diferencia del nombre de hoja, que sí
 * sanea); un "/" en cualquiera de los dos llegaría tal cual al controlador,
 * que es quien lo escribe como segmento de URL de descarga y como nombre de
 * entrada de ZIP.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EtiquetasArticuloControllerTest {

    private static final String EAN_VALIDO = "3666598543892";

    @Autowired
    private MockMvc mvc;

    /** Un pedido de AMI con un "/" en la columna Made in: el dato conflictivo. */
    private static byte[] pedidoConMadeInProblematico() {
        return PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO/TEST", "USL738.AL0137", "A236", "TRUFFLE", "U",
                        "07714 CH", EAN_VALIDO));
    }

    private MockHttpSession generarConMadeInProblematico() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(multipart("/etiquetas-articulo/generar")
                        .file(new MockMultipartFile("pedido", "EAN PUNTOTRES H26.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                pedidoConMadeInProblematico()))
                        .param("cliente", "AMI")
                        .param("temporada", "H26")
                        .session(sesion))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/etiquetas-articulo/resultados"));
        return sesion;
    }

    @Test
    void unMadeInConBarraNoRompeLaPantallaDeResultadosNiElNombreDeFichero() throws Exception {
        MockHttpSession sesion = generarConMadeInProblematico();

        String html = mvc.perform(get("/etiquetas-articulo/resultados").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // La descripción (texto libre) sí conserva el "Made in" tal cual del
        // pedido -- no es un problema, es solo una celda de tabla. Lo que
        // importa es nombreFichero, que es lo que se usa como segmento de
        // URL y como nombre de entrada de ZIP: ese no debe traer la barra,
        // porque el href generado por Thymeleaf la codificaría como %2F, que
        // Tomcat rechaza por defecto.
        assertFalse(html.contains("MOROCCO/TEST.xlsx"), html);
        assertTrue(html.contains("MOROCCO_TEST.xlsx"), html);
    }

    @Test
    void unMadeInConBarraSeDescargaConElNombreSaneado() throws Exception {
        MockHttpSession sesion = generarConMadeInProblematico();

        mvc.perform(get("/etiquetas-articulo/descargar/AMI CODE BARRE H26 MOROCCO_TEST.xlsx")
                        .session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void unMadeInConBarraNoGeneraUnaEntradaDeZipConSubdirectorio() throws Exception {
        MockHttpSession sesion = generarConMadeInProblematico();

        byte[] zip = mvc.perform(get("/etiquetas-articulo/descargar-todo").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andReturn().getResponse().getContentAsByteArray();

        List<String> entradas = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entrada = in.getNextEntry(); entrada != null; entrada = in.getNextEntry()) {
                entradas.add(entrada.getName());
            }
        }
        assertTrue(entradas.contains("AMI CODE BARRE H26 MOROCCO_TEST.xlsx"), entradas.toString());
        // Ninguna entrada trae "/": una entrada con barra crearía un
        // subdirectorio dentro del ZIP en vez de un fichero suelto.
        assertTrue(entradas.stream().noneMatch(nombre -> nombre.contains("/")), entradas.toString());
    }

    @Test
    void laPantallaOfreceAmiDisponibleYElRestoEnDesarrollo() throws Exception {
        mvc.perform(get("/etiquetas-articulo"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Introducir excel del pedido de AMI")))
                .andExpect(content().string(containsString("en desarrollo")));
    }
}
