package com.puntotres.packinglist.shipping.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.puntotres.packinglist.shipping.model.Incoterm;
import com.puntotres.packinglist.shipping.service.DatosEnvio;

/** Cuerpo de POST y PUT /api/shipments. Misma forma que DatosEnvio; existe para que la API no dependa del record del servicio. */
public record ShipmentInput(Long remitenteId, Long destinatarioId, LocalDate fechaRecogida, Incoterm incoterm,
                            Long cuentaTransporteId, Long cuentaArancelesId, Long motivoId,
                            @Size(max = 60) String cliente, @Size(max = 60) String temporada, @Size(max = 35) String referencia,
                            @Size(max = 1000) String notas, @Size(max = 120) String responsable,
                            Boolean pedirRecogida, String horaCierreRecogida, String lugarRecogida,
                            @Valid List<PackageInput> bultos, @Valid List<CustomsLineInput> lineasAduana) {

    public record PackageInput(@PositiveOrZero Double peso, @PositiveOrZero Double largo, @PositiveOrZero Double ancho,
                               @PositiveOrZero Double alto, @Size(max = 70) String descripcion) { }
    public record CustomsLineInput(@Size(max = 255) String descripcion, @Size(max = 20) String codigoHs, @Positive Integer cantidad,
                                   @PositiveOrZero BigDecimal valorUnitario, Double pesoNeto, Double pesoBruto,
                                   @Size(min = 2, max = 2) String paisOrigen, Long articuloId) { }

    public DatosEnvio aDatos() {
        return new DatosEnvio(remitenteId, destinatarioId, fechaRecogida, incoterm, cuentaTransporteId, cuentaArancelesId, motivoId,
                cliente, temporada, referencia, notas, responsable, pedirRecogida, horaCierreRecogida, lugarRecogida,
                bultos == null ? List.of() : bultos.stream().map(b -> new DatosEnvio.DatosBulto(b.peso(), b.largo(), b.ancho(), b.alto(), b.descripcion())).toList(),
                lineasAduana == null ? List.of() : lineasAduana.stream().map(l -> new DatosEnvio.DatosLinea(l.descripcion(), l.codigoHs(), l.cantidad(), l.valorUnitario(), l.pesoNeto(), l.pesoBruto(), l.paisOrigen(), l.articuloId())).toList());
    }
}
