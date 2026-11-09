package com.puntotres.packinglist.modelos.api.dto;

import java.time.LocalDateTime;

/** Un nombre que ha tenido el modelo (código o nombre de prototipo); {@code hasta} nulo es el de hoy. */
public record CodigoHistorico(String codigo, LocalDateTime desde, LocalDateTime hasta, String motivo) {
}
