package com.puntotres.packinglist.carrier.dhl.dto;

import java.util.List;

public record DhlExportDeclaration(List<DhlLineItem> lineItems, DhlInvoice invoice, String exportReasonType, String placeOfIncoterm) { }
