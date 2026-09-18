package com.puntotres.packinglist.shipping.importacion;

import java.util.List;

/** Recuento de una importación de contactos ya aplicada. */
public record ResultadoImportacion(int altas, int actualizadas, int omitidas, List<String> errores) { }
