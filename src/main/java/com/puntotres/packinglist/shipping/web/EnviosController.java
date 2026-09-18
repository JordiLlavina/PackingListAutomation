package com.puntotres.packinglist.shipping.web;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.ResumenTemporada;
import com.puntotres.packinglist.shipping.config.EnviosProperties;
import com.puntotres.packinglist.shipping.model.*;
import com.puntotres.packinglist.shipping.service.*;

/**
 * El envío es un solo formulario y cada botón es un submit con un parámetro
 * "accion": guardar, añadir/quitar filas, tarifar, confirmar y crear,
 * reintentar, cancelar. Todo lo tecleado se aplica antes de la acción, así que
 * nunca se pierde nada al pulsar un botón.
 *
 * En QUOTED el formulario se pinta de solo lectura: cualquier edición devuelve
 * a DRAFT y borra la tarifa, y eso tiene que ser una decisión visible
 * ("Modificar datos"), no un efecto de tocar una casilla.
 */
@Controller
@RequestMapping("/envios")
public class EnviosController {

    private final ShipmentService envios;
    private final ContactService contactos;
    private final BillingAccountService cuentas;
    private final ShipmentReasonService motivos;
    private final ArticleService articulos;
    private final EnviosProperties propiedades;
    private final ClientesProperties clientes;
    private final ArchivoTemporadas temporadas;

    public EnviosController(ShipmentService envios, ContactService contactos, BillingAccountService cuentas, ShipmentReasonService motivos,
                            ArticleService articulos, EnviosProperties propiedades, ClientesProperties clientes, ArchivoTemporadas temporadas) {
        this.envios = envios; this.contactos = contactos; this.cuentas = cuentas; this.motivos = motivos;
        this.articulos = articulos; this.propiedades = propiedades; this.clientes = clientes; this.temporadas = temporadas;
    }

    @GetMapping
    public String listado(@ModelAttribute("filtro") FiltroEnviosForm filtro, Model model) {
        model.addAttribute("envios", envios.listar(filtro.aFiltro()));
        model.addAttribute("estados", ShipmentStatus.values());
        model.addAttribute("sentidos", Direction.values());
        model.addAttribute("motivos", motivos.todos());
        return "envios";
    }

    @PostMapping("/nuevo")
    public String nuevo() {
        String responsable = propiedades.getResponsables().isEmpty() ? "" : propiedades.getResponsables().get(0);
        Shipment envio = envios.crearBorrador(responsable);
        return "redirect:/envios/" + envio.getId();
    }

    @GetMapping("/{id}")
    public String ficha(@PathVariable Long id, Model model) {
        Shipment envio = envios.cargar(id);
        EnvioForm form = EnvioForm.de(envio);
        form.heredarDe(envio.getDestinatario());
        return pintar(envio, form, model);
    }

    @PostMapping("/{id}")
    public String accion(@PathVariable Long id, @ModelAttribute("form") EnvioForm form, @RequestParam String accion,
                         @RequestParam(required = false) Integer indice, @RequestParam(required = false) Long articuloId,
                         RedirectAttributes redirect) {
        try {
            switch (accion) {
                case "guardar" -> { envios.editar(id, form.aDatos()); redirect.addFlashAttribute("mensaje", "Guardado."); }
                case "anadirBulto" -> { form.getBultos().add(new EnvioForm.BultoForm()); envios.editar(id, form.aDatos()); }
                case "quitarBulto" -> { if (indice != null && indice < form.getBultos().size()) form.getBultos().remove(indice.intValue()); envios.editar(id, form.aDatos()); }
                case "anadirLinea" -> { form.getLineas().add(new EnvioForm.LineaForm()); envios.editar(id, form.aDatos()); }
                case "anadirArticulo" -> { articulos.porId(articuloId).ifPresent(form::anadirLineaDe); envios.editar(id, form.aDatos()); }
                case "quitarLinea" -> { if (indice != null && indice < form.getLineas().size()) form.getLineas().remove(indice.intValue()); envios.editar(id, form.aDatos()); }
                case "intercambiar" -> { Long r = form.getRemitenteId(); form.setRemitenteId(form.getDestinatarioId()); form.setDestinatarioId(r); envios.editar(id, form.aDatos()); }
                case "tarifar" -> {
                    envios.editar(id, form.aDatos());
                    ResultadoTarifa r = envios.tarifar(id);
                    if (!r.problemas().isEmpty()) redirect.addFlashAttribute("problemas", r.problemas());
                    else if (r.errorTransportista() != null) redirect.addFlashAttribute("mensajeError", r.errorTransportista());
                    else redirect.addFlashAttribute("mensaje", r.envio().getOfertas().size() + " productos disponibles. Elige uno y confirma.");
                }
                case "modificar" -> { envios.editar(id, EnvioForm.de(envios.cargar(id)).aDatos()); redirect.addFlashAttribute("mensaje", "De vuelta a borrador; la tarifa anterior ya no vale."); }
                case "confirmar" -> {
                    if (form.getProductoCodigo() == null || form.getProductoCodigo().isBlank()) {
                        redirect.addFlashAttribute("mensajeError", "Elige un producto de la tarifa antes de confirmar.");
                        break;
                    }
                    envios.confirmar(id, form.getProductoCodigo());
                    Shipment creado = envios.crear(id);
                    if (creado.getEstado() == ShipmentStatus.CREATED) redirect.addFlashAttribute("mensaje", "Envío creado en DHL. Guía " + creado.getNumeroGuia() + ".");
                    else redirect.addFlashAttribute("mensajeError", creado.getUltimoError());
                }
                case "reintentar" -> {
                    Shipment creado = envios.crear(id);
                    if (creado.getEstado() == ShipmentStatus.CREATED) redirect.addFlashAttribute("mensaje", "Envío creado en DHL. Guía " + creado.getNumeroGuia() + ".");
                    else redirect.addFlashAttribute("mensajeError", creado.getUltimoError());
                }
                case "cancelar" -> { envios.cancelar(id); redirect.addFlashAttribute("mensaje", "Envío cancelado."); }
                default -> redirect.addFlashAttribute("mensajeError", "Acción desconocida: " + accion);
            }
        } catch (TransicionIlegalException | ObjectOptimisticLockingFailureException e) {
            // Doble clic o dos pestañas: la pantalla se recarga con el estado real, que es lo que hay que ver.
            redirect.addFlashAttribute("mensajeError", "El envío ha cambiado de estado mientras tanto. Se muestra tal como está ahora.");
        }
        return "redirect:/envios/" + id;
    }

    @GetMapping("/{id}/documentos/{tipo}.pdf")
    public ResponseEntity<byte[]> documento(@PathVariable Long id, @PathVariable String tipo) {
        Optional<ShipmentDocument> doc = envios.documento(id, tipo);
        if (doc.isEmpty()) return ResponseEntity.notFound().build();
        Shipment envio = envios.cargar(id);
        String nombre = "DHL_" + envio.getNumeroGuia() + "_" + tipo + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(doc.get().getContenido());
    }

    @ExceptionHandler(EnvioNoEncontradoException.class)
    public String noEncontrado(EnvioNoEncontradoException e, RedirectAttributes redirect) {
        redirect.addFlashAttribute("mensajeError", e.getMessage());
        return "redirect:/envios";
    }

    private String pintar(Shipment envio, EnvioForm form, Model model) {
        boolean editable = envio.getEstado() == ShipmentStatus.DRAFT;
        model.addAttribute("envio", envio);
        model.addAttribute("form", form);
        model.addAttribute("editable", editable);
        model.addAttribute("problemas", editable || envio.getEstado() == ShipmentStatus.QUOTED ? envios.problemasParaTarifar(envio) : List.of());
        model.addAttribute("avisos", envios.avisos(envio));
        model.addAttribute("contactos", contactos.activos());
        model.addAttribute("motivos", motivos.activos());
        model.addAttribute("responsables", propiedades.getResponsables());
        model.addAttribute("incoterms", Incoterm.values());
        model.addAttribute("cuentasTransporte", cuentas.activasConRol(BillingRole.PAYER));
        model.addAttribute("cuentasAranceles", cuentas.activasConRol(BillingRole.DUTIES));
        model.addAttribute("articulos", articulos.activos());
        model.addAttribute("clientes", clientes.getClientes().keySet());
        model.addAttribute("temporadas", temporadas.todas().stream().map(ResumenTemporada::temporada).distinct().toList());
        model.addAttribute("tiposDocumento", envio.getEstado() == ShipmentStatus.CREATED ? envios.tiposDocumento(envio.getId()) : List.of());
        return "envio";
    }
}
