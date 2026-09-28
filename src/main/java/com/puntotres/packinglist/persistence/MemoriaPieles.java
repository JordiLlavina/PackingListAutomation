package com.puntotres.packinglist.persistence;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que el programa recuerda de las pieles para los documentos del corte,
 * para no teclearlo cada temporada.
 *
 * Dos claves a propósito: el NOMBRE de la piel es del material y se guarda
 * por cliente + referencia de piel; el FORRO y las COMBINACIONES son del
 * bolso y se guardan por cliente + referencia (modelo + piel). Guardadas solo
 * por piel, las combinaciones de ULL027.AL0103 aparecerían en ULL712.AL0103,
 * que no las lleva.
 *
 * Solo se guarda lo que alguien ha dado por bueno: una fila sin nombre de
 * piel no ha rellenado nada y no toca la memoria (ni el nombre ni el forro ni
 * las combinaciones). Con nombre, el forro y las combinaciones se guardan tal
 * cual, vacío incluido: quitar una combinación es decir que ya no la lleva.
 */
@Service
public class MemoriaPieles {

    public static final int MAXIMO_COMBINACIONES = 4;

    /** Lo recordado de una fila; cadenas vacías y lista vacía cuando no se sabe nada. */
    public record Recordadas(String nombrePiel, String forro, List<String> combinaciones) {

        public Recordadas {
            combinaciones = List.copyOf(combinaciones);
        }
    }

    private final MemoriaPielRepository pieles;
    private final MemoriaPielesArticuloRepository articulos;

    public MemoriaPieles(MemoriaPielRepository pieles, MemoriaPielesArticuloRepository articulos) {
        this.pieles = pieles;
        this.articulos = articulos;
    }

    public Recordadas buscar(String cliente, String refPiel, String referencia) {
        if (vacio(cliente)) {
            return new Recordadas("", "", List.of());
        }
        String nombre = vacio(refPiel) ? "" : pieles.findById(
                        new MemoriaPielId(clave(cliente), clave(refPiel)))
                .map(MemoriaPiel::getNombre)
                .orElse("");
        if (vacio(referencia)) {
            return new Recordadas(nombre, "", List.of());
        }
        return articulos.findById(new MemoriaPielesArticuloId(clave(cliente), clave(referencia)))
                .map(fila -> new Recordadas(nombre, fila.getForro(), fila.getCombinaciones()))
                .orElse(new Recordadas(nombre, "", List.of()));
    }

    @Transactional
    public void recordar(String cliente, String refPiel, String referencia,
                         String nombrePiel, String forro, List<String> combinaciones) {
        if (vacio(cliente) || vacio(referencia) || vacio(nombrePiel)) {
            return;
        }
        String elCliente = clave(cliente);
        if (!vacio(refPiel)) {
            MemoriaPielId id = new MemoriaPielId(elCliente, clave(refPiel));
            String nombre = nombrePiel.trim();
            pieles.findById(id).ifPresentOrElse(
                    fila -> fila.actualizar(nombre),
                    () -> pieles.save(new MemoriaPiel(id.cliente(), id.refPiel(), nombre)));
        }
        List<String> rellenas = combinaciones == null ? List.of() : combinaciones.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(combinacion -> !combinacion.isEmpty())
                .limit(MAXIMO_COMBINACIONES)
                .toList();
        String elForro = forro == null ? "" : forro.trim();
        MemoriaPielesArticuloId id = new MemoriaPielesArticuloId(elCliente, clave(referencia));
        articulos.findById(id).ifPresentOrElse(
                fila -> fila.actualizar(elForro, rellenas),
                () -> articulos.save(new MemoriaPielesArticulo(
                        id.cliente(), id.referencia(), elForro, rellenas)));
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    /** Las claves se teclean o se leen de un excel: "al0103 " y "AL0103" son la misma. */
    private static String clave(String texto) {
        return texto.trim().toUpperCase(Locale.ROOT);
    }
}
