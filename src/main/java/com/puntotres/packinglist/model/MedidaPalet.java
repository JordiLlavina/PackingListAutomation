package com.puntotres.packinglist.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * La medida de un palet CARGADO tal como la leen el cliente y la aduana
 * ("80x120x91"): la base es siempre la de un palet europeo, 80x120, y el alto
 * es el de sus cajas apiladas más lo que mide el palet vacío. Es la que sale
 * en PALLETS y en el volumen bruto del packing de APC, en la fila de cada
 * palet del genérico y en el rótulo de palet del Packing Puntotres: se
 * calcula aquí para que los tres documentos digan lo mismo.
 *
 * <p>Cómo se calcula el alto, por decisión del usuario (noviembre de 2026):
 * <ul>
 * <li>Cuentan las cajas que lleva el palet DE VERDAD, no un número fijo de
 *     cajas por palet.</li>
 * <li>Las cajas van por niveles. En un nivel caben las que caben en la base:
 *     cuatro de 60x40 y dos de 70x60 ({@link #cajasPorNivel}). Una caja ocupa
 *     la fracción del nivel que le toca, así que una de 70x60 y dos de 60x40
 *     llenan uno entre las tres.</li>
 * <li>Se apila de la más alta a la más baja y cada nivel mide lo que su caja
 *     más alta. El último nivel, aunque vaya a medias, cuenta entero.</li>
 * <li>Una caja sin medida deja el alto en {@code ?}: no se inventa.</li>
 * <li>Una medida escrita (el {@code palets[].medidas} del JSON, que es lo
 *     medido en el almacén) manda sobre la calculada.</li>
 * </ul>
 *
 * <p>El reparto real de las cajas dentro del palet no se sabe —ningún
 * documento lo dice y el almacén lo monta como le conviene—, así que esto es
 * el alto del apilado ordenado, no una foto del palet.
 */
public final class MedidaPalet {

    public static final int ANCHO_BASE_CM = 80;
    public static final int LARGO_BASE_CM = 120;

    /** Lo que se escribe en lugar del alto cuando no se puede calcular. */
    private static final String ALTO_DESCONOCIDO = "?";

    /** Margen de redondeo al sumar fracciones de nivel (1/4 + 1/4 + 1/2 = 1). */
    private static final double TOLERANCIA = 1e-9;

    private MedidaPalet() {
    }

    /**
     * La medida del palet: la escrita si la hay; si no, la calculada de sus
     * cajas ("80x120x91"), o "80x120x?" si alguna caja no trae medida.
     *
     * @param declaradas    la medida escrita del palet, o null
     * @param medidasCajas  una medida por caja FÍSICA del palet (la de su
     *                      línea líder), no una por línea
     * @param alturaPaletCm lo que mide el palet vacío
     */
    public static String de(String declaradas, List<String> medidasCajas, int alturaPaletCm) {
        if (declaradas != null && !declaradas.isBlank()) {
            return declaradas.trim();
        }
        OptionalInt carga = alturaCargaCm(medidasCajas);
        String alto = carga.isPresent()
                ? String.valueOf(carga.getAsInt() + alturaPaletCm)
                : ALTO_DESCONOCIDO;
        return ANCHO_BASE_CM + "x" + LARGO_BASE_CM + "x" + alto;
    }

    /** Lo mismo que {@link #de}, a partir de las cajas físicas del palet. */
    public static String deCajas(String declaradas, List<CajaFisica> cajas, int alturaPaletCm) {
        return de(declaradas, cajas.stream().map(caja -> caja.lider().getTamanoCaja()).toList(),
                alturaPaletCm);
    }

    /**
     * Si la medida trae sus tres lados en números, es decir, si se puede
     * sumar a un volumen. "80x120x?" no.
     */
    public static boolean tieneVolumen(String medidasPalet) {
        return MedidaCaja.parse(medidasPalet).isPresent();
    }

    /**
     * Cuántas cajas de esa base caben en un nivel del palet, probando las dos
     * orientaciones: 4 de 60x40, 2 de 70x60 o de 60x60. Nunca menos de una:
     * una caja que no cabe en la base sigue ocupando su nivel.
     */
    static int cajasPorNivel(MedidaCaja caja) {
        int derecha = (ANCHO_BASE_CM / caja.ancho()) * (LARGO_BASE_CM / caja.largo());
        int girada = (ANCHO_BASE_CM / caja.largo()) * (LARGO_BASE_CM / caja.ancho());
        return Math.max(1, Math.max(derecha, girada));
    }

    /** El alto de las cajas apiladas, sin el palet; vacío si alguna no se puede leer. */
    private static OptionalInt alturaCargaCm(List<String> medidasCajas) {
        List<MedidaCaja> cajas = new ArrayList<>();
        for (String medida : medidasCajas) {
            Optional<MedidaCaja> leida = MedidaCaja.parse(medida);
            if (leida.isEmpty()) {
                return OptionalInt.empty();
            }
            cajas.add(leida.get());
        }
        cajas.sort(Comparator.comparingInt(MedidaCaja::alto).reversed());

        int alto = 0;
        double ocupado = 0;
        for (MedidaCaja caja : cajas) {
            double fraccion = 1.0 / cajasPorNivel(caja);
            if (ocupado == 0 || ocupado + fraccion > 1 + TOLERANCIA) {
                // Nivel nuevo: lo abre la caja más alta que queda, así que su
                // alto es el del nivel entero.
                alto += caja.alto();
                ocupado = 0;
            }
            ocupado += fraccion;
        }
        return OptionalInt.of(alto);
    }
}
