package com.puntotres.packinglist.shipping.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.shipping.carrier.AddressCheck;
import com.puntotres.packinglist.shipping.carrier.CarrierClientRegistry;
import com.puntotres.packinglist.shipping.carrier.TipoDireccion;
import com.puntotres.packinglist.shipping.model.*;

/**
 * Mantenimiento de la libreta de contactos: remitentes y destinatarios de un
 * envío. Baja lógica (activo=false), como las cuentas DHL.
 */
@Service
public class ContactService {

    private final ContactRepository repo;
    private final BillingAccountRepository cuentas;
    private final CarrierClientRegistry transportistas;

    public ContactService(ContactRepository repo, BillingAccountRepository cuentas, CarrierClientRegistry transportistas) {
        this.repo = repo; this.cuentas = cuentas; this.transportistas = transportistas;
    }

    public List<Contact> activos() { return repo.findByActivoTrueOrderByEmpresaAscPersonaAsc(); }

    /** Búsqueda en memoria: la libreta tiene unos cientos de contactos y así entra también por alias. */
    public List<Contact> buscar(String q) {
        if (q == null || q.isBlank()) return activos();
        String aguja = q.trim().toLowerCase(Locale.ROOT);
        return activos().stream().filter(c -> contiene(c.getEmpresa(), aguja) || contiene(c.getPersona(), aguja)
                || contiene(c.getCiudad(), aguja) || c.getAlias().stream().anyMatch(a -> contiene(a, aguja))).toList();
    }

    public Optional<Contact> porId(Long id) { return id == null ? Optional.empty() : repo.findById(id); }

    public boolean existe(String empresa, String persona) {
        return !repo.findByEmpresaIgnoreCaseAndPersonaIgnoreCase(empresa == null ? "" : empresa.trim(), persona == null ? "" : persona.trim()).isEmpty();
    }

    @Transactional
    public Contact guardar(Long id, DatosContacto d) {
        String empresa = primeroNoVacio(d.empresa(), d.persona());
        if (empresa == null) throw new DatoInvalidoException("El contacto necesita empresa o persona.");
        if (vacio(d.direccion1())) throw new DatoInvalidoException("El contacto " + empresa + " necesita dirección.");
        if (vacio(d.ciudad())) throw new DatoInvalidoException("El contacto " + empresa + " necesita ciudad.");
        if (d.pais() == null || d.pais().trim().length() != 2) throw new DatoInvalidoException("El país de " + empresa + " es el código de dos letras (ES, MA, FR).");

        Contact c = id == null ? null : repo.findById(id).orElse(null);
        if (c == null) c = new Contact(empresa, d.direccion1().trim(), d.ciudad().trim(), d.pais().trim().toUpperCase());
        c.setEmpresa(empresa);
        c.setPersona(limpiar(d.persona()));
        c.setDireccion1(d.direccion1().trim());
        c.setDireccion2(limpiar(d.direccion2()));
        c.setDireccion3(limpiar(d.direccion3()));
        c.setCodigoPostal(limpiar(d.codigoPostal()));
        c.setCiudad(d.ciudad().trim());
        c.setProvincia(limpiar(d.provincia()));
        c.setProvinciaCodigo(limpiar(d.provinciaCodigo()));
        c.setPais(d.pais().trim());
        c.setTelefono(limpiar(d.telefono()));
        c.setEmail(limpiar(d.email()));
        c.setTipo(d.tipo());
        c.setResidencial(d.residencial());
        c.setNifVat(limpiar(d.nifVat()));
        c.setEori(limpiar(d.eori()));
        c.setIdentificadorMarroqui(limpiar(d.identificadorMarroqui()));
        c.setNotas(limpiar(d.notas()));
        c.setIncotermPorDefecto(d.incotermPorDefecto());
        c.setCuentaTransportePorDefecto(d.cuentaTransportePorDefectoId() == null ? null : cuentas.findById(d.cuentaTransportePorDefectoId()).orElse(null));
        c.setCuentaArancelesPorDefecto(d.cuentaArancelesPorDefectoId() == null ? null : cuentas.findById(d.cuentaArancelesPorDefectoId()).orElse(null));
        c.setAlias(d.alias().stream().map(String::trim).filter(a -> !a.isEmpty()).toList());
        c.tocar();
        return repo.save(c);
    }

    @Transactional
    public void darDeBaja(Long id) {
        repo.findById(id).ifPresent(c -> { c.setActivo(false); c.tocar(); repo.save(c); });
    }

    public AddressCheck comprobarDireccion(Long id, TipoDireccion tipo) {
        Contact c = repo.findById(id).orElseThrow(() -> new DatoInvalidoException("No existe el contacto " + id));
        return transportistas.para(CarrierCode.DHL_EXPRESS).validateAddress(c, tipo);
    }

    private static boolean contiene(String texto, String aguja) {
        return texto != null && texto.toLowerCase(Locale.ROOT).contains(aguja);
    }
    private static boolean vacio(String s) { return s == null || s.isBlank(); }
    private static String limpiar(String s) { return vacio(s) ? null : s.trim(); }
    private static String primeroNoVacio(String a, String b) { return !vacio(a) ? a.trim() : !vacio(b) ? b.trim() : null; }
}
