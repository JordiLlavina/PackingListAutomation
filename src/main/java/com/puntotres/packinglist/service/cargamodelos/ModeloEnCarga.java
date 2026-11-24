package com.puntotres.packinglist.service.cargamodelos;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.puntotres.packinglist.persistence.Etapa;
import com.puntotres.packinglist.persistence.TipoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.corte.ConversionFotos;
import com.puntotres.packinglist.service.corte.FotoModelo;

/**
 * Un modelo de la batería, mientras se revisa: lo que se ha podido leer del
 * origen, lo que ya dice el catálogo si el modelo existe, y lo que el usuario
 * va tecleando. No toca la base de datos: eso lo hace {@link CargadorModelos}
 * con los que se acepten.
 *
 * {@code tocados} son los campos que una persona ha cambiado en el panel. En
 * un modelo que ya existe es lo que decide si un valor PISA lo guardado (lo
 * tocado) o solo rellena un hueco (lo que venía del origen).
 */
public class ModeloEnCarga {

    /** Qué ha decidido el usuario de este modelo. Solo los aceptados se cargan. */
    public enum EstadoModelo { PENDIENTE, ACEPTADO, CANCELADO }

    final int id;
    String cliente;
    /** El id del modelo en el catálogo si ya existe; null si es nuevo. */
    Long modeloId;
    String codigo = "";
    String nombrePrototipo = "";
    Long temporadaId;
    String nombre = "";
    TipoArticulo tipo = TipoArticulo.BOLSO;
    Etapa etapa = Etapa.PROTOTIPO;
    final List<PielEnCarga> pieles = new ArrayList<>();
    /** En el orden de la tira: la primera seleccionada será la principal. */
    final List<FotoEnCarga> fotos = new ArrayList<>();
    final List<FichaEnCarga> fichas = new ArrayList<>();
    EstadoModelo estado = EstadoModelo.PENDIENTE;
    final Set<String> tocados = new HashSet<>();
    boolean ordenTocado;
    /** El modelo ya existe y ya está vinculado a la temporada de la carga (o no hay temporada). */
    boolean yaVinculado;
    /**
     * La referencia y el cliente se pueden corregir aunque el modelo case con uno que ya existe: es un
     * modelo escrito a mano («Añadir modelo»), y una errata no puede dejarlo atado al modelo equivocado.
     */
    boolean identidadLibre;
    /** Campos que en el catálogo estaban vacíos y el origen trae: cuentan como novedad. */
    int huecosRellenados;

    ModeloEnCarga(int id, String cliente) {
        this.id = id;
        this.cliente = cliente;
    }

    public int id() {
        return id;
    }

    public boolean existe() {
        return modeloId != null;
    }

    public EstadoModelo estado() {
        return estado;
    }

    /** Cómo se enseña: la referencia o, sin ella, la de prototipo. */
    public String nombreVisible() {
        if (!codigo.isEmpty()) {
            return codigo;
        }
        return nombrePrototipo.isEmpty() ? "Modelo nuevo" : nombrePrototipo;
    }

    boolean tocado(String campo) {
        return tocados.contains(campo);
    }

    Optional<PielEnCarga> piel(String referencia) {
        return pieles.stream().filter(piel -> piel.referencia.equals(referencia)).findFirst();
    }

    Optional<FotoEnCarga> foto(String fotoId) {
        return fotos.stream().filter(foto -> foto.id.equals(fotoId)).findFirst();
    }

    Optional<FichaEnCarga> ficha(String fichaId) {
        return fichas.stream().filter(ficha -> ficha.id.equals(fichaId)).findFirst();
    }

    /** Sin ninguna foto seleccionada: el iconito de foto tachada de la lista. */
    boolean sinFotos() {
        return fotos.stream().noneMatch(foto -> foto.seleccionada && !foto.ilegible());
    }

    long fotosNuevas() {
        return fotos.stream().filter(foto -> foto.origen != FotoEnCarga.Origen.GUARDADA && foto.seleccionada
                && !foto.ilegible()).count();
    }

    /** Una piel del modelo: un artículo del catálogo (la referencia entera). */
    static class PielEnCarga {

        final String referencia;
        final String refPiel;
        String nombrePiel = "";
        String forro = "";
        /** Siempre {@link CatalogoModelos#MAXIMO_COMBINACIONES} casillas, sin huecos delante. */
        final List<String> combinaciones = casillas();
        String medidaCaja = "";
        String unidadesPorCaja = "";
        String pesoNetoKg = "";
        final List<ColorEnCarga> colores = new ArrayList<>();
        /** El artículo ya está en el catálogo. */
        boolean existe;

        PielEnCarga(String referencia, String refPiel) {
            this.referencia = referencia;
            this.refPiel = refPiel == null ? "" : refPiel;
        }

        Optional<ColorEnCarga> color(String clave) {
            return colores.stream().filter(color -> color.clave.equals(clave)).findFirst();
        }

        String claveCampo(String campo) {
            return "p|" + referencia + "|" + campo;
        }
    }

    /** Un color del bolso en una piel: su fila de {@code articulo_color}. */
    static class ColorEnCarga {

        /** El código o, si no tiene, la descripción en mayúsculas: como lo identifica el catálogo. */
        final String clave;
        final String codigo;
        String descripcion = "";
        String colorPiel = "";
        String colorForro = "";
        final List<String> coloresCombinacion = casillas();
        boolean existe;

        ColorEnCarga(String codigo, String descripcion) {
            this.codigo = mayusculas(codigo);
            this.descripcion = limpio(descripcion);
            this.clave = this.codigo.isEmpty() ? mayusculas(descripcion) : this.codigo;
        }

        String claveCampo(PielEnCarga piel, String campo) {
            return "p|" + piel.referencia + "|c|" + clave + "|" + campo;
        }
    }

    /** Una foto de la tira del modelo. */
    static class FotoEnCarga {

        /** De dónde sale: subida como foto, elegida de una ficha técnica, croquis del excel, o ya guardada. */
        enum Origen { SUBIDA, FICHA, CROQUIS, GUARDADA }

        final String id;
        final String nombre;
        final Origen origen;
        /** Solo las GUARDADA: su id en el catálogo. */
        final Long fotoCatalogoId;
        private final FotoModelo original;
        private final ConversionFotos conversion;
        private final Path jpegDirecto;
        boolean seleccionada;
        /** Solo las FICHA: de qué ficha técnica sale y cuál de sus imágenes es. */
        String fichaId;
        String claveFicha;

        private FotoEnCarga(String id, String nombre, Origen origen, Long fotoCatalogoId, FotoModelo original,
                            ConversionFotos conversion, Path jpegDirecto, boolean seleccionada) {
            this.id = id;
            this.nombre = nombre;
            this.origen = origen;
            this.fotoCatalogoId = fotoCatalogoId;
            this.original = original;
            this.conversion = conversion;
            this.jpegDirecto = jpegDirecto;
            this.seleccionada = seleccionada;
        }

        /** Una foto subida, que se está convirtiendo (o ya lo está) en esa conversión. Sale seleccionada. */
        static FotoEnCarga subida(String id, FotoModelo original, ConversionFotos conversion) {
            return new FotoEnCarga(id, original.nombreOriginal(), Origen.SUBIDA, null, original, conversion, null,
                    true);
        }

        /** Una imagen ya en JPEG reducido: elegida de una ficha técnica o croquis del excel. */
        static FotoEnCarga directa(String id, String nombre, Origen origen, Path jpeg, boolean seleccionada) {
            return new FotoEnCarga(id, nombre, origen, null, null, null, jpeg, seleccionada);
        }

        /** Una foto que el modelo ya tiene en el catálogo: sale en la tira para ordenarla, y no se quita. */
        static FotoEnCarga guardada(String id, String nombre, Long fotoCatalogoId) {
            return new FotoEnCarga(id, nombre, Origen.GUARDADA, fotoCatalogoId, null, null, null, true);
        }

        /** El JPEG reducido, si ya está; las guardadas no tienen fichero aquí. */
        Optional<Path> jpeg() {
            if (jpegDirecto != null) {
                return Optional.of(jpegDirecto);
            }
            return conversion == null ? Optional.empty() : conversion.reducida(original);
        }

        boolean lista() {
            return origen == Origen.GUARDADA || jpeg().isPresent();
        }

        /** La conversión ha acabado y esta foto no ha salido: no se podrá cargar. */
        boolean ilegible() {
            return conversion != null && conversion.terminada() && conversion.reducida(original).isEmpty();
        }
    }

    /** Una ficha técnica (PDF) del modelo, en el directorio del lote. */
    static class FichaEnCarga {

        final String id;
        final String nombre;
        final Path pdf;

        FichaEnCarga(String id, String nombre, Path pdf) {
            this.id = id;
            this.nombre = nombre;
            this.pdf = pdf;
        }
    }

    static List<String> casillas() {
        List<String> casillas = new ArrayList<>();
        for (int i = 0; i < CatalogoModelos.MAXIMO_COMBINACIONES; i++) {
            casillas.add("");
        }
        return casillas;
    }

    static String limpio(String texto) {
        return texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
    }

    static String mayusculas(String texto) {
        return limpio(texto).toUpperCase(Locale.ROOT);
    }
}
