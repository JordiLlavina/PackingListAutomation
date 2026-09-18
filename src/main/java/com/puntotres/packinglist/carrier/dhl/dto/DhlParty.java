package com.puntotres.packinglist.carrier.dhl.dto;

import java.util.List;

/** typeCode: business | direct_consumer. */
public record DhlParty(DhlPostalAddress postalAddress, DhlContactInformation contactInformation,
                       List<DhlRegistrationNumber> registrationNumbers, String typeCode) { }
