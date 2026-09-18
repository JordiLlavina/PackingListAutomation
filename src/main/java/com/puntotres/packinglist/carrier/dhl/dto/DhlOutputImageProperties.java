package com.puntotres.packinglist.carrier.dhl.dto;

import java.util.List;

public record DhlOutputImageProperties(Integer printerDPI, String encodingFormat, List<DhlImageOption> imageOptions,
                                       Boolean splitTransportAndWaybillDocLabels, Boolean allDocumentsInOneImage) { }
