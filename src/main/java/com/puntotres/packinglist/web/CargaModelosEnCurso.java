package com.puntotres.packinglist.web;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.puntotres.packinglist.service.cargamodelos.LoteCarga;

import jakarta.annotation.PreDestroy;

/**
 * La carga de modelos en marcha, en la sesión del usuario: el lote que se
 * está revisando en la batería. Sus fotos y fichas técnicas viven en un
 * directorio temporal (con el prefijo de los del corte, así que los que se
 * queden huérfanos los barre {@link LimpiezaDirectoriosCorte} al arrancar),
 * que se borra al empezar otra carga, al descartarla y al caducar la sesión.
 */
@Component
@SessionScope
public class CargaModelosEnCurso {

    private LoteCarga lote;

    public synchronized Optional<LoteCarga> lote() {
        return Optional.ofNullable(lote);
    }

    /** Sustituye la carga que hubiera: su conversión se cancela y su directorio se borra. */
    public synchronized void empezar(LoteCarga nuevo) {
        soltar();
        lote = nuevo;
    }

    public synchronized void descartar() {
        soltar();
    }

    @PreDestroy
    public void alCerrarLaSesion() {
        descartar();
    }

    private void soltar() {
        if (lote != null) {
            lote.cancelar();
            CorteEnCurso.borrar(lote.directorio());
            lote = null;
        }
    }
}
