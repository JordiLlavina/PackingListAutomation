package com.puntotres.packinglist.shipping.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

/**
 * Entrada de la libreta: remitente o destinatario. Los "por defecto" vienen
 * de la libreta de MyDHL+ y preseleccionan cuenta e incoterm en el formulario.
 * Baja lógica (activo=false): un contacto usado en envíos no se borra.
 */
@Entity
@Table(name = "contact")
public class Contact {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa", length = 120, nullable = false) private String empresa;
    @Column(name = "persona", length = 120) private String persona;
    @Column(name = "direccion1", length = 45, nullable = false) private String direccion1;
    @Column(name = "direccion2", length = 45) private String direccion2;
    @Column(name = "direccion3", length = 45) private String direccion3;
    @Column(name = "codigo_postal", length = 12) private String codigoPostal;
    @Column(name = "ciudad", length = 45, nullable = false) private String ciudad;
    @Column(name = "provincia", length = 45) private String provincia;
    @Column(name = "provincia_codigo", length = 35) private String provinciaCodigo;
    /** ISO 3166 alfa-2, en mayúsculas. */
    @Column(name = "pais", length = 2, nullable = false) private String pais;
    /** Con prefijo internacional: +34 934425135. */
    @Column(name = "telefono", length = 35) private String telefono;
    @Column(name = "email", length = 100) private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 10, nullable = false) private ContactType tipo = ContactType.BUSINESS;
    @Column(name = "residencial", nullable = false) private boolean residencial;

    @Column(name = "nif_vat", length = 35) private String nifVat;
    @Column(name = "eori", length = 35) private String eori;
    /** ICE de las empresas marroquíes. No viene en la libreta de MyDHL+. */
    @Column(name = "identificador_marroqui", length = 35) private String identificadorMarroqui;
    @Column(name = "notas", length = 1000) private String notas;

    @Enumerated(EnumType.STRING)
    @Column(name = "incoterm_por_defecto", length = 3) private Incoterm incotermPorDefecto;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "cuenta_transporte_por_defecto_id")
    private BillingAccount cuentaTransportePorDefecto;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "cuenta_aranceles_por_defecto_id")
    private BillingAccount cuentaArancelesPorDefecto;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "contact_alias", joinColumns = @JoinColumn(name = "contact_id"))
    @Column(name = "alias", length = 120, nullable = false)
    private List<String> alias = new ArrayList<>();

    @Column(name = "activo", nullable = false) private boolean activo = true;
    @Column(name = "fecha_actualizacion", nullable = false) private LocalDateTime fechaActualizacion = LocalDateTime.now();

    protected Contact() { }

    public Contact(String empresa, String direccion1, String ciudad, String pais) {
        this.empresa = empresa;
        this.direccion1 = direccion1;
        this.ciudad = ciudad;
        this.pais = pais;
    }

    /** Lo que se ve en el desplegable: EMPRESA · Persona · Ciudad (PAÍS). */
    public String etiqueta() {
        StringBuilder sb = new StringBuilder(empresa);
        if (persona != null && !persona.isBlank() && !persona.equalsIgnoreCase(empresa)) sb.append(" · ").append(persona);
        sb.append(" · ").append(ciudad).append(" (").append(pais).append(")");
        return sb.toString();
    }

    public void tocar() { fechaActualizacion = LocalDateTime.now(); }

    // getters y setters de todos los campos (id solo getter)
    public Long getId() { return id; }
    public String getEmpresa() { return empresa; } public void setEmpresa(String v) { empresa = v; }
    public String getPersona() { return persona; } public void setPersona(String v) { persona = v; }
    public String getDireccion1() { return direccion1; } public void setDireccion1(String v) { direccion1 = v; }
    public String getDireccion2() { return direccion2; } public void setDireccion2(String v) { direccion2 = v; }
    public String getDireccion3() { return direccion3; } public void setDireccion3(String v) { direccion3 = v; }
    public String getCodigoPostal() { return codigoPostal; } public void setCodigoPostal(String v) { codigoPostal = v; }
    public String getCiudad() { return ciudad; } public void setCiudad(String v) { ciudad = v; }
    public String getProvincia() { return provincia; } public void setProvincia(String v) { provincia = v; }
    public String getProvinciaCodigo() { return provinciaCodigo; } public void setProvinciaCodigo(String v) { provinciaCodigo = v; }
    public String getPais() { return pais; } public void setPais(String v) { pais = v == null ? null : v.toUpperCase(); }
    public String getTelefono() { return telefono; } public void setTelefono(String v) { telefono = v; }
    public String getEmail() { return email; } public void setEmail(String v) { email = v; }
    public ContactType getTipo() { return tipo; } public void setTipo(ContactType v) { tipo = v; }
    public boolean isResidencial() { return residencial; } public void setResidencial(boolean v) { residencial = v; }
    public String getNifVat() { return nifVat; } public void setNifVat(String v) { nifVat = v; }
    public String getEori() { return eori; } public void setEori(String v) { eori = v; }
    public String getIdentificadorMarroqui() { return identificadorMarroqui; } public void setIdentificadorMarroqui(String v) { identificadorMarroqui = v; }
    public String getNotas() { return notas; } public void setNotas(String v) { notas = v; }
    public Incoterm getIncotermPorDefecto() { return incotermPorDefecto; } public void setIncotermPorDefecto(Incoterm v) { incotermPorDefecto = v; }
    public BillingAccount getCuentaTransportePorDefecto() { return cuentaTransportePorDefecto; } public void setCuentaTransportePorDefecto(BillingAccount v) { cuentaTransportePorDefecto = v; }
    public BillingAccount getCuentaArancelesPorDefecto() { return cuentaArancelesPorDefecto; } public void setCuentaArancelesPorDefecto(BillingAccount v) { cuentaArancelesPorDefecto = v; }
    public List<String> getAlias() { return alias; } public void setAlias(List<String> v) { alias = new ArrayList<>(v); }
    public boolean isActivo() { return activo; } public void setActivo(boolean v) { activo = v; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
}
