package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.VolcadoErpData;
import com.puntotres.packinglist.service.EnvioImportado;
import com.puntotres.packinglist.service.ExcelGenerado;

/**
 * Estado del asistente entre pantallas (una sesión = un envío en curso).
 *
 * Guarda el envío importado con sus CajaData mutables: la edición de pesos
 * desde la pantalla de revisión y la re-inferencia escriben sobre estos
 * mismos objetos, igual que hacen los servicios de dominio.
 */
@Component
@SessionScope
public class EnvioEnCurso {

    private DatosEnvio cabecera;
    private EnvioImportado importado;
    private final List<String> avisosPalets = new ArrayList<>();
    private final List<String> avisosInferencia = new ArrayList<>();
    private final List<CajaData> cajasSinPalet = new ArrayList<>();
    private final List<ExcelGenerado> excels = new ArrayList<>();
    private VolcadoErpData volcadoErp;
    private final List<ExcelGenerado> etiquetas = new ArrayList<>();
    private final List<String> avisosEtiquetas = new ArrayList<>();

    public boolean estaVacio() {
        return importado == null;
    }

    /** Deja la sesión lista para un envío nuevo. */
    public void reiniciar() {
        cabecera = null;
        importado = null;
        avisosPalets.clear();
        avisosInferencia.clear();
        cajasSinPalet.clear();
        excels.clear();
        volcadoErp = null;
        etiquetas.clear();
        avisosEtiquetas.clear();
    }

    public DatosEnvio getCabecera() { return cabecera; }
    public void setCabecera(DatosEnvio cabecera) { this.cabecera = cabecera; }

    public EnvioImportado getImportado() { return importado; }
    public void setImportado(EnvioImportado importado) { this.importado = importado; }

    public List<String> getAvisosPalets() { return avisosPalets; }
    public List<String> getAvisosInferencia() { return avisosInferencia; }
    public List<CajaData> getCajasSinPalet() { return cajasSinPalet; }
    public List<ExcelGenerado> getExcels() { return excels; }

    public VolcadoErpData getVolcadoErp() { return volcadoErp; }
    public void setVolcadoErp(VolcadoErpData volcadoErp) { this.volcadoErp = volcadoErp; }

    public List<ExcelGenerado> getEtiquetas() { return etiquetas; }
    public List<String> getAvisosEtiquetas() { return avisosEtiquetas; }
}
