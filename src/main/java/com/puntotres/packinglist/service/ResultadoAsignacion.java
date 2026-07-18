package com.puntotres.packinglist.service;

import java.util.ArrayList;
import java.util.List;

import com.puntotres.packinglist.model.CajaData;

/**
 * Resultado del cruce cajas-palets: qué cajas quedaron sin palet y avisos
 * detectados (rangos solapados, destinación sin palets...). La asignación
 * en sí se escribe sobre las propias CajaData.
 *
 * TODO(web-ui): igual que EnvioImportado.avisos, esto debe acabar como
 * popup/alerta en la pantalla de revisión, no solo en consola.
 */
public class ResultadoAsignacion {

    private final List<CajaData> cajasSinPalet = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();

    public List<CajaData> getCajasSinPalet() { return cajasSinPalet; }
    public List<String> getAvisos() { return avisos; }

    public boolean todoAsignado() {
        return cajasSinPalet.isEmpty();
    }
}
