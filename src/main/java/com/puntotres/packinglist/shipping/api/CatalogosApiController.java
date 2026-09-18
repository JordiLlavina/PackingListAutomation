package com.puntotres.packinglist.shipping.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.puntotres.packinglist.shipping.model.BillingRole;
import com.puntotres.packinglist.shipping.service.*;

/** Catálogos de solo lectura para que un agente rellene un envío: contactos, cuentas, motivos y artículos. */
@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class CatalogosApiController {

    public record CuentaDto(Long id, String numero, String titular, boolean propia, Set<BillingRole> roles) { }
    public record MotivoDto(Long id, String nombre, String motivoDhl) { }
    public record ArticuloDto(Long id, String referencia, String descripcionAduanera, String codigoHs, String paisOrigen, Double pesoNeto, Double pesoBruto, BigDecimal valorUnitario) { }

    private final ContactService contactos;
    private final BillingAccountService cuentas;
    private final ShipmentReasonService motivos;
    private final ArticleService articulos;

    public CatalogosApiController(ContactService contactos, BillingAccountService cuentas, ShipmentReasonService motivos, ArticleService articulos) {
        this.contactos = contactos; this.cuentas = cuentas; this.motivos = motivos; this.articulos = articulos;
    }

    @GetMapping("/contacts")
    public List<ContactDto> contactos(@RequestParam(required = false) String q) { return contactos.buscar(q).stream().map(ContactDto::de).toList(); }

    @GetMapping("/billing-accounts")
    public List<CuentaDto> cuentas() {
        return cuentas.todas().stream().filter(c -> c.isActiva()).map(c -> new CuentaDto(c.getId(), c.getNumero(), c.getTitular(), c.isPropia(), c.getRoles())).toList();
    }

    @GetMapping("/reasons")
    public List<MotivoDto> motivos() { return motivos.activos().stream().map(m -> new MotivoDto(m.getId(), m.getNombre(), m.getMotivoDhl().codigoDhl())).toList(); }

    @GetMapping("/articles")
    public List<ArticuloDto> articulos(@RequestParam(required = false) String q) {
        return articulos.activos().stream()
                .filter(a -> q == null || q.isBlank() || a.getReferencia().toLowerCase().contains(q.toLowerCase()) || a.getDescripcionAduanera().toLowerCase().contains(q.toLowerCase()))
                .map(a -> new ArticuloDto(a.getId(), a.getReferencia(), a.getDescripcionAduanera(), a.getCodigoHs(), a.getPaisOrigen(), a.getPesoNetoUnitario(), a.getPesoBrutoUnitario(), a.getValorUnitario())).toList();
    }
}
