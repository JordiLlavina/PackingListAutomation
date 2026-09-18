package com.puntotres.packinglist.carrier.dhl.dto;

import java.math.BigDecimal;
import java.util.List;

public record DhlContent(List<DhlPackage> packages, Boolean isCustomsDeclarable, BigDecimal declaredValue, String declaredValueCurrency,
                         DhlExportDeclaration exportDeclaration, String description, String incoterm, String unitOfMeasurement) { }
