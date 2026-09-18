package com.puntotres.packinglist.carrier.dhl.dto;

import java.util.List;

public record DhlPackage(Double weight, DhlDimensions dimensions, List<DhlCustomerReference> customerReferences, String description) { }
