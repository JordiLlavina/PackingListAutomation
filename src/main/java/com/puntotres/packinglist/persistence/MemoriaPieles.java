package com.puntotres.packinglist.persistence;

import java.util.ArrayList;
import java.util.Collection;
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
 * Y una tercera para los COLORES de las pieles, por cliente + referencia +
 * código de color del bolso: son de ese bolso en ese color.
 *
 * Solo se guarda lo que alguien ha dado por bueno: una fila sin nombre de
 * piel no ha rellenado nada y no toca la memoria (ni el nombre ni el forro ni
 * las combinaciones ni los colores). Con nombre, el resto se guarda tal cual,
 * vacío incluido: quitar una combinación es decir que ya no la lleva.
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

    /**
     * Los colores de piel recordados de un color del bolso; vacíos cuando no
     * se sabe nada. Los de las combinaciones van en el orden de
     * {@link Recordadas#combinaciones()}.
     */
    public record ColoresRecordados(String colorPiel, String colorForro, List<String> coloresCombinacion) {

        public static final ColoresRecordados NINGUNO = new ColoresRecordados("", "", List.of());

        public ColoresRecordados {
            coloresCombinacion = List.copyOf(coloresCombinacion);
        }
    }

    private final MemoriaPielRepository pieles;
    private final MemoriaPielesArticuloRepository articulos;
    private final MemoriaColoresPielRepository colores;

    public MemoriaPieles(MemoriaPielRepository pieles, MemoriaPielesArticuloRepository articulos,
                         MemoriaColoresPielRepository colores) {
        this.pieles = pieles;
        this.articulos = articulos;
        this.colores = colores;
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

    public ColoresRecordados buscarColores(String cliente, String referencia, String codigoColor) {
        if (vacio(cliente) || vacio(referencia) || vacio(codigoColor)) {
            return ColoresRecordados.NINGUNO;
        }
        return colores.findById(new MemoriaColoresPielId(clave(cliente), clave(referencia), clave(codigoColor)))
                .map(fila -> new ColoresRecordados(fila.getColorPiel(), fila.getColorForro(),
                        fila.getColoresCombinacion()))
                .orElse(ColoresRecordados.NINGUNO);
    }

    /**
     * Guarda los colores de piel de un color del bolso, con la misma regla
     * que los nombres: sin nombre de piel la fila no se ha rellenado y no
     * toca nada. Los de las combinaciones llegan uno por combinación CON
     * nombre y en su orden, que es como se guardan los nombres.
     */
    @Transactional
    public void recordarColores(String cliente, String referencia, String codigoColor, String nombrePiel,
                                String colorPiel, String colorForro, List<String> coloresCombinacion) {
        if (vacio(cliente) || vacio(referencia) || vacio(codigoColor) || vacio(nombrePiel)) {
            return;
        }
        List<String> deCombinaciones = coloresCombinacion == null ? List.of() : coloresCombinacion.stream()
                .map(color -> color == null ? "" : color.trim())
                .limit(MAXIMO_COMBINACIONES)
                .toList();
        String elDeLaPiel = colorPiel == null ? "" : colorPiel.trim();
        String elDelForro = colorForro == null ? "" : colorForro.trim();
        MemoriaColoresPielId id = new MemoriaColoresPielId(clave(cliente), clave(referencia), clave(codigoColor));
        colores.findById(id).ifPresentOrElse(
                fila -> fila.actualizar(elDeLaPiel, elDelForro, deCombinaciones),
                () -> colores.save(new MemoriaColoresPiel(id.cliente(), id.referencia(), id.codigoColor(),
                        elDeLaPiel, elDelForro, deCombinaciones)));
    }

    // --- La tabla de pieles de la pantalla "Colores y Pieles de Cliente" ---
    // Es la misma memoria de nombres de piel que lee la tabla de los Documentos
    // del Corte: lo que se guarda aquí sale allí solo, sin otra copia.

    /** Las pieles de un cliente, por referencia de piel. */
    public List<MemoriaPiel> pielesDe(String cliente) {
        return vacio(cliente) ? List.of() : pieles.findByClienteOrderByRefPiel(clave(cliente));
    }

    /**
     * Alta o corrección del nombre de una piel desde su pantalla. Al revés que
     * {@link #recordar}, aquí un nombre vacío SÍ se guarda: es la tabla donde
     * se mantienen, y vaciar una casilla a mano es una corrección.
     */
    @Transactional
    public void guardarPiel(String cliente, String refPiel, String nombre) {
        if (vacio(cliente) || vacio(refPiel)) {
            throw new IllegalArgumentException("La piel necesita cliente y referencia");
        }
        MemoriaPielId id = new MemoriaPielId(clave(cliente), clave(refPiel));
        String elNombre = nombre == null ? "" : nombre.trim();
        pieles.findById(id).ifPresentOrElse(
                fila -> fila.actualizar(elNombre),
                () -> pieles.save(new MemoriaPiel(id.cliente(), id.refPiel(), elNombre)));
    }

    /** Devuelve si había algo que borrar. */
    @Transactional
    public boolean borrarPiel(String cliente, String refPiel) {
        if (vacio(cliente) || vacio(refPiel)) {
            return false;
        }
        MemoriaPielId id = new MemoriaPielId(clave(cliente), clave(refPiel));
        if (pieles.existsById(id)) {
            pieles.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Mete las referencias de piel de un excel de pedido. El pedido no trae el
     * nombre de la piel, así que las nuevas entran vacías, listas para
     * rellenar; las que ya estaban no se tocan.
     */
    @Transactional
    public Incorporacion incorporarPieles(String cliente, Collection<String> refsPiel) {
        List<String> anadidas = new ArrayList<>();
        List<String> yaEstaban = new ArrayList<>();
        if (vacio(cliente)) {
            return new Incorporacion(anadidas, List.of(), yaEstaban);
        }
        String elCliente = clave(cliente);
        for (String refPiel : refsPiel) {
            if (vacio(refPiel)) {
                continue;
            }
            String laRef = clave(refPiel);
            if (anadidas.contains(laRef) || yaEstaban.contains(laRef)) {
                continue;
            }
            MemoriaPielId id = new MemoriaPielId(elCliente, laRef);
            if (pieles.existsById(id)) {
                yaEstaban.add(laRef);
            } else {
                pieles.save(new MemoriaPiel(elCliente, laRef, ""));
                anadidas.add(laRef);
            }
        }
        return new Incorporacion(anadidas, List.of(), yaEstaban);
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    /** Las claves se teclean o se leen de un excel: "al0103 " y "AL0103" son la misma. */
    private static String clave(String texto) {
        return texto.trim().toUpperCase(Locale.ROOT);
    }
}
