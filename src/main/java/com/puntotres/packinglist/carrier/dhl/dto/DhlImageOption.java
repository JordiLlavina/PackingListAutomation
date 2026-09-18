package com.puntotres.packinglist.carrier.dhl.dto;

public record DhlImageOption(String typeCode, String templateName, Boolean isRequested, String invoiceType,
                             String languageCode, Integer numberOfCopies, Boolean fitLabelsToA4) { }
