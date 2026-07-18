package com.puntotres.packinglist.service;

import java.util.ArrayList;
import java.util.List;

import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

/**
 * Resultado de traducir el JSON de un envío al modelo de dominio:
 * cada destinación con sus cajas ya expandidas y sus palets, más los
 * avisos de validación detectados durante la importación.
 *
 * TODO(web-ui): cuando exista la pantalla de revisión, mostrar estos
 * avisos como popup/alerta al usuario (no solo log) antes de dejarle
 * generar los excels. Hoy Main.java solo los imprime por consola.
 */
public class EnvioImportado {

    private final List<DestinoImportado> destinos = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();

    public List<DestinoImportado> getDestinos() { return destinos; }
    public List<String> getAvisos() { return avisos; }

    public static class DestinoImportado {

        private final DestinoData destino;
        private final List<PaletData> palets;

        public DestinoImportado(DestinoData destino, List<PaletData> palets) {
            this.destino = destino;
            this.palets = palets;
        }

        public DestinoData getDestino() { return destino; }
        public List<PaletData> getPalets() { return palets; }
    }
}
