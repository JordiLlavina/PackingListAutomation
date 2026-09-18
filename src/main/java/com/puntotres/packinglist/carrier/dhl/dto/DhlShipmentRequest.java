package com.puntotres.packinglist.carrier.dhl.dto;

import java.util.List;

/** Cuerpo de POST /shipments. */
public record DhlShipmentRequest(String plannedShippingDateAndTime, DhlPickup pickup, String productCode, List<DhlAccount> accounts,
                                 DhlOutputImageProperties outputImageProperties, List<DhlCustomerReference> customerReferences,
                                 DhlCustomerDetails customerDetails, DhlContent content) { }
