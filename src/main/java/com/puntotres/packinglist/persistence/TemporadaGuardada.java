package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Una temporada de un cliente y los excels con los que se trabaja durante
 * toda ella: el de PEDIDO del cliente y el de PRODUCCIÓN de Puntotres.
 *
 * Existe para no volver a subir los mismos ficheros en cada envío: son el
 * mismo documento durante meses, y quien prepara un envío los tiene que ir a
 * buscar al disco cada vez. Guardarlos aquí lo convierte en elegir la
 * temporada en un desplegable.
 *
 * Cuál es obligatorio lo decide el cliente, no esta clase: los de
 * {@code pedido-cliente} (AMI, APC) necesitan el de pedido y el de producción
 * es opcional; los genéricos no tienen pedido y necesitan el de producción.
 * Por eso los dos son opcionales aquí, y quien lee uno pregunta antes si está
 * ({@link #tienePedido()}, {@link #tieneProduccion()}).
 *
 * Los ficheros se guardan enteros en la base de datos y no como ruta a un
 * fichero del disco: una ruta se rompe en cuanto alguien mueve o renombra la
 * carpeta, y el fallo aparecería a mitad de un envío. Con la copia dentro, la
 * base de datos de {@code ./datos} se lleva de un ordenador a otro y las
 * temporadas viajan con ella.
 *
 * La clave es un id generado y no cliente+temporada: así se puede corregir el
 * nombre de una temporada mal tecleada sin borrarla y volver a subir el excel.
 * Que no haya dos temporadas iguales del mismo cliente lo garantizan la
 * restricción de aquí abajo y la comprobación —esta sí, sin distinguir
 * mayúsculas— de {@link ArchivoTemporadas}.
 */
@Entity
@Table(name = "temporada_guardada",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_temporada_por_cliente",
                columnNames = {"cliente", "temporada"}))
public class TemporadaGuardada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Clave del cliente en el catálogo de application.yml (AMI, APC...). */
    @Column(name = "cliente", length = 60, nullable = false)
    private String cliente;

    /** Como la llama el cliente: H26, FALL26, E25... Va tal cual a los excels. */
    @Column(name = "temporada", length = 60, nullable = false)
    private String temporada;

    @Column(name = "nombre_fichero_pedido", length = 255)
    private String nombreFicheroPedido;

    @Lob
    @Column(name = "excel_pedido")
    private byte[] excelPedido;

    @Column(name = "nombre_fichero_produccion", length = 255)
    private String nombreFicheroProduccion;

    @Lob
    @Column(name = "excel_produccion")
    private byte[] excelProduccion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected TemporadaGuardada() {
        // Constructor para JPA.
    }

    public TemporadaGuardada(String cliente, String temporada) {
        this.cliente = cliente;
        this.temporada = temporada;
        this.fechaActualizacion = LocalDateTime.now();
    }

    /** Con su excel de pedido: la forma de siempre de AMI y APC. */
    public TemporadaGuardada(String cliente, String temporada, String nombreFichero, byte[] excel) {
        this(cliente, temporada);
        sustituirPedido(new FicheroExcel(nombreFichero, excel));
    }

    public Long getId() {
        return id;
    }

    public String getCliente() {
        return cliente;
    }

    public String getTemporada() {
        return temporada;
    }

    public String getNombreFicheroPedido() {
        return nombreFicheroPedido;
    }

    public byte[] getExcelPedido() {
        return excelPedido;
    }

    public String getNombreFicheroProduccion() {
        return nombreFicheroProduccion;
    }

    public byte[] getExcelProduccion() {
        return excelProduccion;
    }

    public boolean tienePedido() {
        return excelPedido != null;
    }

    public boolean tieneProduccion() {
        return excelProduccion != null;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    /** Corrige el cliente o el nombre; los excels se quedan como estaban. */
    public void renombrar(String cliente, String temporada) {
        this.cliente = cliente;
        this.temporada = temporada;
        this.fechaActualizacion = LocalDateTime.now();
    }

    /** Un fichero nulo no toca nada: en la pantalla, no adjuntar es "deja el que había". */
    public void sustituirPedido(FicheroExcel fichero) {
        if (fichero != null) {
            this.nombreFicheroPedido = fichero.nombre();
            this.excelPedido = fichero.bytes();
            this.fechaActualizacion = LocalDateTime.now();
        }
    }

    /** Como {@link #sustituirPedido}, con el excel de producción. */
    public void sustituirProduccion(FicheroExcel fichero) {
        if (fichero != null) {
            this.nombreFicheroProduccion = fichero.nombre();
            this.excelProduccion = fichero.bytes();
            this.fechaActualizacion = LocalDateTime.now();
        }
    }

    /**
     * Quita el excel de pedido. Solo pasa al llevar la temporada a un cliente
     * genérico, que no tiene pedido: guardado, el packing list de ese cliente
     * lo leería como pedido.
     */
    public void quitarPedido() {
        this.nombreFicheroPedido = null;
        this.excelPedido = null;
        this.fechaActualizacion = LocalDateTime.now();
    }
}
