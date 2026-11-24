package com.puntotres.packinglist.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.persistence.CatalogoClientesJpa;
import com.puntotres.packinglist.persistence.CatalogoClientesJpa.DatosCliente;
import com.puntotres.packinglist.persistence.Cliente;

/**
 * Pantalla de clientes: alta, corrección y eliminación de los clientes de
 * plantilla genérica, que antes había que añadir al yml y reiniciar.
 *
 * AMI y APC salen en la lista pero sin botones: su packing list es código.
 *
 * Eliminar pide teclear la contraseña {@value #CONTRASENA_ELIMINAR} en un
 * emergente (decisión del usuario), y solo se puede con un cliente que no
 * tenga nada guardado. La contraseña se comprueba aquí, no solo en la página:
 * es lo que de verdad frena un borrado.
 */
@Controller
public class ClientesController {

    static final String CONTRASENA_ELIMINAR = "ELIMINAR";

    private final CatalogoClientesJpa catalogo;

    public ClientesController(CatalogoClientesJpa catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping("/clientes")
    public String clientes(@RequestParam(required = false) String editar, Model model) {
        model.addAttribute("programados", catalogo.getClientes().entrySet().stream()
                .filter(entrada -> catalogo.esProgramado(entrada.getKey()))
                .toList());
        var guardados = catalogo.guardados();
        model.addAttribute("guardados", guardados);
        // Lo que impide eliminar cada uno, ya en frase; vacío = se puede.
        Map<String, String> bloqueos = new LinkedHashMap<>();
        for (Cliente cliente : guardados) {
            List<String> datos = catalogo.datosGuardados(cliente.getClave());
            if (!datos.isEmpty()) {
                bloqueos.put(cliente.getClave(), CatalogoClientesJpa.describir(datos));
            }
        }
        model.addAttribute("bloqueos", bloqueos);
        if (!model.containsAttribute("formulario")) {
            Cliente enEdicion = editar == null ? null : guardados.stream()
                    .filter(cliente -> cliente.getClave().equalsIgnoreCase(editar.trim()))
                    .findFirst().orElse(null);
            model.addAttribute("claveEditada", enEdicion == null ? null : enEdicion.getClave());
            model.addAttribute("formulario", enEdicion == null
                    ? new DatosCliente(null, null, null, null, null)
                    : new DatosCliente(enEdicion.getNombre(), enEdicion.getNombreLegal(),
                            enEdicion.getDireccionEntrega(), enEdicion.getPlaceholderTemporada(),
                            enEdicion.getEtiquetaPedido()));
        }
        return "clientes";
    }

    @PostMapping("/clientes")
    public String guardar(@RequestParam(required = false) String clave,
                          @RequestParam(required = false) String nombre,
                          @RequestParam(required = false) String nombreLegal,
                          @RequestParam(required = false) String direccionEntrega,
                          @RequestParam(required = false) String placeholderTemporada,
                          @RequestParam(required = false) String etiquetaPedido,
                          RedirectAttributes redirect) {
        DatosCliente datos = new DatosCliente(nombre, nombreLegal, direccionEntrega,
                placeholderTemporada, etiquetaPedido);
        boolean esAlta = clave == null || clave.isBlank();
        try {
            if (esAlta) {
                catalogo.alta(datos);
                redirect.addFlashAttribute("mensaje", "Dado de alta " + datos.nombre() + ".");
            } else {
                catalogo.corregir(clave, datos);
                redirect.addFlashAttribute("mensaje", "Guardado " + datos.nombre() + ".");
            }
            return "redirect:/clientes";
        } catch (IllegalArgumentException e) {
            // Lo tecleado vuelve al formulario: con una dirección larga,
            // tener que escribirla otra vez por un nombre repetido es castigo.
            redirect.addFlashAttribute("mensajeError", e.getMessage());
            redirect.addFlashAttribute("formulario", datos);
            redirect.addFlashAttribute("claveEditada", esAlta ? null : clave);
            return "redirect:/clientes";
        }
    }

    @PostMapping("/clientes/eliminar")
    public String eliminar(@RequestParam String clave,
                           @RequestParam(required = false) String contrasena,
                           RedirectAttributes redirect) {
        if (!CONTRASENA_ELIMINAR.equals(contrasena)) {
            redirect.addFlashAttribute("mensajeError",
                    "La contraseña no es correcta: el cliente no se ha eliminado.");
            return "redirect:/clientes";
        }
        String nombre = catalogo.clientePara(clave).map(config -> config.getNombre()).orElse(clave);
        try {
            catalogo.eliminar(clave);
            redirect.addFlashAttribute("mensaje", "Eliminado el cliente " + nombre + ".");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/clientes";
    }
}
