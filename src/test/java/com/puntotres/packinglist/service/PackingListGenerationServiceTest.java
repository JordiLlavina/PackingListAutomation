package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

/**
 * El servicio ya no genera nada por sí mismo: solo despacha al
 * {@link GeneradorPackingListCliente} registrado para la plantilla del
 * cliente. La generación real (agrupado, plantilla Excel...) se prueba en
 * el test de cada generador (p. ej. AmiGeneradorTest).
 */
class PackingListGenerationServiceTest {

    private static final ExcelGenerado UN_EXCEL =
            new ExcelGenerado("desc", "fichero.xlsx", new byte[0], List.of());

    private static class GeneradorDePrueba implements GeneradorPackingListCliente {
        private final TipoPlantilla tipo;
        private DestinoData destinoRecibido;
        private List<PaletData> paletsRecibidos;
        private ClienteConfig clienteRecibido;

        GeneradorDePrueba(TipoPlantilla tipo) {
            this.tipo = tipo;
        }

        @Override
        public TipoPlantilla tipo() {
            return tipo;
        }

        @Override
        public List<ExcelGenerado> generar(DestinoData destino, List<PaletData> palets,
                                           DatosEnvio envio, ClienteConfig cliente) {
            this.destinoRecibido = destino;
            this.paletsRecibidos = palets;
            this.clienteRecibido = cliente;
            return List.of(UN_EXCEL);
        }
    }

    private static ClienteConfig clienteDe(TipoPlantilla tipo) {
        ClienteConfig cliente = new ClienteConfig();
        cliente.setPlantilla(tipo);
        return cliente;
    }

    @Test
    void despachaAlGeneradorRegistradoParaLaPlantillaDelCliente() throws Exception {
        GeneradorDePrueba generadorAmi = new GeneradorDePrueba(TipoPlantilla.AMI);
        GeneradorDePrueba generadorApc = new GeneradorDePrueba(TipoPlantilla.APC);
        PackingListGenerationService service =
                new PackingListGenerationService(List.of(generadorAmi, generadorApc));

        DestinoData destino = new DestinoData();
        DatosEnvio envio = new DatosEnvio();
        ClienteConfig apc = clienteDe(TipoPlantilla.APC);

        List<PaletData> palets = List.of();
        List<ExcelGenerado> resultado = service.generar(destino, palets, envio, apc);

        assertEquals(List.of(UN_EXCEL), resultado);
        assertEquals(destino, generadorApc.destinoRecibido);
        assertEquals(palets, generadorApc.paletsRecibidos);
        assertEquals(apc, generadorApc.clienteRecibido);
        // El generador AMI no se invoca porque el cliente es APC.
        assertEquals(null, generadorAmi.destinoRecibido);
    }

    @Test
    void plantillaSinGeneradorRegistradoLanzaExcepcionClara() {
        PackingListGenerationService service = new PackingListGenerationService(List.of());

        Exception ex = assertThrows(IllegalStateException.class, () ->
                service.generar(new DestinoData(), List.of(), new DatosEnvio(), clienteDe(TipoPlantilla.GENERIC)));
        assertTrue(ex.getMessage().contains("GENERIC"));
    }

    private static void assertTrue(boolean condicion) {
        org.junit.jupiter.api.Assertions.assertTrue(condicion);
    }
}
