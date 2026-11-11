package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un modelo de un cliente: la forma del bolso ("ULL027", "F67008"), sin la
 * piel. Es el nivel al que pertenecen las fotos y el nombre del bolso; lo que
 * depende de la piel (cartón, unidades, forro, peso) va en {@link Articulo}.
 * Un modelo va en varias pieles —ULL754 en cuatro— y guardar aquí lo del
 * artículo mezclaría las de una con las de otra.
 *
 * La clave es un id interno y no el código del cliente: un PROTOTIPO todavía
 * no tiene código (se conoce por su nombre de prototipo, "PROTO A") y el
 * código puede llegar o cambiar después. Por eso {@link #getCodigo()} puede
 * ser null, y lo que se enseña es {@link #nombreVisible()}. Artículos, fotos
 * y temporadas cuelgan del id (las FK viven en el SQL, como en el resto del
 * paquete: aquí son columnas planas).
 */
@Entity
@Table(name = "modelo")
public class Modelo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "cliente", length = 60, nullable = false)
    private String cliente;

    /** El código del cliente; null mientras es un prototipo sin código. */
    @Column(name = "codigo", length = 80)
    private String codigo;

    /** El nombre con el que se conoce el modelo antes de tener código. */
    @Column(name = "nombre_prototipo", length = 200)
    private String nombrePrototipo;

    /** El nombre del bolso ("LE NEIGE", "AMI CANDY POUCH BAG"), si alguien lo ha dicho. */
    @Column(name = "nombre", length = 200)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20, nullable = false)
    private TipoArticulo tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa", length = 20, nullable = false)
    private Etapa etapa;

    /** Desde cuándo está en su etapa. */
    @Column(name = "fecha_etapa", nullable = false)
    private LocalDateTime fechaEtapa;

    /** La temporada en que nació el prototipo; null en los modelos que llegaron por un pedido. */
    @Column(name = "temporada_alta_id")
    private Long temporadaAltaId;

    @Column(name = "etiqueta_impresa_en")
    private LocalDateTime etiquetaImpresaEn;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected Modelo() {
        // Constructor para JPA.
    }

    /** Un modelo con código, como los que da de alta un pedido: nace en PRODUCCION. */
    public Modelo(String cliente, String codigo, String nombre, TipoArticulo tipo) {
        this(cliente, codigo, null, nombre, tipo, Etapa.PRODUCCION, null);
    }

    /**
     * Un modelo en cualquier etapa. Un prototipo puede no tener código, pero
     * entonces necesita su nombre de prototipo: sin ninguno de los dos no
     * habría forma de nombrarlo (la base de datos tampoco lo admite).
     */
    public Modelo(String cliente, String codigo, String nombrePrototipo, String nombre, TipoArticulo tipo,
                  Etapa etapa, Long temporadaAltaId) {
        this.cliente = cliente;
        this.codigo = vacioANulo(codigo);
        this.nombrePrototipo = vacioANulo(nombrePrototipo);
        if (this.codigo == null && this.nombrePrototipo == null) {
            throw new IllegalArgumentException("Un modelo necesita su código o, si no lo tiene, su nombre de prototipo");
        }
        this.nombre = nombre;
        this.tipo = tipo;
        this.etapa = Objects.requireNonNull(etapa);
        this.temporadaAltaId = temporadaAltaId;
        LocalDateTime ahora = LocalDateTime.now();
        this.fechaEtapa = ahora;
        this.fechaActualizacion = ahora;
    }

    public Long getId() {
        return id;
    }

    public String getCliente() {
        return cliente;
    }

    /** El código del cliente, o null si es un prototipo que todavía no lo tiene. */
    public String getCodigo() {
        return codigo;
    }

    public String getNombrePrototipo() {
        return nombrePrototipo == null ? "" : nombrePrototipo;
    }

    /** Cómo se nombra el modelo en pantalla: su código o, si no lo tiene, su nombre de prototipo. */
    public String nombreVisible() {
        return codigo != null ? codigo : getNombrePrototipo();
    }

    public String getNombre() {
        return nombre == null ? "" : nombre;
    }

    public TipoArticulo getTipo() {
        return tipo;
    }

    public Etapa getEtapa() {
        return etapa;
    }

    public LocalDateTime getFechaEtapa() {
        return fechaEtapa;
    }

    public Long getTemporadaAltaId() {
        return temporadaAltaId;
    }

    public LocalDateTime getEtiquetaImpresaEn() {
        return etiquetaImpresaEn;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
        tocar();
    }

    public void setTipo(TipoArticulo tipo) {
        this.tipo = tipo;
        tocar();
    }

    /** Pasa a otra etapa; la fecha de etapa solo se mueve si de verdad cambia. */
    public void cambiarEtapa(Etapa nueva) {
        Objects.requireNonNull(nueva);
        if (nueva == etapa) {
            return;
        }
        this.etapa = nueva;
        this.fechaEtapa = LocalDateTime.now();
        tocar();
    }

    /** El código que el cliente ha dado al modelo (un prototipo que lo recibe, o uno que cambia). */
    public void asignarCodigo(String codigo) {
        String limpio = vacioANulo(codigo);
        if (limpio == null) {
            throw new IllegalArgumentException("El código del modelo no puede quedarse vacío");
        }
        this.codigo = limpio;
        tocar();
    }

    /**
     * El nombre de prototipo de otro modelo que se ha juntado con este (el
     * importado que sobrevive a su prototipo), con la temporada en que nació
     * si este no tenía. Un nombre en blanco no cambia nada.
     */
    public void recibirPrototipo(String nombrePrototipo, Long temporadaAltaId) {
        String limpio = vacioANulo(nombrePrototipo);
        if (limpio == null) {
            return;
        }
        this.nombrePrototipo = limpio;
        if (this.temporadaAltaId == null) {
            this.temporadaAltaId = temporadaAltaId;
        }
        tocar();
    }

    public void marcarEtiquetaImpresa(LocalDateTime cuando) {
        this.etiquetaImpresaEn = cuando;
        tocar();
    }

    private void tocar() {
        this.fechaActualizacion = LocalDateTime.now();
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }
}
