package com.puntotres.packinglist.persistence;

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
 * Una piel de la biblioteca: QUÉ piel es (proveedor + nombre), con lo que se
 * sabe de ella. No es lo mismo que {@link Piel}, que dice cómo la llama un
 * cliente: la misma ficha se usa para varios clientes, cada uno con su
 * referencia, y por eso los datos viven aquí y {@link Piel} solo apunta.
 *
 * Todo es opcional menos el proveedor y el nombre: una ficha a medias se
 * guarda. Los rangos (grueso, superficie) son mínimo y máximo; un valor fijo
 * es mínimo = máximo. No hay campo MOQ: el mínimo es el tramo de precio más
 * bajo ({@link PrecioPiel}).
 */
@Entity
@Table(name = "ficha_piel")
public class FichaPiel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "proveedor_id", nullable = false)
    private Long proveedorId;

    @Column(name = "nombre", length = 200, nullable = false)
    private String nombre;

    @Column(name = "ref_proveedor", length = 80)
    private String refProveedor;

    @Column(name = "animal", length = 40)
    private String animal;

    @Column(name = "tipo", length = 60)
    private String tipo;

    @Column(name = "grueso_min_mm")
    private Double gruesoMinMm;

    @Column(name = "grueso_max_mm")
    private Double gruesoMaxMm;

    @Column(name = "superficie_min_m2")
    private Double superficieMinM2;

    @Column(name = "superficie_max_m2")
    private Double superficieMaxM2;

    @Column(name = "stock_service")
    private Boolean stockService;

    @Column(name = "plazo_entrega_dias")
    private Integer plazoEntregaDias;

    @Column(name = "moq_por_color")
    private Boolean moqPorColor;

    @Column(name = "fecha_precios")
    private LocalDate fechaPrecios;

    @Column(name = "calidad")
    private Integer calidad;

    @Column(name = "nota_calidad", length = 1000)
    private String notaCalidad;

    @Column(name = "comentarios", length = 4000)
    private String comentarios;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", length = 20, nullable = false)
    private OrigenFicha origen;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDateTime fechaAlta;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected FichaPiel() {
        // Constructor para JPA.
    }

    public FichaPiel(Long proveedorId, String nombre) {
        this.proveedorId = proveedorId;
        this.nombre = nombre;
        this.origen = OrigenFicha.MANUAL;
        this.fechaAlta = LocalDateTime.now();
        this.fechaActualizacion = this.fechaAlta;
    }

    public Long getId() { return id; }
    public Long getProveedorId() { return proveedorId; }
    public String getNombre() { return nombre; }
    public String getRefProveedor() { return texto(refProveedor); }
    public String getAnimal() { return texto(animal); }
    public String getTipo() { return texto(tipo); }
    public Double getGruesoMinMm() { return gruesoMinMm; }
    public Double getGruesoMaxMm() { return gruesoMaxMm; }
    public Double getSuperficieMinM2() { return superficieMinM2; }
    public Double getSuperficieMaxM2() { return superficieMaxM2; }
    public Boolean getStockService() { return stockService; }
    public Integer getPlazoEntregaDias() { return plazoEntregaDias; }
    public Boolean getMoqPorColor() { return moqPorColor; }
    public LocalDate getFechaPrecios() { return fechaPrecios; }
    public Integer getCalidad() { return calidad; }
    public String getNotaCalidad() { return texto(notaCalidad); }
    public String getComentarios() { return texto(comentarios); }
    public OrigenFicha getOrigen() { return origen; }
    public LocalDateTime getFechaAlta() { return fechaAlta; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }

    public void setProveedorId(Long proveedorId) { this.proveedorId = proveedorId; tocar(); }
    public void setNombre(String nombre) { this.nombre = nombre; tocar(); }
    public void setRefProveedor(String refProveedor) { this.refProveedor = refProveedor; tocar(); }
    public void setAnimal(String animal) { this.animal = animal; tocar(); }
    public void setTipo(String tipo) { this.tipo = tipo; tocar(); }
    public void setGruesoMinMm(Double gruesoMinMm) { this.gruesoMinMm = gruesoMinMm; tocar(); }
    public void setGruesoMaxMm(Double gruesoMaxMm) { this.gruesoMaxMm = gruesoMaxMm; tocar(); }
    public void setSuperficieMinM2(Double superficieMinM2) { this.superficieMinM2 = superficieMinM2; tocar(); }
    public void setSuperficieMaxM2(Double superficieMaxM2) { this.superficieMaxM2 = superficieMaxM2; tocar(); }
    public void setStockService(Boolean stockService) { this.stockService = stockService; tocar(); }
    public void setPlazoEntregaDias(Integer plazoEntregaDias) { this.plazoEntregaDias = plazoEntregaDias; tocar(); }
    public void setMoqPorColor(Boolean moqPorColor) { this.moqPorColor = moqPorColor; tocar(); }
    public void setFechaPrecios(LocalDate fechaPrecios) { this.fechaPrecios = fechaPrecios; tocar(); }
    public void setCalidad(Integer calidad) { this.calidad = calidad; tocar(); }
    public void setNotaCalidad(String notaCalidad) { this.notaCalidad = notaCalidad; tocar(); }
    public void setComentarios(String comentarios) { this.comentarios = comentarios; tocar(); }
    public void setOrigen(OrigenFicha origen) { this.origen = origen; tocar(); }

    private void tocar() {
        this.fechaActualizacion = LocalDateTime.now();
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }
}
