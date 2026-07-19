package com.puntotres.packinglist.model;

import java.util.List;
import com.puntotres.packinglist.service.ExcelGenerado;

public class OutputsGenerados {
    private List<ExcelGenerado> packingLists;
    private VolcadoErpData volcadoErp;
    private Object etiquetas;  // null por ahora

    public OutputsGenerados(List<ExcelGenerado> packingLists, VolcadoErpData volcadoErp) {
        this.packingLists = packingLists;
        this.volcadoErp = volcadoErp;
        this.etiquetas = null;
    }

    // Getters
    public List<ExcelGenerado> getPackingLists() { return packingLists; }
    public VolcadoErpData getVolcadoErp() { return volcadoErp; }
    public Object getEtiquetas() { return etiquetas; }
}
