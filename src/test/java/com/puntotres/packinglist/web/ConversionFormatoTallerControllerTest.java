package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.puntotres.packinglist.persistence.TablaColores;
import com.puntotres.packinglist.service.taller.ConversionFormatoTallerService;
import com.puntotres.packinglist.service.taller.FormatoPuntotresExcelBuilder;
import com.puntotres.packinglist.service.taller.PackingFormatoPuntotres;
import com.puntotres.packinglist.service.taller.PedidoParaConversion;
import com.puntotres.packinglist.testutil.PedidoAmiExcel;

/**
 * "Excel Formato Taller" en la entrada: convertir sin salir de la pantalla,
 * descargar lo convertido y digerirlo. La conversión con Claude está
 * sustituida; aquí se prueba el cableado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConversionFormatoTallerControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TablaColores tablaColores;

    @MockitoBean
    private ConversionFormatoTallerService conversion;

    private MockHttpSession sesion;

    @BeforeEach
    void nuevaSesion() {
        sesion = new MockHttpSession();
    }

    private static final String REFERENCIA = "ULL777.CONV01";

    /** Lo que habría devuelto Claude: una línea de AMI a China, con una nota. */
    private static ConversionFormatoTallerService.Resultado convertido(List<String> hojasFuera) {
        PackingFormatoPuntotres packing = new PackingFormatoPuntotres("LISTE DE COL", hojasFuera,
                new PackingFormatoPuntotres.Cabecera("SWEET BAG SARL", null, null, null),
                List.of(new PackingFormatoPuntotres.Fila(null, "AMI", null, REFERENCIA, "KAKI",
                        "U", "CHINA", null, 10, 20, "1-2", null, null)),
                List.of(List.of("CAJA 2", "MAL")),
                null,
                List.of(PackingFormatoPuntotres.Aviso.deFila(1, PackingFormatoPuntotres.Columna.CODIGO_CLIENTE, "varios pedidos posibles",
                        "Para F63023 en LAW el pedido tiene varios números"),
                        PackingFormatoPuntotres.Aviso.deFila(2, PackingFormatoPuntotres.Columna.CODIGO_CLIENTE, "varios pedidos posibles",
                        "Para F67043 en LAW el pedido tiene varios números"),
                        PackingFormatoPuntotres.Aviso.general("La hoja declara 60 y suman 58"),
                        PackingFormatoPuntotres.Aviso.informativo(1, PackingFormatoPuntotres.Columna.COLOR, "color LZZ por la tabla")));
        return new ConversionFormatoTallerService.Resultado(packing,
                FormatoPuntotresExcelBuilder.construir(packing),
                new ConversionFormatoTallerService.Uso(1234, 567));
    }

    private MockMultipartHttpServletRequestBuilder peticionDeConvertir() {
        MockMultipartHttpServletRequestBuilder peticion =
                multipart("/packing-list/taller/convertir");
        peticion.session(sesion);
        peticion.file(new MockMultipartFile("excelTallerOriginal", "packing fez.xlsx", null,
                new byte[] {1, 2, 3}));
        return peticion;
    }

    private MockMultipartHttpServletRequestBuilder peticionDeDigerir() {
        MockMultipartHttpServletRequestBuilder peticion =
                multipart("/packing-list/taller/digerir");
        peticion.session(sesion);
        peticion.file(new MockMultipartFile("pedidoCliente", "EAN H26.xlsx", null,
                PedidoAmiExcel.crear("EAN H26",
                        PedidoAmiExcel.Fila.pedida(REFERENCIA, "KAKI", "U", "07001 CH", 20))));
        peticion.param("modo", "TALLER").param("cliente", "AMI").param("temporada", "H26")
                .param("fechaFactura", "05/09/2026").param("fechaEnvio", "05/09/2026");
        return peticion;
    }

    @Test
    void laEntradaOfreceLosDosFormatosYLaConversion() throws Exception {
        mvc.perform(get("/packing-list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Excel Formato Puntotres")))
                .andExpect(content().string(containsString("Excel Formato Taller")))
                .andExpect(content().string(containsString("Generar Formato con IA")))
                .andExpect(content().string(containsString("Packing de Taller Formato Puntotres")))
                .andExpect(content().string(containsString("id=\"hojaElegida\"")));
    }

    @Test
    void convertirContestaSinRecargarYDejaLaDescargaPreparada() throws Exception {
        ConversionFormatoTallerService.Resultado resultado = convertido(List.of());
        when(conversion.convertir(any(), anyString(), eq("AMI"), any(), isNull()))
                .thenReturn(resultado);

        mvc.perform(peticionDeConvertir().param("cliente", "AMI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.nombreFichero").value("packing fez - Formato Puntotres.xlsx"))
                .andExpect(jsonPath("$.filas").value(1))
                .andExpect(jsonPath("$.notas").value(1))
                .andExpect(jsonPath("$.hojasNoConvertidas").isEmpty())
                // Los del fichero sueltos; los de fila plegados por consecuencia
                // con su recuento; lo completado solo se cuenta.
                .andExpect(jsonPath("$.avisos[0]").value("La hoja declara 60 y suman 58"))
                .andExpect(jsonPath("$.avisos.length()").value(1))
                .andExpect(jsonPath("$.bloques.length()").value(1))
                .andExpect(jsonPath("$.bloques[0].consecuencia").value("varios pedidos posibles"))
                .andExpect(jsonPath("$.bloques[0].recuento").value("2 filas"))
                .andExpect(jsonPath("$.bloques[0].filas[1].rotulo").value("Fila 2"))
                .andExpect(jsonPath("$.bloques[0].filas[1].texto")
                        .value(containsString("F67043")))
                .andExpect(jsonPath("$.completados").value(1))
                .andExpect(jsonPath("$.informativos").doesNotExist())
                .andExpect(jsonPath("$.tokens.entrada").value(1234));

        byte[] descargado = mvc.perform(get("/packing-list/taller/formato-puntotres").session(sesion))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("packing%20fez%20-%20Formato%20Puntotres.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(resultado.excel(), descargado);
    }

    /**
     * La conversión recibe el pedido ya cargado con la tabla de colores del
     * cliente: es lo que permite pasar "Negro" a LZZ. Y la hoja elegida, si
     * el usuario pide una concreta de un libro con varios envíos.
     */
    @Test
    void laConversionRecibeElPedidoConLaTablaDeColoresYLaHojaElegida() throws Exception {
        tablaColores.guardar("APC", "LZZ", "NOIR");
        when(conversion.convertir(any(), anyString(), eq("APC"), any(), eq("01-08")))
                .thenReturn(convertido(List.of("11-07")));

        mvc.perform(peticionDeConvertir().param("cliente", "APC").param("hojaElegida", "01-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hojasNoConvertidas[0]").value("11-07"))
                .andExpect(jsonPath("$.nombreFichero")
                        .value("packing fez (LISTE DE COL) - Formato Puntotres.xlsx"));

        ArgumentCaptor<PedidoParaConversion> pedido = ArgumentCaptor.forClass(PedidoParaConversion.class);
        verify(conversion).convertir(any(), anyString(), eq("APC"), pedido.capture(), eq("01-08"));
        assertEquals("NOIR", pedido.getValue().coloresCliente().get("LZZ"));
        assertEquals("LZZ", pedido.getValue().codigoDeColor("noir").orElseThrow());
    }

    /** Lo que se digiere con "Excel Formato Taller" es lo convertido, no lo subido. */
    @Test
    void digerirConFormatoTallerUsaElExcelConvertido() throws Exception {
        when(conversion.convertir(any(), anyString(), any(), any(), any()))
                .thenReturn(convertido(List.of()));
        mvc.perform(peticionDeConvertir().param("cliente", "AMI")).andExpect(status().isOk());

        mvc.perform(peticionDeDigerir().param("formatoExcel", "TALLER"))
                .andExpect(status().isOk())
                .andExpect(view().name("taller-ajuste"))
                .andExpect(model().attribute("nombreExcelTaller",
                        "packing fez - Formato Puntotres.xlsx"));
    }

    @Test
    void digerirConFormatoTallerSinConvertirVuelveALaEntradaDiciendoQueFalta() throws Exception {
        mvc.perform(peticionDeDigerir().param("formatoExcel", "TALLER"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"))
                .andExpect(content().string(containsString("Generar Formato con IA")))
                .andExpect(model().attributeHasFieldErrors("envioForm", "excelTallerOriginal"));
    }

    @Test
    void sinClienteNiFicheroNoSeLlamaAClaude() throws Exception {
        mvc.perform(peticionDeConvertir())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error").value(containsString("cliente")));

        mvc.perform(multipart("/packing-list/taller/convertir").session(sesion)
                        .param("cliente", "AMI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("excel")));

        verifyNoInteractions(conversion);
    }

    /** Un fallo de la conversión se enseña y deja digerir apagado: no queda nada viejo. */
    @Test
    void unaConversionFallidaNoDejaLaAnteriorADisposicion() throws Exception {
        when(conversion.convertir(any(), anyString(), any(), any(), any()))
                .thenReturn(convertido(List.of()))
                .thenThrow(new ConversionFormatoTallerService.ConversionException("Claude caído"));
        mvc.perform(peticionDeConvertir().param("cliente", "AMI")).andExpect(status().isOk());

        mvc.perform(peticionDeConvertir().param("cliente", "AMI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Claude caído"));

        mvc.perform(get("/packing-list/taller/formato-puntotres").session(sesion))
                .andExpect(status().isNotFound());
        assertEquals("packing fez - Formato Puntotres.xlsx",
                PackingListTallerController.nombreConvertido("packing fez.xlsx", "X", true));
        assertEquals("packing fez (01-08) - Formato Puntotres.xlsx",
                PackingListTallerController.nombreConvertido("packing fez.xlsx", "01-08", false));
    }
}
