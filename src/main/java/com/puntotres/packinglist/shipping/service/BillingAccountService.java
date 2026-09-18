package com.puntotres.packinglist.shipping.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.shipping.model.BillingAccount;
import com.puntotres.packinglist.shipping.model.BillingAccountRepository;
import com.puntotres.packinglist.shipping.model.BillingRole;

/**
 * Mantenimiento de las cuentas DHL: la propia y las de los clientes que
 * pagan ellos el transporte o los aranceles.
 */
@Service
public class BillingAccountService {

    private final BillingAccountRepository repo;

    public BillingAccountService(BillingAccountRepository repo) { this.repo = repo; }

    public List<BillingAccount> todas() { return repo.findAllByOrderByPropiaDescTitularAsc(); }

    public List<BillingAccount> activasConRol(BillingRole rol) {
        return repo.findByActivaTrueOrderByPropiaDescTitularAsc().stream().filter(c -> c.permite(rol)).toList();
    }

    @Transactional
    public BillingAccount guardar(Long id, String numero, String titular, boolean propia, Set<BillingRole> roles) {
        String limpio = normalizar(numero);
        if (limpio.isEmpty()) throw new DatoInvalidoException("El número de cuenta no puede estar vacío.");
        if (titular == null || titular.isBlank()) throw new DatoInvalidoException("La cuenta necesita un titular.");
        if (roles == null || roles.isEmpty()) throw new DatoInvalidoException("Marca al menos un uso: remitente, transporte o aranceles.");
        Optional<BillingAccount> misma = repo.findByNumero(limpio);
        if (misma.isPresent() && (id == null || !id.equals(misma.get().getId()))) {
            throw new DatoInvalidoException("Ya existe la cuenta " + limpio + " (" + misma.get().getTitular() + ").");
        }
        BillingAccount cuenta = id == null ? null : repo.findById(id).orElse(null);
        if (cuenta == null) return repo.save(new BillingAccount(limpio, titular.trim(), propia, roles));
        cuenta.setNumero(limpio);
        cuenta.setTitular(titular.trim());
        cuenta.setPropia(propia);
        cuenta.setRoles(roles);
        return repo.save(cuenta);
    }

    @Transactional
    public void activar(Long id, boolean activa) {
        repo.findById(id).ifPresent(c -> { c.setActiva(activa); repo.save(c); });
    }

    /** Alta si no existe. Si existe, solo se le añaden roles: lo tecleado a mano manda sobre lo importado. */
    @Transactional
    public BillingAccount asegurar(String numero, String titular, boolean propia, Set<BillingRole> rolesSiNueva) {
        String limpio = normalizar(numero);
        Optional<BillingAccount> existente = repo.findByNumero(limpio);
        if (existente.isPresent()) {
            rolesSiNueva.forEach(existente.get()::anadirRol);
            return repo.save(existente.get());
        }
        return repo.save(new BillingAccount(limpio, titular == null || titular.isBlank() ? limpio : titular.trim(), propia, rolesSiNueva));
    }

    static String normalizar(String numero) {
        return numero == null ? "" : numero.replaceAll("\\s+", "");
    }
}
