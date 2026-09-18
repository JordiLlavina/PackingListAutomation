package com.puntotres.packinglist.shipping.service;

import java.util.List;

import com.puntotres.packinglist.shipping.model.ContactType;
import com.puntotres.packinglist.shipping.model.Incoterm;

/** Lo que se teclea o se importa de un contacto. Ids para las cuentas por defecto. */
public record DatosContacto(String empresa, String persona, String direccion1, String direccion2, String direccion3,
                            String codigoPostal, String ciudad, String provincia, String provinciaCodigo, String pais,
                            String telefono, String email, ContactType tipo, boolean residencial,
                            String nifVat, String eori, String identificadorMarroqui, String notas,
                            Incoterm incotermPorDefecto, Long cuentaTransportePorDefectoId, Long cuentaArancelesPorDefectoId,
                            List<String> alias) {
    public DatosContacto {
        alias = alias == null ? List.of() : List.copyOf(alias);
        tipo = tipo == null ? ContactType.BUSINESS : tipo;
    }
}
