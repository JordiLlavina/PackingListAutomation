package com.puntotres.packinglist.service.taller;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.DestinoClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;

/**
 * Herramienta manual: convierte de verdad, con la API de Claude, los dos
 * excels de taller de ejemplo (Fez y Tánger) como cliente APC, con el pedido
 * de Fall 26 y una tabla de colores de prueba, deja el resultado en
 * target/conversion-taller/ (el excel y el JSON, para comparar dos
 * ejecuciones con un diff) y lo relee con {@link TallerColisExcel}, que es lo
 * que hará la digestión.
 *
 * Imprime además lo que ha costado: segundos, tokens de entrada y de salida,
 * y cuántas filas han quedado con color en código y con código de pedido,
 * que es la medida de si el índice y el ajuste hacen su trabajo. Con
 * {@code -Dconversion.esfuerzo=medium} se mide el esfuerzo bajo.
 *
 * Gasta dinero (unos céntimos por fichero) y tarda uno o dos minutos por
 * fichero, por eso no es un test: no lo ejecuta {@code mvn test}.
 *
 * <p>Ejecutar: mvn test -Dtest=ConversionFormatoTallerRealTool
 * (necesita ANTHROPIC_API_KEY).
 */
class ConversionFormatoTallerRealTool {

    private static final Path SALIDA = Path.of("target", "conversion-taller");

    /**
     * Una tabla de colores de prueba. En la aplicación sale de Colores y
     * Pieles de Cliente; aquí se pone a mano con los nombres que escriben
     * los talleres en los dos ejemplos para ver hasta dónde llega el ajuste.
     * Los nombres de KAN, GAE y CAB son suposiciones de esta prueba, no del
     * cliente.
     */
    private static final Map<String, String> COLORES_PRUEBA = Map.of(
            "LZZ", "NOIR", "LAW", "GRIS", "KAN", "LIQUEN", "GAE", "VINO", "CAB", "CEREZA");

    @Test
    void convertirLosEjemplosReales() throws Exception {
        Files.createDirectories(SALIDA);
        PedidoParaConversion pedido = PedidoParaConversion.cargar(TipoPlantilla.APC,
                leer("/ejemplos/APC_PEDIDO_FALL26.xlsx"), COLORES_PRUEBA, clienteApc());
        System.out.println("Índice del pedido que viaja a Claude:\n"
                + IndiceCatalogoConversion.para(pedido).texto());
        ConversionFormatoTallerService servicio = new ConversionFormatoTallerService(new ObjectMapper());
        ObjectMapper bonito = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        for (String nombre : new String[] {
                "packing taller fez ejemplo.xlsx", "packing taller tanger ejemplos.xlsx"}) {
            long inicio = System.currentTimeMillis();
            ConversionFormatoTallerService.Resultado resultado = servicio.convertir(
                    leer("/ejemplos/taller/" + nombre), nombre, "APC", pedido, null);
            long segundos = (System.currentTimeMillis() - inicio) / 1000;

            Path fichero = SALIDA.resolve(nombre.replace(".xlsx", " - Formato Puntotres.xlsx"));
            Files.write(fichero, resultado.excel());
            Files.writeString(SALIDA.resolve(nombre.replace(".xlsx", ".json")),
                    bonito.writeValueAsString(resultado.packing()));

            PackingFormatoPuntotres packing = resultado.packing();
            System.out.println("\n===== " + nombre + " -> " + fichero);
            System.out.println("hoja: " + packing.hoja() + "   no convertidas: "
                    + packing.hojasNoConvertidas() + "   cabecera: " + packing.cabecera());
            packing.filas().forEach(fila -> System.out.println("  " + fila));
            System.out.println("notas:");
            packing.notas().forEach(nota -> System.out.println("  " + nota));
            System.out.println("resumen de la hoja: " + packing.resumen() + "   filas suman "
                    + packing.unidadesConvertidas() + " unidades y " + packing.cajasConvertidas()
                    + " cajas");
            System.out.println("avisos:");
            packing.avisos().forEach(aviso -> System.out.println("  - " + aviso.frase()));

            Map<String, Object> medida = new LinkedHashMap<>();
            medida.put("segundos", segundos);
            medida.put("esfuerzo", ConversionFormatoTallerService.ESFUERZO.asString());
            medida.put("tokensEntrada", resultado.uso().tokensEntrada());
            medida.put("tokensSalida", resultado.uso().tokensSalida());
            medida.put("filas", packing.filas().size());
            medida.put("filasConColorEnCodigo", packing.filas().stream()
                    .filter(f -> f.color() != null && f.color().matches("[A-Z]{3}")).count());
            medida.put("filasConCodigoDePedido", packing.filas().stream()
                    .filter(f -> f.codigoCliente() != null && !f.codigoCliente().isBlank()).count());
            medida.put("avisos", packing.avisos().size());
            System.out.println("MEDIDA " + medida);

            TallerColisExcel releido = TallerColisExcel.desdeBytes(resultado.excel());
            System.out.println("releído por TallerColisExcel: " + releido.lineas().size()
                    + " líneas (" + packing.filas().size() + " filas convertidas), avisos "
                    + releido.avisos());
        }
    }

    private static ClienteConfig clienteApc() {
        ClienteConfig cliente = new ClienteConfig();
        DestinoClienteConfig wholesale = new DestinoClienteConfig();
        wholesale.setAbreviatura("WH");
        wholesale.setDestinosHijo(List.of("AUSTRALIA", "WHOLESALE", "CHINE FRANCH"));
        DestinoClienteConfig usa = new DestinoClienteConfig();
        usa.setAbreviatura("UST");
        usa.setDestinosHijo(List.of("DOUANES USA", "D. USA", "RETAIL USA"));
        DestinoClienteConfig retail = new DestinoClienteConfig();
        retail.setAbreviatura("RT");
        retail.setDestinosHijo(List.of("RETAIL", "WHOLESALE CONCESS"));
        DestinoClienteConfig japan = new DestinoClienteConfig();
        japan.setAbreviatura("JPT");
        DestinoClienteConfig korea = new DestinoClienteConfig();
        korea.setAbreviatura("KRT");
        cliente.setDestinos(Map.of("WHOLESALE", wholesale, "D. USA", usa, "RETAIL", retail,
                "JAPAN", japan, "KOREA", korea));
        return cliente;
    }

    private static byte[] leer(String recurso) throws Exception {
        try (InputStream in = ConversionFormatoTallerRealTool.class.getResourceAsStream(recurso)) {
            return in.readAllBytes();
        }
    }
}
