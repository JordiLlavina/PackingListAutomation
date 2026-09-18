package com.puntotres.packinglist.shipping.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.puntotres.packinglist.shipping.model.Incoterm;

/**
 * Lo que el usuario teclea de un envío, con ids en vez de entidades. Lo
 * construyen el formulario web y la API, y ShipmentService lo aplica: así los
 * dos caminos resuelven contactos, cuentas y motivo en un solo sitio.
 */
public record DatosEnvio(Long remitenteId, Long destinatarioId, LocalDate fechaRecogida, Incoterm incoterm,
                         Long cuentaTransporteId, Long cuentaArancelesId, Long motivoId,
                         String cliente, String temporada, String referencia, String notas, String responsable,
                         Boolean pedirRecogida, String horaCierreRecogida, String lugarRecogida,
                         List<DatosBulto> bultos, List<DatosLinea> lineasAduana) {

    public record DatosBulto(Double peso, Double largo, Double ancho, Double alto, String descripcion) { }

    public record DatosLinea(String descripcion, String codigoHs, Integer cantidad, BigDecimal valorUnitario,
                             Double pesoNeto, Double pesoBruto, String paisOrigen, Long articuloId) { }

    public DatosEnvio {
        bultos = bultos == null ? List.of() : List.copyOf(bultos);
        lineasAduana = lineasAduana == null ? List.of() : List.copyOf(lineasAduana);
    }
}
