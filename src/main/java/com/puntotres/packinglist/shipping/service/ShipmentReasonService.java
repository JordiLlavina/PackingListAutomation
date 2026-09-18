package com.puntotres.packinglist.shipping.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.shipping.model.ExportReasonType;
import com.puntotres.packinglist.shipping.model.ShipmentReason;
import com.puntotres.packinglist.shipping.model.ShipmentReasonRepository;

/** Mantenimiento de los motivos de envío (muestras, producción...) y su equivalencia en DHL. */
@Service
public class ShipmentReasonService {

    private final ShipmentReasonRepository repo;

    public ShipmentReasonService(ShipmentReasonRepository repo) { this.repo = repo; }

    public List<ShipmentReason> todos() { return repo.findAllByOrderByNombreAsc(); }
    public List<ShipmentReason> activos() { return repo.findByActivoTrueOrderByNombreAsc(); }

    @Transactional
    public ShipmentReason guardar(Long id, String nombre, ExportReasonType motivoDhl) {
        if (nombre == null || nombre.isBlank()) throw new DatoInvalidoException("El motivo necesita un nombre.");
        if (motivoDhl == null) throw new DatoInvalidoException("Elige a qué motivo de DHL corresponde.");
        Optional<ShipmentReason> mismo = repo.findByNombreIgnoreCase(nombre.trim());
        if (mismo.isPresent() && (id == null || !id.equals(mismo.get().getId()))) throw new DatoInvalidoException("Ya existe el motivo " + nombre.trim() + ".");
        ShipmentReason motivo = id == null ? null : repo.findById(id).orElse(null);
        if (motivo == null) return repo.save(new ShipmentReason(nombre.trim(), motivoDhl));
        motivo.setNombre(nombre.trim());
        motivo.setMotivoDhl(motivoDhl);
        return repo.save(motivo);
    }

    @Transactional
    public void activar(Long id, boolean activo) {
        repo.findById(id).ifPresent(m -> { m.setActivo(activo); repo.save(m); });
    }
}
