package com.puntotres.packinglist.model;

import java.util.List;
import com.puntotres.packinglist.service.ExcelGenerado;

public class OutputsGenerados {
    private List<ExcelGenerado> packingLists;
    private VoltadoErpData volcadoErp;
    private Object etiquetas;  // null por ahora

    public OutputsGenerados(List<ExcelGenerado> packingLists, VoltadoErpData volcadoErp) {
        this.packingLists = packingLists;
        this.volcadoErp = volcadoErp;
        this.etiquetas = null;
    }

    // Getters
    public List<ExcelGenerado> getPackingLists() { return packingLists; }
    public VoltadoErpData getVolcadoErp() { return volcadoErp; }
    public Object getEtiquetas() { return etiquetas; }
}
