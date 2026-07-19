package com.puntotres.packinglist;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.TaraProperties;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.EnvioInput;
import com.puntotres.packinglist.service.AmiGenerador;
import com.puntotres.packinglist.service.EnvioImportService;
import com.puntotres.packinglist.service.EnvioImportado;
import com.puntotres.packinglist.service.ExcelGenerado;
import com.puntotres.packinglist.service.PackingListGenerationService;
import com.puntotres.packinglist.service.PaletAssignmentService;
import com.puntotres.packinglist.service.ResultadoAsignacion;
import com.puntotres.packinglist.service.WeightInferenceService;

/**
 * Prueba manual del flujo completo con el JSON real de ejemplo
 * (client-packinglist/packing_list_ami_test.json): importa el envío,
 * asigna palets, intenta inferir pesos y genera un excel por
 * destinación + referencia + color en target/.
 */
public class Main {

    private static final String JSON_PRUEBA = "/client-packinglist/packing_list_ami_test.json";

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        EnvioInput envio;
        try (InputStream json = Main.class.getResourceAsStream(JSON_PRUEBA)) {
            envio = mapper.readValue(json, EnvioInput.class);
        }

        // Servicios cableados a mano (fuera de Spring) para la prueba manual.
        TaraProperties taras = new TaraProperties();
        taras.setTaras(Map.of("60x40x40", 1.6, "60x40x30", 1.2));
        EnvioImportService importador = new EnvioImportService();
        PaletAssignmentService asignadorPalets = new PaletAssignmentService();
        WeightInferenceService inferidorPesos = new WeightInferenceService(taras);
        PackingListGenerationService generador =
                new PackingListGenerationService(List.of(new AmiGenerador(new AmiExcelBuilder())));

        // Cabecera que no sale de las imágenes (la pondrá la pantalla de revisión).
        DatosEnvio cabecera = new DatosEnvio();
        cabecera.setTemporada("H26");
        cabecera.setNumeroFactura("FACTURA-PENDIENTE");
        cabecera.setFechaFactura("18/07/2026");
        cabecera.setFechaEnvio("31/07/2026");

        ClienteConfig ami = new ClienteConfig();
        ami.setPlantilla(TipoPlantilla.AMI);

        EnvioImportado importado = importador.importar(envio);
        // TODO(web-ui): sustituir estos prints por popups/alertas en la
        // pantalla de revisión (ver TODO en EnvioImportado y ResultadoAsignacion).
        importado.getAvisos().forEach(aviso -> System.out.println("[AVISO importación] " + aviso));

        Files.createDirectories(Path.of("target"));
        for (EnvioImportado.DestinoImportado destino : importado.getDestinos()) {
            System.out.println("\n=== " + destino.getDestino().getNombreDestino()
                    + " (" + destino.getDestino().getCajas().size() + " cajas) ===");

            ResultadoAsignacion palets =
                    asignadorPalets.asignar(destino.getDestino(), destino.getPalets());
            palets.getAvisos().forEach(aviso -> System.out.println("[AVISO palets] " + aviso));
            palets.getCajasSinPalet().forEach(caja ->
                    System.out.println("[SIN PALET] caja " + caja.getNumeroCaja()));

            inferidorPesos.inferirPesosPorReferencia(destino.getDestino().getCajas());

            List<ExcelGenerado> excels =
                    generador.generar(destino.getDestino(), destino.getPalets(), cabecera, ami);
            for (ExcelGenerado excel : excels) {
                Path fichero = Path.of("target", excel.getNombreFichero());
                Files.write(fichero, excel.getContenido());
                String pendientes = excel.tienePesosPendientes()
                        ? " (PESOS PENDIENTES en " + excel.getCajasPendientes().size() + " cajas)"
                        : "";
                System.out.println("Generado: " + fichero + pendientes);
            }
        }
    }
}
