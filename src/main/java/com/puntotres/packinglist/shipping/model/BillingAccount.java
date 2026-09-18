package com.puntotres.packinglist.shipping.model;

import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.*;

/**
 * Una cuenta DHL: la propia o la de un cliente que paga él el transporte o los
 * aranceles. Los roles dicen en qué desplegable puede aparecer.
 */
@Entity
@Table(name = "billing_account", uniqueConstraints = @UniqueConstraint(name = "uk_billing_account_numero", columnNames = "numero"))
public class BillingAccount {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero", length = 12, nullable = false)
    private String numero;

    @Column(name = "titular", length = 120, nullable = false)
    private String titular;

    @Column(name = "propia", nullable = false)
    private boolean propia;

    @Column(name = "activa", nullable = false)
    private boolean activa = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "billing_account_roles", joinColumns = @JoinColumn(name = "billing_account_id"))
    @Column(name = "rol", length = 10, nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<BillingRole> roles = EnumSet.noneOf(BillingRole.class);

    protected BillingAccount() { }

    public BillingAccount(String numero, String titular, boolean propia, Set<BillingRole> roles) {
        this.numero = numero;
        this.titular = titular;
        this.propia = propia;
        setRoles(roles);
    }

    public boolean permite(BillingRole rol) { return roles.contains(rol); }

    public Long getId() { return id; }
    public String getNumero() { return numero; }
    public String getTitular() { return titular; }
    public boolean isPropia() { return propia; }
    public boolean isActiva() { return activa; }
    public Set<BillingRole> getRoles() { return roles; }
    public void setNumero(String numero) { this.numero = numero; }
    public void setTitular(String titular) { this.titular = titular; }
    public void setPropia(boolean propia) { this.propia = propia; }
    public void setActiva(boolean activa) { this.activa = activa; }
    /** EnumSet.copyOf lanza con una colección vacía que no sea ya un EnumSet (Set.of(), HashSet vacío...); null significa sin roles, como una colección vacía. */
    public void setRoles(Set<BillingRole> roles) { this.roles = (roles == null || roles.isEmpty()) ? EnumSet.noneOf(BillingRole.class) : EnumSet.copyOf(roles); }
    public void anadirRol(BillingRole rol) { roles.add(rol); }
}
