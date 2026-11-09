package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Una foto de un modelo, ya reducida (JPEG de 1.600 px de lado como mucho,
 * como las del corte), guardada dentro de la base de datos y no como ruta al
 * disco: así ./datos viaja entero de un ordenador a otro, igual que el excel
 * de las temporadas. Tabla aparte de {@link Modelo} para que listar modelos
 * no cargue los bytes; para listar está {@link ResumenFoto}.
 *
 * La foto es del MODELO, no de la piel: la carpeta "ULL027" vale para
 * ULL027.AL0103 y ULL027.AL0216. Como mucho una por modelo es la principal;
 * lo garantiza el servicio y no la base de datos.
 */
@Entity
@Table(name = "foto_modelo")
public class FotoCatalogo {

    /** Lo que cabe en {@code foto_modelo.nombre_fichero}. */
    private static final int LARGO_NOMBRE = 255;

    /** Hasta dónde un punto final se lee como extensión ({@code .jpeg} sí; {@code .algo muy largo} no). */
    private static final int LARGO_EXTENSION = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "modelo_id", nullable = false)
    private Long modeloId;

    @Column(name = "nombre_fichero", length = 255, nullable = false)
    private String nombreFichero;

    @Column(name = "principal", nullable = false)
    private boolean principal;

    @Column(name = "orden", nullable = false)
    private int orden;

    @Lob
    @Column(name = "bytes", nullable = false)
    private byte[] jpeg;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected FotoCatalogo() {
        // Constructor para JPA.
    }

    public FotoCatalogo(Long modeloId, String nombreFichero, int orden, byte[] jpeg) {
        this.modeloId = modeloId;
        this.nombreFichero = nombreFichero;
        this.orden = orden;
        this.jpeg = jpeg;
        this.principal = false;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getModeloId() {
        return modeloId;
    }

    public String getNombreFichero() {
        return nombreFichero;
    }

    public boolean isPrincipal() {
        return principal;
    }

    public int getOrden() {
        return orden;
    }

    public byte[] getJpeg() {
        return jpeg;
    }

    public void setPrincipal(boolean principal) {
        this.principal = principal;
        this.fechaActualizacion = LocalDateTime.now();
    }

    /**
     * Pasa a otro modelo al juntar dos: con el nombre libre que le toca allí,
     * detrás de las fotos que ya tenía y siendo la principal solo si se le dice.
     */
    public void moverA(Long modeloId, String nombreFichero, int orden, boolean principal) {
        this.modeloId = modeloId;
        this.nombreFichero = nombreFichero;
        this.orden = orden;
        this.principal = principal;
        this.fechaActualizacion = LocalDateTime.now();
    }

    /**
     * El nombre tal cual si está libre entre {@code usados} (en minúsculas);
     * si no, con {@code " (2)"}, {@code " (3)"}... delante de la extensión.
     * Una foto se identifica por su nombre dentro del modelo, así que dos con
     * el mismo nombre en un modelo serían una. Sin mirar mayúsculas
     * ({@code IMAGE.JPG} e {@code image.jpg} son el mismo fichero para quien
     * los descarga en Windows) y cortando el nombre, nunca la extensión, para
     * que quepa en la columna.
     */
    public static String nombreLibre(String nombre, Set<String> usados) {
        int punto = nombre.lastIndexOf('.');
        boolean conExtension = punto > 0 && nombre.length() - punto <= LARGO_EXTENSION;
        String base = conExtension ? nombre.substring(0, punto) : nombre;
        String extension = conExtension ? nombre.substring(punto) : "";
        for (int numero = 1; ; numero++) {
            String sufijo = numero == 1 ? "" : " (" + numero + ")";
            int cabe = LARGO_NOMBRE - sufijo.length() - extension.length();
            String candidato = base.substring(0, Math.min(base.length(), cabe)) + sufijo + extension;
            if (!usados.contains(candidato.toLowerCase(Locale.ROOT))) {
                return candidato;
            }
        }
    }

    public ResumenFoto resumen() {
        return new ResumenFoto(id, modeloId, nombreFichero, principal, orden);
    }
}
