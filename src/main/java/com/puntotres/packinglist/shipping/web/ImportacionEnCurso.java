package com.puntotres.packinglist.shipping.web;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.puntotres.packinglist.shipping.importacion.PrevisualizacionLibreta;

/**
 * La vista previa de una importación entre el "subir" y el "confirmar". Como
 * EnvioEnCurso: por sesión.
 *
 * Solo lleva la libreta de contactos (Task 9); la importación del catálogo de
 * artículos (Task 10) añade aquí su propio campo cuando exista
 * PrevisualizacionArticulos.
 */
@Component
@SessionScope
public class ImportacionEnCurso {
    private PrevisualizacionLibreta libreta;

    public PrevisualizacionLibreta getLibreta() { return libreta; }
    public void setLibreta(PrevisualizacionLibreta v) { libreta = v; }
}
