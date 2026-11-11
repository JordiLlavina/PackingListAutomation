package com.puntotres.packinglist.modelos.api.dto;

import java.time.LocalDateTime;

import com.puntotres.packinglist.modelos.persistence.DestinoAplicacion;

/**
 * Dónde se aplica un aviso del taller y si ya está aplicado ahí.
 *
 * @param texto    cómo se llama el destino en pantalla («ICSUITE», «Excel de escandallos»)
 * @param hechoEn  cuándo se aplicó; null si todavía no
 * @param hechoPor el nombre de quien lo aplicó; null si todavía no
 */
public record DestinoEstado(DestinoAplicacion destino, String texto, boolean aplica, LocalDateTime hechoEn,
                            String hechoPor) {
}
