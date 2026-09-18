package com.puntotres.packinglist.shipping.web;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.shipping.carrier.AddressCheck;
import com.puntotres.packinglist.shipping.carrier.CarrierException;
import com.puntotres.packinglist.shipping.carrier.TipoDireccion;
import com.puntotres.packinglist.shipping.importacion.ImportadorLibretaMyDhl;
import com.puntotres.packinglist.shipping.importacion.PrevisualizacionLibreta;
import com.puntotres.packinglist.shipping.importacion.ResultadoImportacion;
import com.puntotres.packinglist.shipping.model.*;
import com.puntotres.packinglist.shipping.service.*;

/**
 * Libreta de contactos: alta y edición manual, y la importación en dos pasos
 * (previsualizar → confirmar) del CSV de la libreta de MyDHL+.
 */
@Controller
@RequestMapping("/envios/contactos")
public class ContactosController {

    private final ContactService contactos;
    private final BillingAccountService cuentas;
    private final ImportadorLibretaMyDhl importador;
    private final ImportacionEnCurso enCurso;

    public ContactosController(ContactService contactos, BillingAccountService cuentas, ImportadorLibretaMyDhl importador, ImportacionEnCurso enCurso) {
        this.contactos = contactos; this.cuentas = cuentas; this.importador = importador; this.enCurso = enCurso;
    }

    @GetMapping
    public String listado(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("q", q);
        model.addAttribute("contactos", contactos.buscar(q));
        return "contactos";
    }

    @GetMapping("/nuevo")
    public String nuevo(@RequestParam(required = false) String volver, Model model) {
        return ficha(model, new ContactoForm(), volver);
    }

    @GetMapping("/{id}")
    public String editar(@PathVariable Long id, @RequestParam(required = false) String volver, Model model) {
        Contact c = contactos.porId(id).orElseThrow(() -> new DatoInvalidoException("No existe el contacto " + id));
        return ficha(model, ContactoForm.de(c), volver);
    }

    @PostMapping
    public String guardar(@ModelAttribute ContactoForm form, @RequestParam(required = false) String volver, RedirectAttributes redirect) {
        try {
            Contact c = contactos.guardar(form.getId(), form.aDatos());
            redirect.addFlashAttribute("mensaje", "Guardado " + c.getEmpresa() + ".");
            return "redirect:" + volverSeguro(volver);
        } catch (DatoInvalidoException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
            return "redirect:" + (form.getId() == null ? "/envios/contactos/nuevo" : "/envios/contactos/" + form.getId())
                    + (volver == null ? "" : "?volver=" + volverSeguro(volver));
        }
    }

    /**
     * "volver" viaja como parámetro de la URL para volver a la pantalla que
     * abrió la ficha (la del envío, normalmente) en vez de a la libreta, y se
     * redirige y se pinta tal cual llega. Sin acotarlo, un enlace con
     * {@code ?volver=https://evil.example} redirigiría fuera de la aplicación
     * (open redirect) y el hidden del formulario lo devolvería en el href de
     * "Volver" sin comprobar nada. Solo se acepta una ruta local que empiece
     * por "/" y no por "//" (que el navegador trata como protocolo-relativa,
     * o sea otro host); cualquier otra cosa cae al destino de siempre.
     */
    private static String volverSeguro(String volver) {
        return volver != null && volver.startsWith("/") && !volver.startsWith("//") ? volver : "/envios/contactos";
    }

    /** Un id que no existe es un enlace viejo (favorito, pestaña olvidada), no un fallo del programa: se avisa y se vuelve a la libreta en vez de dar un 500. */
    @ExceptionHandler(DatoInvalidoException.class)
    public String contactoInvalido(DatoInvalidoException e, RedirectAttributes redirect) {
        redirect.addFlashAttribute("mensajeError", e.getMessage());
        return "redirect:/envios/contactos";
    }

    @PostMapping("/{id}/baja")
    public String baja(@PathVariable Long id, RedirectAttributes redirect) {
        contactos.darDeBaja(id);
        redirect.addFlashAttribute("mensaje", "Contacto dado de baja. Los envíos que lo usaban lo conservan.");
        return "redirect:/envios/contactos";
    }

    @PostMapping("/{id}/comprobar")
    public String comprobar(@PathVariable Long id, @RequestParam TipoDireccion tipo, RedirectAttributes redirect) {
        try {
            AddressCheck r = contactos.comprobarDireccion(id, tipo);
            redirect.addFlashAttribute(r.valida() ? "mensaje" : "mensajeError", r.detalle());
        } catch (CarrierException e) {
            redirect.addFlashAttribute("mensajeError", e.getMensajeUsuario());
        }
        return "redirect:/envios/contactos/" + id;
    }

    @GetMapping("/importar")
    public String importar(Model model) {
        model.addAttribute("previsualizacion", enCurso.getLibreta());
        return "contactos-importar";
    }

    @PostMapping("/importar")
    public String previsualizar(@RequestParam MultipartFile fichero, RedirectAttributes redirect) throws IOException {
        try {
            enCurso.setLibreta(importador.previsualizar(fichero.getBytes(), fichero.getOriginalFilename()));
        } catch (DatoInvalidoException | IllegalArgumentException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/envios/contactos/importar";
    }

    @PostMapping("/importar/confirmar")
    public String confirmar(@RequestParam(required = false) List<Integer> duplicadas, RedirectAttributes redirect) {
        PrevisualizacionLibreta prev = enCurso.getLibreta();
        if (prev == null) return "redirect:/envios/contactos/importar";
        ResultadoImportacion r = importador.importar(prev, duplicadas == null ? Set.of() : Set.copyOf(duplicadas));
        enCurso.setLibreta(null);
        redirect.addFlashAttribute("mensaje", r.altas() + " contactos dados de alta, " + r.omitidas() + " omitidos por duplicados.");
        if (!r.errores().isEmpty()) redirect.addFlashAttribute("errores", r.errores());
        return "redirect:/envios/contactos";
    }

    private String ficha(Model model, ContactoForm form, String volver) {
        model.addAttribute("form", form);
        model.addAttribute("volver", volverSeguro(volver));
        model.addAttribute("tipos", ContactType.values());
        model.addAttribute("incoterms", Incoterm.values());
        model.addAttribute("cuentasTransporte", cuentas.activasConRol(BillingRole.PAYER));
        model.addAttribute("cuentasAranceles", cuentas.activasConRol(BillingRole.DUTIES));
        model.addAttribute("tiposDireccion", TipoDireccion.values());
        return "contacto";
    }

    /** Formulario de la ficha. Los alias van en un textarea, uno por línea. */
    public static class ContactoForm {
        private Long id;
        private String empresa, persona, direccion1, direccion2, direccion3, codigoPostal, ciudad, provincia, provinciaCodigo, pais,
                telefono, email, nifVat, eori, identificadorMarroqui, notas, aliasTexto;
        private ContactType tipo = ContactType.BUSINESS;
        private boolean residencial;
        private Incoterm incotermPorDefecto;
        private Long cuentaTransportePorDefectoId, cuentaArancelesPorDefectoId;

        static ContactoForm de(Contact c) {
            ContactoForm f = new ContactoForm();
            f.id = c.getId(); f.empresa = c.getEmpresa(); f.persona = c.getPersona();
            f.direccion1 = c.getDireccion1(); f.direccion2 = c.getDireccion2(); f.direccion3 = c.getDireccion3();
            f.codigoPostal = c.getCodigoPostal(); f.ciudad = c.getCiudad(); f.provincia = c.getProvincia(); f.provinciaCodigo = c.getProvinciaCodigo();
            f.pais = c.getPais(); f.telefono = c.getTelefono(); f.email = c.getEmail(); f.tipo = c.getTipo(); f.residencial = c.isResidencial();
            f.nifVat = c.getNifVat(); f.eori = c.getEori(); f.identificadorMarroqui = c.getIdentificadorMarroqui(); f.notas = c.getNotas();
            f.incotermPorDefecto = c.getIncotermPorDefecto();
            f.cuentaTransportePorDefectoId = c.getCuentaTransportePorDefecto() == null ? null : c.getCuentaTransportePorDefecto().getId();
            f.cuentaArancelesPorDefectoId = c.getCuentaArancelesPorDefecto() == null ? null : c.getCuentaArancelesPorDefecto().getId();
            f.aliasTexto = String.join("\n", c.getAlias());
            return f;
        }

        DatosContacto aDatos() {
            List<String> alias = aliasTexto == null ? List.of() : Arrays.stream(aliasTexto.split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
            return new DatosContacto(empresa, persona, direccion1, direccion2, direccion3, codigoPostal, ciudad, provincia, provinciaCodigo, pais,
                    telefono, email, tipo, residencial, nifVat, eori, identificadorMarroqui, notas, incotermPorDefecto,
                    cuentaTransportePorDefectoId, cuentaArancelesPorDefectoId, alias);
        }

        // getters y setters de todos los campos (necesarios para @ModelAttribute y th:field)
        public Long getId() { return id; } public void setId(Long v) { id = v; }
        public String getEmpresa() { return empresa; } public void setEmpresa(String v) { empresa = v; }
        public String getPersona() { return persona; } public void setPersona(String v) { persona = v; }
        public String getDireccion1() { return direccion1; } public void setDireccion1(String v) { direccion1 = v; }
        public String getDireccion2() { return direccion2; } public void setDireccion2(String v) { direccion2 = v; }
        public String getDireccion3() { return direccion3; } public void setDireccion3(String v) { direccion3 = v; }
        public String getCodigoPostal() { return codigoPostal; } public void setCodigoPostal(String v) { codigoPostal = v; }
        public String getCiudad() { return ciudad; } public void setCiudad(String v) { ciudad = v; }
        public String getProvincia() { return provincia; } public void setProvincia(String v) { provincia = v; }
        public String getProvinciaCodigo() { return provinciaCodigo; } public void setProvinciaCodigo(String v) { provinciaCodigo = v; }
        public String getPais() { return pais; } public void setPais(String v) { pais = v; }
        public String getTelefono() { return telefono; } public void setTelefono(String v) { telefono = v; }
        public String getEmail() { return email; } public void setEmail(String v) { email = v; }
        public String getNifVat() { return nifVat; } public void setNifVat(String v) { nifVat = v; }
        public String getEori() { return eori; } public void setEori(String v) { eori = v; }
        public String getIdentificadorMarroqui() { return identificadorMarroqui; } public void setIdentificadorMarroqui(String v) { identificadorMarroqui = v; }
        public String getNotas() { return notas; } public void setNotas(String v) { notas = v; }
        public String getAliasTexto() { return aliasTexto; } public void setAliasTexto(String v) { aliasTexto = v; }
        public ContactType getTipo() { return tipo; } public void setTipo(ContactType v) { tipo = v; }
        public boolean isResidencial() { return residencial; } public void setResidencial(boolean v) { residencial = v; }
        public Incoterm getIncotermPorDefecto() { return incotermPorDefecto; } public void setIncotermPorDefecto(Incoterm v) { incotermPorDefecto = v; }
        public Long getCuentaTransportePorDefectoId() { return cuentaTransportePorDefectoId; } public void setCuentaTransportePorDefectoId(Long v) { cuentaTransportePorDefectoId = v; }
        public Long getCuentaArancelesPorDefectoId() { return cuentaArancelesPorDefectoId; } public void setCuentaArancelesPorDefectoId(Long v) { cuentaArancelesPorDefectoId = v; }
    }
}
