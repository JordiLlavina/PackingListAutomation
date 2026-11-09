package com.puntotres.packinglist.modelos.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un cambio de un modelo que va de un lado al otro: un aviso del taller al
 * despacho o una orden del despacho al taller ({@link Direccion}). Lo que se
 * dice va en sus {@link Mensaje}s (el primero es el cuerpo) y, en los avisos,
 * dónde se ha aplicado va en {@link ModificacionDestino}.
 *
 * Las reglas del ciclo (quién puede qué y en qué estado) son del servicio;
 * esto solo guarda. El modelo y los usuarios son columnas planas, como en el
 * resto del paquete: las claves ajenas viven en el SQL.
 */
@Entity
@Table(name = "modificacion")
public class Modificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "modelo_id", nullable = false)
    private Long modeloId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direccion", length = 20, nullable = false)
    private Direccion direccion;

    /** Clave de {@code modelos.tipos-cambio} del yml; puede faltar en una orden. */
    @Column(name = "tipo_cambio", length = 40)
    private String tipoCambio;

    /** Null en un aviso del taller hasta que el despacho la pone. */
    @Enumerated(EnumType.STRING)
    @Column(name = "prioridad", length = 20)
    private Prioridad prioridad;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoModificacion estado;

    /** El del despacho en sus órdenes; en los avisos, el que ponga la IA. */
    @Column(name = "titulo", length = 200)
    private String titulo;

    @Column(name = "titulo_de_ia", nullable = false)
    private boolean tituloDeIa;

    /** El último mensaje espera respuesta del otro lado (ver {@code Modificaciones.ladoQueDebeResponder}). */
    @Column(name = "necesita_respuesta", nullable = false)
    private boolean necesitaRespuesta;

    @Column(name = "creada_por")
    private Long creadaPor;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(name = "vista_en")
    private LocalDateTime vistaEn;

    @Column(name = "hecha_por")
    private Long hechaPor;

    @Column(name = "hecha_en")
    private LocalDateTime hechaEn;

    @Column(name = "motivo_descarte")
    private String motivoDescarte;

    protected Modificacion() {
        // Constructor para JPA.
    }

    /** Nace NUEVA, sin ver, sin hacer y sin esperar respuesta. */
    public Modificacion(Long modeloId, Direccion direccion, String tipoCambio, Prioridad prioridad, String titulo,
                        Long creadaPor, LocalDateTime creadaEn) {
        this.modeloId = modeloId;
        this.direccion = direccion;
        this.tipoCambio = tipoCambio;
        this.prioridad = prioridad;
        this.titulo = titulo;
        this.creadaPor = creadaPor;
        this.creadaEn = creadaEn;
        this.estado = EstadoModificacion.NUEVA;
    }

    public Long getId() {
        return id;
    }

    public Long getModeloId() {
        return modeloId;
    }

    public Direccion getDireccion() {
        return direccion;
    }

    /** Un aviso del taller al despacho: el único sentido con destinos. */
    public boolean esAviso() {
        return direccion == Direccion.TALLER_A_DESPACHO;
    }

    public String getTipoCambio() {
        return tipoCambio;
    }

    public Prioridad getPrioridad() {
        return prioridad;
    }

    public EstadoModificacion getEstado() {
        return estado;
    }

    public String getTitulo() {
        return titulo;
    }

    public boolean isTituloDeIa() {
        return tituloDeIa;
    }

    public boolean isNecesitaRespuesta() {
        return necesitaRespuesta;
    }

    public Long getCreadaPor() {
        return creadaPor;
    }

    public LocalDateTime getCreadaEn() {
        return creadaEn;
    }

    public LocalDateTime getVistaEn() {
        return vistaEn;
    }

    public Long getHechaPor() {
        return hechaPor;
    }

    public LocalDateTime getHechaEn() {
        return hechaEn;
    }

    public String getMotivoDescarte() {
        return motivoDescarte;
    }

    /** Apunta cuándo la abrió quien la recibe, si no estaba apuntado, y la pasa a VISTA si era NUEVA. */
    public void marcarVista(LocalDateTime cuando) {
        if (vistaEn == null) {
            vistaEn = cuando;
        }
        if (estado == EstadoModificacion.NUEVA) {
            estado = EstadoModificacion.VISTA;
        }
    }

    public void ponerPrioridad(Prioridad prioridad) {
        this.prioridad = prioridad;
    }

    /** EN_CURSO, olvidando quién la dio por hecha si lo estaba. */
    public void pasarAEnCurso() {
        estado = EstadoModificacion.EN_CURSO;
        hechaPor = null;
        hechaEn = null;
    }

    public void marcarHecha(Long usuarioId, LocalDateTime cuando) {
        estado = EstadoModificacion.HECHA;
        hechaPor = usuarioId;
        hechaEn = cuando;
    }

    /** DESCARTADA con su motivo; todo lo demás se queda como estaba. */
    public void descartar(String motivo) {
        estado = EstadoModificacion.DESCARTADA;
        motivoDescarte = motivo;
    }

    /** El último mensaje espera respuesta del otro lado. */
    public void pedirRespuesta() {
        necesitaRespuesta = true;
    }

    /** El título que ha sacado la IA de la nota de voz; se marca como suyo para que se vea y se pueda corregir. */
    public void ponerTituloDeIa(String titulo) {
        this.titulo = titulo;
        this.tituloDeIa = true;
    }

    /** El título que pone (o corrige) una persona: deja de ser de la IA. */
    public void cambiarTitulo(String titulo) {
        this.titulo = titulo;
        this.tituloDeIa = false;
    }
}
