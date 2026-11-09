package com.puntotres.packinglist.modelos.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.modelos.ModelosProperties;
import com.puntotres.packinglist.modelos.ModelosProperties.TipoCambio;
import com.puntotres.packinglist.modelos.service.TextosDestino;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.FicheroExcel;
import com.puntotres.packinglist.persistence.TemporadaGuardada;

/**
 * Las listas que rellenan los desplegables de la app: clientes, temporadas,
 * tipos de cambio y dónde aplica un cambio el despacho. Las lee cualquiera que
 * haya entrado; crear una temporada es cosa del despacho.
 */
@RestController
@RequestMapping("/api")
public class CatalogoAuxController {

    private final ClientesProperties clientes;
    private final ArchivoTemporadas temporadas;
    private final ModelosProperties props;

    public CatalogoAuxController(ClientesProperties clientes, ArchivoTemporadas temporadas,
                                 ModelosProperties props) {
        this.clientes = clientes;
        this.temporadas = temporadas;
        this.props = props;
    }

    public record ClienteDto(String clave, String nombre) {
    }

    public record TemporadaDto(Long id, String temporada) {
    }

    /** Una temporada nueva, solo con su nombre: los excels se adjuntan después en /temporadas. */
    public record NuevaTemporada(String cliente, String temporada) {
    }

    public record DestinoAplicacionDto(String clave, String texto) {
    }

    @GetMapping("/clientes")
    public List<ClienteDto> clientes() {
        return clientes.getClientes().entrySet().stream()
                .map(entrada -> new ClienteDto(entrada.getKey(), nombreDe(entrada.getKey(),
                        entrada.getValue().getNombre())))
                .toList();
    }

    @GetMapping("/temporadas")
    public List<TemporadaDto> temporadas(@RequestParam String cliente) {
        return temporadas.de(cliente).stream()
                .map(resumen -> new TemporadaDto(resumen.id(), resumen.temporada()))
                .toList();
    }

    @PostMapping("/temporadas")
    @PreAuthorize("hasAnyRole('DESPACHO','ADMIN')")
    public ResponseEntity<TemporadaDto> crearTemporada(@RequestBody NuevaTemporada nueva) {
        String nombre = nueva.temporada() == null ? "" : nueva.temporada().trim();
        String clave = claveDe(nueva.cliente());
        if (nombre.isEmpty()) {
            throw new IllegalArgumentException("Falta el nombre de la temporada");
        }
        if (clave == null) {
            throw new IllegalArgumentException("No hay ningún cliente " + nueva.cliente());
        }
        if (temporadas.yaExiste(clave, nombre, null)) {
            throw new IllegalArgumentException("Ya existe la temporada " + nombre + " de " + clave);
        }
        TemporadaGuardada guardada = temporadas.guardar(null, clave, nombre, (FicheroExcel) null, null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new TemporadaDto(guardada.getId(), guardada.getTemporada()));
    }

    @GetMapping("/tipos-cambio")
    public List<TipoCambio> tiposCambio() {
        return props.tiposCambio();
    }

    @GetMapping("/destinos-aplicacion")
    public List<DestinoAplicacionDto> destinosAplicacion() {
        return props.destinosAplicacion().stream()
                .map(clave -> new DestinoAplicacionDto(clave, TextosDestino.texto(clave)))
                .toList();
    }

    /** La clave del catálogo que corresponde a lo recibido (sin mirar mayúsculas ni espacios), o null. */
    private String claveDe(String cliente) {
        if (cliente == null || cliente.isBlank()) {
            return null;
        }
        return clientes.getClientes().keySet().stream()
                .filter(clave -> clave.equalsIgnoreCase(cliente.trim()))
                .findFirst()
                .orElse(null);
    }

    private static String nombreDe(String clave, String nombre) {
        return nombre == null || nombre.isBlank() ? clave : nombre;
    }
}
