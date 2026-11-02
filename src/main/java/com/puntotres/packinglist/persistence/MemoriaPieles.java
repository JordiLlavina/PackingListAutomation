package com.puntotres.packinglist.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.service.catalogo.CatalogoModelos;

/**
 * Lo que el programa recuerda de las pieles para los documentos del corte,
 * para no teclearlo cada temporada. Es una fachada sobre el catálogo de
 * modelos: la misma interfaz de siempre para el corte y la pantalla de
 * colores, y los datos en {@link Piel}, {@link Articulo} y
 * {@link ArticuloColor}, donde la pantalla del catálogo los enseña y edita.
 *
 * Tres claves a propósito: el NOMBRE de la piel es del material y va en la
 * piel (cliente + referencia de piel); el FORRO y las COMBINACIONES son del
 * bolso y van en el artículo (cliente + referencia entera) —guardadas solo
 * por piel, las combinaciones de ULL027.AL0103 aparecerían en ULL712.AL0103,
 * que no las lleva—; y los COLORES de las pieles van por artículo + código
 * de color del bolso, porque son de ese bolso en ese color.
 *
 * Solo se guarda lo que alguien ha dado por bueno: una fila sin nombre de
 * piel no ha rellenado nada y no toca la memoria (ni el nombre ni el forro ni
 * las combinaciones ni los colores). Con nombre, el resto se guarda tal cual,
 * vacío incluido: quitar una combinación es decir que ya no la lleva. Los
 * textos se guardan en mayúsculas y sin espacios de sobra, para que "badana"
 * y "BADANA" sean lo mismo.
 */
@Service
public class MemoriaPieles {

    public static final int MAXIMO_COMBINACIONES = CatalogoModelos.MAXIMO_COMBINACIONES;

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

    private final PielRepository pieles;
    private final ArticuloColorRepository colores;
    private final CatalogoModelos catalogo;

    public MemoriaPieles(PielRepository pieles, ArticuloColorRepository colores, CatalogoModelos catalogo) {
        this.pieles = pieles;
        this.colores = colores;
        this.catalogo = catalogo;
    }

    public Recordadas buscar(String cliente, String refPiel, String referencia) {
        if (vacio(cliente)) {
            return new Recordadas("", "", List.of());
        }
        String nombre = vacio(refPiel) ? "" : pieles.findById(new PielId(clave(cliente), clave(refPiel)))
                .map(Piel::getNombre)
                .orElse("");
        if (vacio(referencia)) {
            return new Recordadas(nombre, "", List.of());
        }
        return catalogo.articulo(cliente, referencia)
                .map(articulo -> new Recordadas(nombre, articulo.getForro(), articulo.getCombinaciones()))
                .orElse(new Recordadas(nombre, "", List.of()));
    }

    @Transactional
    public void recordar(String cliente, String refPiel, String referencia,
                         String nombrePiel, String forro, List<String> combinaciones) {
        if (vacio(cliente) || vacio(referencia) || vacio(nombrePiel)) {
            return;
        }
        if (!vacio(refPiel)) {
            escribirNombre(clave(cliente), clave(refPiel), texto(nombrePiel));
        }
        recordarPielesDelArticulo(cliente, referencia, forro, combinaciones);
    }

    /**
     * Escribe el forro y las combinaciones del artículo (creándolo si falta)
     * SIN tocar el nombre de la piel. Es lo que usa el camino del excel de
     * producción, donde la piel es texto concatenado ("SERRAJE ALABAMA") que
     * no debe pisar el nombre que alguien tecleó en su tabla.
     */
    @Transactional
    public void recordarPielesDelArticulo(String cliente, String referencia, String forro,
                                          List<String> combinaciones) {
        if (vacio(cliente) || vacio(referencia)) {
            return;
        }
        List<String> rellenas = combinaciones == null ? List.of() : combinaciones.stream()
                .filter(Objects::nonNull)
                .map(MemoriaPieles::texto)
                .filter(combinacion -> !combinacion.isEmpty())
                .limit(MAXIMO_COMBINACIONES)
                .toList();
        Articulo articulo = catalogo.asegurarArticulo(cliente, referencia);
        articulo.setForro(texto(forro));
        articulo.setCombinaciones(rellenas);
    }

    public ColoresRecordados buscarColores(String cliente, String referencia, String codigoColor) {
        if (vacio(cliente) || vacio(referencia) || vacio(codigoColor)) {
            return ColoresRecordados.NINGUNO;
        }
        return colores.findById(new ArticuloColorId(clave(cliente), clave(referencia), clave(codigoColor)))
                .map(fila -> new ColoresRecordados(fila.getColorPiel(), fila.getColorForro(),
                        fila.getColoresCombinacion()))
                .orElse(ColoresRecordados.NINGUNO);
    }

    /**
     * Guarda los colores de piel de un color del bolso, con la misma regla
     * que los nombres: sin nombre de piel la fila no se ha rellenado y no
     * toca nada. Los de las combinaciones llegan uno por combinación CON
     * nombre y en su orden, que es como se guardan los nombres. El código de
     * color es el del bolso o, si no tiene, su descripción entera: así lo
     * agrupa el corte.
     */
    @Transactional
    public void recordarColores(String cliente, String referencia, String codigoColor, String nombrePiel,
                                String colorPiel, String colorForro, List<String> coloresCombinacion) {
        if (vacio(cliente) || vacio(referencia) || vacio(codigoColor) || vacio(nombrePiel)) {
            return;
        }
        List<String> deCombinaciones = coloresCombinacion == null ? List.of() : coloresCombinacion.stream()
                .map(MemoriaPieles::texto)
                .limit(MAXIMO_COMBINACIONES)
                .toList();
        String elDeLaPiel = texto(colorPiel);
        String elDelForro = texto(colorForro);
        Articulo articulo = catalogo.asegurarArticulo(cliente, referencia);
        ArticuloColorId id = new ArticuloColorId(articulo.getCliente(), articulo.getReferencia(), clave(codigoColor));
        colores.findById(id).ifPresentOrElse(
                fila -> fila.actualizar(elDeLaPiel, elDelForro, deCombinaciones),
                () -> colores.save(new ArticuloColor(id.cliente(), id.referencia(), id.codigoColor(),
                        elDeLaPiel, elDelForro, deCombinaciones)));
    }

    // --- La tabla de pieles de la pantalla "Colores y Pieles de Cliente" ---
    // Es la misma tabla de pieles del catálogo que lee la tabla de los
    // Documentos del Corte: lo que se guarda aquí sale allí solo, sin otra copia.

    /** Las pieles de un cliente, por referencia de piel. */
    public List<Piel> pielesDe(String cliente) {
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
        escribirNombre(clave(cliente), clave(refPiel), texto(nombre));
    }

    private void escribirNombre(String cliente, String refPiel, String nombre) {
        PielId id = new PielId(cliente, refPiel);
        pieles.findById(id).ifPresentOrElse(
                fila -> fila.actualizar(nombre),
                () -> pieles.save(new Piel(id.cliente(), id.refPiel(), nombre)));
    }

    /** Devuelve si había algo que borrar. Los artículos de esa piel se quedan, sin piel (FK del esquema). */
    @Transactional
    public boolean borrarPiel(String cliente, String refPiel) {
        if (vacio(cliente) || vacio(refPiel)) {
            return false;
        }
        PielId id = new PielId(clave(cliente), clave(refPiel));
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
            PielId id = new PielId(elCliente, laRef);
            if (pieles.existsById(id)) {
                yaEstaban.add(laRef);
            } else {
                pieles.save(new Piel(elCliente, laRef, ""));
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

    /** Un texto libre (nombre, forro, color): sin espacios de sobra y en mayúsculas; vacío si no hay nada. */
    private static String texto(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }
}
