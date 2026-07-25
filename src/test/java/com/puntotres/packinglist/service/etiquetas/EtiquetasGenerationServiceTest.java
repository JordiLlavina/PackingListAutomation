package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.service.EnvioImportado;

class EtiquetasGenerationServiceTest {

    /** Doble mínimo: solo interesa el despacho por clave. */
    private static GeneradorEtiquetasCliente generadorDe(String clave) {
        return new GeneradorEtiquetasCliente() {
            @Override public String claveCliente() { return clave; }
            @Override public boolean soportaDestino(String nombreDestino) { return true; }
            @Override public List<CampoEtiquetas> camposRequeridos(List<DestinoData> destinos) {
                return List.of();
            }
            @Override public ResultadoEtiquetas generar(List<EnvioImportado.DestinoImportado> destinos,
                    DatosEnvio envio, Map<String, byte[]> archivos) {
                return new ResultadoEtiquetas();
            }
        };
    }

    @Test
    void despachaPorClaveDeClienteIgnorandoMayusculas() {
        GeneradorEtiquetasCliente ami = generadorDe("AMI");
        EtiquetasGenerationService servicio = new EtiquetasGenerationService(List.of(ami));

        assertSame(ami, servicio.generadorPara("ami").orElseThrow());
        assertSame(ami, servicio.generadorPara(" AMI ").orElseThrow());
    }

    @Test
    void clienteSinGeneradorDevuelveVacio() {
        EtiquetasGenerationService servicio =
                new EtiquetasGenerationService(List.of(generadorDe("AMI")));
        assertTrue(servicio.generadorPara("ACKERMANN").isEmpty());
        assertTrue(servicio.generadorPara(null).isEmpty());
    }
}
