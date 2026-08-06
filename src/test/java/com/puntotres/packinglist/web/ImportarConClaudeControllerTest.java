package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.EnvioInput;
import com.puntotres.packinglist.service.ClaudeEnvioExtractionService;

/**
 * Tests del modo CLAUDE de la pantalla de entrada. El servicio de extracción
 * está mockeado (no se llama a la API real): aquí se prueba el cableado del
 * controlador, no la extracción en sí.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ImportarConClaudeControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private ClaudeEnvioExtractionService extractorClaude;

    private EnvioInput envioDePrueba() throws Exception {
        try (var in = getClass().getResourceAsStream("/client-packinglist/packing_list_ami_test.json")) {
            return mapper.readValue(in, EnvioInput.class);
        }
    }

    private MockMultipartFile imagenDePrueba() {
        return new MockMultipartFile("imagenes", "packing.jpg", "image/jpeg",
                new byte[] {1, 2, 3});
    }

    @Test
    void importarConImagenesExtraeConClaudeYLlevaALaRevision() throws Exception {
        when(extractorClaude.extraer(anyList(), any())).thenReturn(envioDePrueba());

        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(multipart("/importar").file(imagenDePrueba()).session(sesion)
                        .param("modo", "CLAUDE")
                        .param("cliente", "AMI")
                        .param("temporada", "H26")
                        .param("numeroFactura", "FA-26-1189")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/revision"));

        // El prompt se monta con el tipo de plantilla del cliente elegido.
        verify(extractorClaude).extraer(anyList(), eq(TipoPlantilla.AMI));

        // La revisión avisa de que los datos vienen de Claude.
        mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Datos extraídos por Claude")));
    }

    @Test
    void importarEnModoClaudeSinImagenesVuelveALaEntradaConError() throws Exception {
        mvc.perform(multipart("/importar")
                        .param("modo", "CLAUDE")
                        .param("cliente", "AMI")
                        .param("temporada", "H26")
                        .param("numeroFactura", "FA-26-1189")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"))
                .andExpect(model().attributeHasFieldErrors("envioForm", "imagenes"));

        verifyNoInteractions(extractorClaude);
    }

    @Test
    void siLaExtraccionFallaSeVuelveALaEntradaConElMensaje() throws Exception {
        when(extractorClaude.extraer(anyList(), any())).thenThrow(
                new ClaudeEnvioExtractionService.ExtraccionException(
                        "Falta configurar la clave de la API de Claude"));

        mvc.perform(multipart("/importar").file(imagenDePrueba())
                        .param("modo", "CLAUDE")
                        .param("cliente", "AMI")
                        .param("temporada", "H26")
                        .param("numeroFactura", "FA-26-1189")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"))
                .andExpect(content().string(containsString(
                        "No se pudo extraer el packing list con Claude")));
    }

    @Test
    void unResumenQueDeclaraUnaDestinacionSinHojasBloqueaSinPasarARevision() throws Exception {
        // Caso real: el resumen dice "wh. 5 palet" pero el escaneo no trae
        // las hojas de WHOLESALE. No se pasa a revisión; el JSON extraído se
        // vuelca al textarea para no perder el trabajo de la API.
        EnvioInput envio = envioDePrueba();
        EnvioInput.ResumenPaletsInput declarado = new EnvioInput.ResumenPaletsInput();
        declarado.setDestino("WHOLESALE");
        declarado.setPalets(5);
        envio.setResumenPalets(List.of(declarado));
        when(extractorClaude.extraer(anyList(), any())).thenReturn(envio);

        mvc.perform(multipart("/importar").file(imagenDePrueba())
                        .param("modo", "CLAUDE")
                        .param("cliente", "AMI")
                        .param("temporada", "H26")
                        .param("numeroFactura", "FA-26-1189")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"))
                .andExpect(content().string(containsString("falta una hoja")))
                // El textarea de JSON trae el envío extraído, en modo JSON.
                .andExpect(content().string(containsString("&quot;resumenPalets&quot;")));
    }

    @Test
    void losAvisosDeLecturaDeLaExtraccionLleganALaRevision() throws Exception {
        EnvioInput envio = envioDePrueba();
        envio.setAvisos(List.of("La caja 6 trae dos pesos (12,82 y 13,94): se usa 13,94"));
        when(extractorClaude.extraer(anyList(), any())).thenReturn(envio);

        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(multipart("/importar").file(imagenDePrueba()).session(sesion)
                        .param("modo", "CLAUDE")
                        .param("cliente", "AMI")
                        .param("temporada", "H26")
                        .param("numeroFactura", "FA-26-1189")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lectura de las hojas:")))
                .andExpect(content().string(containsString("dos pesos")));
    }

    @Test
    void laEntradaMuestraElSelectorDeModosConFormularioDeshabilitado() throws Exception {
        mvc.perform(get("/packing-list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("botonModoJSON")))
                .andExpect(content().string(containsString("botonModoCLAUDE")))
                .andExpect(content().string(containsString("botonModoFORMULARIO")))
                .andExpect(content().string(containsString("Escaneos del packing list")))
                .andExpect(content().string(containsString("application/pdf")));
    }
}
