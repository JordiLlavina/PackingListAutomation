package com.puntotres.packinglist.shipping.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.puntotres.packinglist.shipping.model.*;
import com.puntotres.packinglist.shipping.service.*;

/**
 * El mismo flujo que la pantalla, para un agente o un script: borrador →
 * quote → confirm → create. Sin autenticación en esta fase (como el resto de
 * la app, solo dentro de la red de la empresa).
 */
@RestController
@RequestMapping(value = "/api/shipments", produces = MediaType.APPLICATION_JSON_VALUE)
public class ShipmentsApiController {

    public record ConfirmInput(String productCode) { }

    private final ShipmentService envios;

    public ShipmentsApiController(ShipmentService envios) { this.envios = envios; }

    @PostMapping
    public ResponseEntity<ShipmentDto> crear(@RequestBody @Valid ShipmentInput entrada) {
        String responsable = entrada.responsable() == null || entrada.responsable().isBlank() ? "api" : entrada.responsable();
        Shipment borrador = envios.crearBorrador(responsable);
        Shipment editado = envios.editar(borrador.getId(), conDefectosDeRecogida(entrada.aDatos(), borrador));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto(editado));
    }

    /**
     * editar() sobrescribe siempre pedirRecogida/horaCierreRecogida/lugarRecogida
     * con lo que traiga la entrada, sin mirar el valor anterior (igual que hace
     * con cliente o notas): el formulario web nunca lo nota porque siempre
     * reenvía las tres con las que se rellenó el envío. Un POST o PUT de la API
     * que no las mande no debería perder la hora de cierre por defecto que
     * crearBorrador puso (o la que ya tenía el envío), o "pedirRecogida" se
     * queda true sin hora y problemasParaTarifar lo bloquea con un dato que
     * nadie pidió cambiar. "previo" es el borrador recién creado en el POST y
     * el envío ya existente en el PUT.
     */
    private static DatosEnvio conDefectosDeRecogida(DatosEnvio datos, Shipment previo) {
        if (datos.pedirRecogida() != null && datos.horaCierreRecogida() != null && datos.lugarRecogida() != null) return datos;
        return new DatosEnvio(datos.remitenteId(), datos.destinatarioId(), datos.fechaRecogida(), datos.incoterm(),
                datos.cuentaTransporteId(), datos.cuentaArancelesId(), datos.motivoId(),
                datos.cliente(), datos.temporada(), datos.referencia(), datos.notas(), datos.responsable(),
                datos.pedirRecogida() == null ? previo.isPedirRecogida() : datos.pedirRecogida(),
                datos.horaCierreRecogida() == null ? previo.getHoraCierreRecogida() : datos.horaCierreRecogida(),
                datos.lugarRecogida() == null ? previo.getLugarRecogida() : datos.lugarRecogida(),
                datos.bultos(), datos.lineasAduana());
    }

    /**
     * @Transactional propia: listar() no toca los bultos ni las líneas de
     * aduana de cada envío (la pantalla solo pinta columnas sueltas), así que
     * ShipmentService no las inicializa; aquí sí hace falta para poder montar
     * el ShipmentDto completo, y sin una transacción abierta durante el
     * volcado a DTO la colección lazy revienta con LazyInitializationException
     * en cuanto se ha cerrado la de ShipmentService.listar().
     */
    @Transactional(readOnly = true)
    @GetMapping
    public List<ShipmentDto> listar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                    @RequestParam(required = false) ShipmentStatus estado, @RequestParam(required = false) Long motivoId,
                                    @RequestParam(required = false) String cliente, @RequestParam(required = false) Direction sentido) {
        return envios.listar(new FiltroEnvios(desde, hasta, estado, motivoId, cliente, sentido)).stream()
                .map(e -> ShipmentDto.de(e, List.of())).toList();
    }

    @GetMapping("/{id}")
    public ShipmentDto uno(@PathVariable Long id) { return dto(envios.cargar(id)); }

    @PutMapping("/{id}")
    public ShipmentDto editar(@PathVariable Long id, @RequestBody @Valid ShipmentInput entrada) {
        Shipment actual = envios.cargar(id);
        return dto(envios.editar(id, conDefectosDeRecogida(entrada.aDatos(), actual)));
    }

    @PostMapping("/{id}/quote")
    public ResponseEntity<?> tarifar(@PathVariable Long id) {
        ResultadoTarifa r = envios.tarifar(id);
        if (!r.problemas().isEmpty()) return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorApi("El envío no está listo para tarifar.", r.problemas()));
        if (r.errorTransportista() != null) return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorApi.de(r.errorTransportista()));
        return ResponseEntity.ok(r.envio().getOfertas().stream().map(OfferDto::de).toList());
    }

    @PostMapping("/{id}/confirm")
    public ShipmentDto confirmar(@PathVariable Long id, @RequestBody ConfirmInput entrada) { return dto(envios.confirmar(id, entrada.productCode())); }

    /** Devuelve 200 tanto en CREATED como en ERROR: el estado del envío es la verdad y va en el cuerpo. */
    @PostMapping("/{id}/create")
    public ShipmentDto crear(@PathVariable Long id) { return dto(envios.crear(id)); }

    @PostMapping("/{id}/cancel")
    public ShipmentDto cancelar(@PathVariable Long id) { return dto(envios.cancelar(id)); }

    @GetMapping(value = "/{id}/documents/{tipo}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> documento(@PathVariable Long id, @PathVariable String tipo) {
        Optional<ShipmentDocument> doc = envios.documento(id, tipo);
        return doc.map(d -> ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + tipo + "-" + id + ".pdf\"").body(d.getContenido()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ShipmentDto dto(Shipment e) {
        Shipment cargado = envios.cargar(e.getId());
        return ShipmentDto.de(cargado, cargado.getEstado() == ShipmentStatus.CREATED ? envios.tiposDocumento(cargado.getId()) : List.of());
    }
}
