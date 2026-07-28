package com.puntotres.packinglist.service.etiquetasarticulo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EtiquetasArticuloGenerationServiceTest {

    private final EtiquetasArticuloGenerationService servicio =
            new EtiquetasArticuloGenerationService(List.of(
                    new AmiEtiquetasArticuloGenerador(new EtiquetasArticuloExcelBuilder())));

    @Test
    void encuentraElGeneradorDeSuCliente() {
        assertEquals("AMI", servicio.generadorPara("AMI").orElseThrow().claveCliente());
    }

    @Test
    void laClaveEsIndiferenteAMayusculasYEspacios() {
        assertTrue(servicio.generadorPara("  ami ").isPresent());
    }

    @Test
    void unClienteSinGeneradorNoTieneEstaFuncionalidad() {
        assertTrue(servicio.generadorPara("APC").isEmpty());
        assertTrue(servicio.generadorPara(null).isEmpty());
    }
}
