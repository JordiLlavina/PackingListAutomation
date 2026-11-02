package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.puntotres.packinglist.model.DatosHojaEmbalaje;
import com.puntotres.packinglist.service.embalaje.ArticuloEmbalaje;

/**
 * El pedido cargado para la «Info de embalaje», entre la pantalla de entrada
 * y el formulario.
 *
 * Vive en la sesión y no viaja en el formulario porque el formulario se usa
 * muchas veces seguidas con el mismo pedido —una hoja por artículo, y cada una
 * es un sobre distinto—: leer el excel en cada «Generar hoja» costaría un
 * segundo por hoja y obligaría a mandarlo otra vez desde el navegador. Por lo
 * mismo, el cliente y la temporada que se imprimen salen de aquí y no de
 * campos del formulario: son los del pedido que se cargó, no algo que se teclea.
 *
 * Independiente de los demás «EnCurso»: son flujos que no comparten nada y
 * pueden estar a medias a la vez en la misma sesión sin pisarse.
 */
@Component
@SessionScope
public class EmbalajeEnCurso {

    private String cliente;
    private String nombreCliente;
    private String temporada;
    /** La temporada guardada de la que salió el pedido; null si el excel se subió a mano. */
    private Long temporadaId;
    private final List<ArticuloEmbalaje> articulos = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();
    /** Los valores de una hoja importada, con los que arranca el formulario; null si no hay. */
    private DatosHojaEmbalaje partida;

    /** Sin cliente no hay pedido cargado y el formulario no tiene nada que ofrecer. */
    public boolean estaVacio() {
        return cliente == null;
    }

    public void reiniciar() {
        cliente = null;
        nombreCliente = null;
        temporada = null;
        temporadaId = null;
        articulos.clear();
        avisos.clear();
        partida = null;
    }

    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }

    public String getNombreCliente() { return nombreCliente; }
    public void setNombreCliente(String nombreCliente) { this.nombreCliente = nombreCliente; }

    public String getTemporada() { return temporada; }
    public void setTemporada(String temporada) { this.temporada = temporada; }

    public Long getTemporadaId() { return temporadaId; }
    public void setTemporadaId(Long temporadaId) { this.temporadaId = temporadaId; }

    public List<ArticuloEmbalaje> getArticulos() { return articulos; }
    public List<String> getAvisos() { return avisos; }

    public DatosHojaEmbalaje getPartida() { return partida; }
    public void setPartida(DatosHojaEmbalaje partida) { this.partida = partida; }
}
