package com.puntotres.packinglist.shipping.web;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.puntotres.packinglist.shipping.importacion.PrevisualizacionArticulos;
import com.puntotres.packinglist.shipping.importacion.PrevisualizacionLibreta;

/**
 * La vista previa de una importación entre el "subir" y el "confirmar". Como
 * EnvioEnCurso: por sesión. Un campo por catálogo importable (libreta de
 * contactos, catálogo de artículos), cada uno con su propio flujo de
 * previsualizar → confirmar.
 */
@Component
@SessionScope
public class ImportacionEnCurso {
    private PrevisualizacionLibreta libreta;
    private PrevisualizacionArticulos articulos;

    public PrevisualizacionLibreta getLibreta() { return libreta; }
    public void setLibreta(PrevisualizacionLibreta v) { libreta = v; }

    public PrevisualizacionArticulos getArticulos() { return articulos; }
    public void setArticulos(PrevisualizacionArticulos v) { articulos = v; }
}
