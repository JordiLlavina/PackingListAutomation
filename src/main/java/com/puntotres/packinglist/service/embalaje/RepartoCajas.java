package com.puntotres.packinglist.service.embalaje;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.puntotres.packinglist.model.DatosHojaEmbalaje;

/**
 * El «reparto en cajas» de la hoja de embalaje, leído línea a línea. Desde que
 * va dentro de la tabla de destinaciones (columna CAJAS) solo admite un
 * formato, el mismo que escribe el cálculo de la pantalla
 * ({@code textoReparto} en {@code reparto-cajas.js}):
 *
 * <pre>
 *   4 CAJAS X 10 BOLSOS: 40 - JAPAN
 *   1 CAJA X 7 BOLSOS: 7 - RETAIL
 * </pre>
 *
 * Sin mirar mayúsculas ni espacios de más; las líneas en blanco no cuentan.
 * Esta clase es la autoridad: el servidor rechaza con ella una hoja mal
 * escrita y el Word la usa para colocar cada línea en su destinación. La
 * pantalla repite las mismas reglas y los mismos mensajes en
 * {@code comprobarReparto} (reparto-cajas.js), para avisar mientras se teclea.
 *
 * Dos clases de problema: los <b>errores</b> bloquean la generación (una
 * línea con otro formato, una destinación que no está en la tabla, unas
 * cuentas que no salen) y los <b>avisos</b> no (las cajas de una destinación
 * no suman su cantidad: puede ser una entrega parcial, lo decide quien lo
 * escribe). Mezclar destinaciones en una caja ya no se puede escribir, por
 * decisión del usuario.
 */
public final class RepartoCajas {

    /**
     * N CAJA(S) X M BOLSO(S): TOTAL - DESTINO. Números de hasta seis cifras,
     * para que ninguno desborde, y un destino que empieza por algo que no es
     * un espacio (con {@code .+?} a secas, «40 -   » daría un destino en blanco).
     */
    static final Pattern LINEA = Pattern.compile(
            "^\\s*(\\d{1,6})\\s+CAJAS?\\s+X\\s+(\\d{1,6})\\s+BOLSOS?\\s*:\\s*(\\d{1,6})\\s*-\\s*(\\S.*?)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Lo que se le enseña a quien escribe una línea que no se entiende. */
    public static final String FORMATO = "N CAJAS X M BOLSOS: TOTAL - DESTINACIÓN";

    private static final Locale CASTELLANO = Locale.forLanguageTag("es");

    private RepartoCajas() {
    }

    /** Una línea bien escrita: {@code cajas} cajas de {@code bolsosPorCaja}, {@code total} bolsos a {@code destino}. */
    public record LineaReparto(int cajas, int bolsosPorCaja, int total, String destino) {

        /** Lo que va en la columna CAJAS: «12 CAJAS X 10 BOLSOS», «1 CAJA X 7 BOLSOS». */
        public String cajasTexto() {
            return RepartoCajas.cajas(cajas) + " X " + bolsosPorCaja + (bolsosPorCaja == 1 ? " BOLSO" : " BOLSOS");
        }
    }

    /**
     * El reparto leído: las líneas buenas agrupadas por fila de la tabla (en
     * el orden de la tabla y, dentro de cada fila, en el del texto), los
     * errores y avisos con su número de línea, y el texto de las líneas que
     * no se han podido colocar (para las hojas guardadas antes de que hubiera
     * formato, que se siguen imprimiendo debajo de la tabla).
     */
    public record Resultado(List<List<LineaReparto>> porDestinacion, List<String> errores, List<String> avisos,
                            List<String> sinColocar) {

        public Resultado {
            porDestinacion = porDestinacion.stream().map(List::copyOf).toList();
            errores = List.copyOf(errores);
            avisos = List.copyOf(avisos);
            sinColocar = List.copyOf(sinColocar);
        }

        /** Las líneas de la fila {@code indice} de la tabla; vacía si no tiene ninguna. */
        public List<LineaReparto> lineasDe(int indice) {
            return indice >= 0 && indice < porDestinacion.size() ? porDestinacion.get(indice) : List.of();
        }

        /** Si alguna línea va a la columna CAJAS. */
        public boolean hayLineas() {
            return porDestinacion.stream().anyMatch(lineas -> !lineas.isEmpty());
        }

        /** Las cajas de todas las líneas colocadas: la cifra de la fila Total. */
        public int totalCajas() {
            return porDestinacion.stream().flatMap(List::stream).mapToInt(LineaReparto::cajas).sum();
        }

        public boolean tieneErrores() {
            return !errores.isEmpty();
        }
    }

    /** Una fila de la tabla de destinaciones, tal como está escrita. */
    public record Destinacion(String destino, int cantidad) {
    }

    /** «25 CAJAS», «1 CAJA». */
    public static String cajas(int numero) {
        return numero + (numero == 1 ? " CAJA" : " CAJAS");
    }

    /** El reparto de una hoja, leído contra su propia tabla de destinaciones. */
    public static Resultado de(DatosHojaEmbalaje datos) {
        return leer(datos.repartoCajas(), datos.destinaciones().stream()
                .map(d -> new Destinacion(d.destino(), d.cantidad()))
                .toList());
    }

    /**
     * Lee el texto contra las filas de la tabla. Cada línea se cuenta desde 1,
     * también las que van en blanco, para que el número sea el renglón que se
     * ve en el campo. Una destinación se reconoce sin mirar mayúsculas ni
     * espacios; si dos filas se llaman igual, la línea va a la primera.
     */
    public static Resultado leer(String texto, List<Destinacion> destinaciones) {
        List<String> nombres = new ArrayList<>();
        List<List<LineaReparto>> porDestinacion = new ArrayList<>();
        for (Destinacion destinacion : destinaciones) {
            nombres.add(normalizar(destinacion.destino()));
            porDestinacion.add(new ArrayList<>());
        }
        List<String> errores = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        List<String> sinColocar = new ArrayList<>();
        boolean algunaBuena = false;

        String[] lineas = texto == null || texto.isBlank() ? new String[0] : texto.split("\\R", -1);
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i].replace(' ', ' ');
            if (linea.isBlank()) {
                continue;
            }
            String prefijo = "Línea " + (i + 1) + ": ";
            Matcher partes = LINEA.matcher(linea);
            if (!partes.matches()) {
                errores.add(prefijo + "«" + linea.strip() + "» no tiene el formato " + FORMATO);
                sinColocar.add(linea);
                continue;
            }
            int cajas = Integer.parseInt(partes.group(1));
            int porCaja = Integer.parseInt(partes.group(2));
            int total = Integer.parseInt(partes.group(3));
            String destino = partes.group(4).strip().replaceAll("\\s+", " ");
            int fila = nombres.indexOf(normalizar(destino));
            boolean buena = true;
            if (cajas * porCaja != total) {
                errores.add(prefijo + (cajas == 1 ? "1 caja de " + porCaja + " es " : cajas + " cajas de " + porCaja
                        + " son ") + cajas * porCaja + ", no " + total);
                buena = false;
            }
            if (fila < 0) {
                errores.add(prefijo + destino + " no está en la tabla de destinaciones");
                buena = false;
            }
            if (buena) {
                porDestinacion.get(fila).add(new LineaReparto(cajas, porCaja, total, destino));
                algunaBuena = true;
            } else {
                sinColocar.add(linea);
            }
        }

        // Sin ninguna línea buena no hay reparto que cuadrar: un campo vacío
        // no debe llenar la pantalla de avisos por cada destinación.
        if (algunaBuena) {
            for (int i = 0; i < destinaciones.size(); i++) {
                Destinacion destinacion = destinaciones.get(i);
                if (nombres.get(i).isEmpty() || nombres.indexOf(nombres.get(i)) != i) {
                    continue;
                }
                int suma = porDestinacion.get(i).stream().mapToInt(LineaReparto::total).sum();
                if (suma != destinacion.cantidad()) {
                    avisos.add(destinacion.destino().strip() + ": las cajas suman " + suma
                            + " y la cantidad es " + destinacion.cantidad());
                }
            }
        }
        return new Resultado(porDestinacion, errores, avisos, sinColocar);
    }

    /** Recortado, con los espacios de dentro reducidos a uno y en mayúsculas. */
    private static String normalizar(String destino) {
        return destino == null ? "" : destino.strip().replaceAll("\\s+", " ").toUpperCase(CASTELLANO);
    }
}
