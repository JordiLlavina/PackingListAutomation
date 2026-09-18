package com.puntotres.packinglist.shipping.carrier;

import java.util.List;

public record CreatedShipment(String numeroGuia, String urlSeguimiento, String numeroConfirmacionRecogida, List<CarrierDocument> documentos) { }
