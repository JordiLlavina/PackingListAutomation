package com.puntotres.packinglist.shipping.api;

import com.puntotres.packinglist.shipping.model.Contact;

public record ContactDto(Long id, String empresa, String persona, String ciudad, String pais, String telefono, String email, String etiqueta) {
    static ContactDto de(Contact c) { return new ContactDto(c.getId(), c.getEmpresa(), c.getPersona(), c.getCiudad(), c.getPais(), c.getTelefono(), c.getEmail(), c.etiqueta()); }
}
