package com.puntotres.packinglist.modelos.persistence;

import java.time.LocalDate;
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

    /**
     * Claves de {@code modelos.tipos-cambio} del yml, una o varias separadas por
     * comas ({@code CONSUMO_PIEL,FORNITURAS}; {@code ModelosProperties.clavesDe}
     * las separa), en el orden del yml; puede faltar en una orden.
     */
    @Column(name = "tipo_cambio", length = 200)
    private String tipoCambio;

    /**
     * El puesto en la cola de órdenes del taller ({@code ColaTaller}): menor va
     * antes. Nulo en los avisos del taller, que no entran en la cola. Una orden
     * que se cierra lo conserva, pero ya no cuenta: la cola son las abiertas.
     */
    @Column(name = "posicion_cola")
    private Integer posicionCola;

    /** La ha marcado urgente el despacho (solo en las órdenes para el taller). */
    @Column(name = "urgente", nullable = false)
    private boolean urgente;

    /**
     * Para qué día debería estar hecha (solo en las órdenes para el taller;
     * opcional). No caduca nada: es la etiqueta de fecha que el taller ve
     * cambiar de color según se acerca o se pasa el día.
     */
    @Column(name = "fecha_objetivo")
    private LocalDate fechaObjetivo;

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
    public Modificacion(Long modeloId, Direccion direccion, String tipoCambio, String titulo,
                        Long creadaPor, LocalDateTime creadaEn) {
        this.modeloId = modeloId;
        this.direccion = direccion;
        this.tipoCambio = tipoCambio;
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

    /** Pasa a otro modelo: al juntar dos, las modificaciones del que desaparece se van al que se queda. */
    public void moverA(Long modeloId) {
        this.modeloId = modeloId;
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

    public Integer getPosicionCola() {
        return posicionCola;
    }

    public boolean isUrgente() {
        return urgente;
    }

    public LocalDate getFechaObjetivo() {
        return fechaObjetivo;
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

    /** Las razones nuevas (claves separadas por comas), cuando el taller corrige su aviso. */
    public void cambiarTipoCambio(String tipoCambio) {
        this.tipoCambio = tipoCambio;
    }

    /**
     * Vuelve a NUEVA y sin ver: el taller ha cambiado lo que decía el aviso y
     * el despacho tiene que volver a mirarlo. Solo tiene sentido en una
     * NUEVA o VISTA (nadie ha aplicado nada todavía).
     */
    public void volverANueva() {
        estado = EstadoModificacion.NUEVA;
        vistaEn = null;
    }

    /** Suelta el título que puso la IA, para que la nota de voz nueva le ponga el suyo. */
    public void quitarTituloDeIa() {
        if (tituloDeIa) {
            titulo = null;
            tituloDeIa = false;
        }
    }

    public void ponerPosicionCola(Integer posicion) {
        this.posicionCola = posicion;
    }

    public void marcarUrgente(boolean urgente) {
        this.urgente = urgente;
    }

    /** La fecha objetivo, o null para quitarla. */
    public void ponerFechaObjetivo(LocalDate fecha) {
        this.fechaObjetivo = fecha;
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
