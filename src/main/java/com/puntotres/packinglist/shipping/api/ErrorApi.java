package com.puntotres.packinglist.shipping.api;

import java.util.List;

public record ErrorApi(String mensaje, List<String> detalles) {
    static ErrorApi de(String mensaje) { return new ErrorApi(mensaje, List.of()); }
}
