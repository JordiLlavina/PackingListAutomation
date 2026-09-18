package com.puntotres.packinglist.carrier.dhl.dto;

import java.util.List;

/** Respuesta de POST /shipments. */
public record DhlShipmentResponse(String shipmentTrackingNumber, String trackingUrl, String dispatchConfirmationNumber,
                                  List<DhlResponseDocument> documents, List<DhlResponsePackage> packages) { }
