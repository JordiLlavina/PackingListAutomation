package com.puntotres.packinglist.carrier.dhl.dto;

/** content en base64. */
public record DhlResponseDocument(String imageFormat, String content, String typeCode) { }
