package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Component;

import com.puntotres.packinglist.service.ApcPedidoExcel;

/**
 * El pedido de APC para el corte: referencia "PIEL-MODELO" ("PXCBC-F67008")
 * y color con el código de la columna Couleurs ("LZZ"), porque el pedido de
 * APC no trae el nombre del color.
 *
 * Los avisos del lector del pedido no se trasladan: hablan de claves que la
 * entrada por taller no puede completar, que al corte no le afectan.
 */
@Component
public class ApcCorte implements ClienteCorte {

    @Override
    public String clave() {
        return "APC";
    }

    @Override
    public PedidoCorte leerPedido(byte[] excel) throws IOException {
        List<LineaCorte> lineas = ApcPedidoExcel.desdeBytes(excel).lineasConCantidad().stream()
                .map(linea -> new LineaCorte(linea.referencia(), linea.color(), linea.cantidad()))
                .toList();
        return PedidoCorte.agrupar(lineas, ReferenciaCorte::deApc);
    }
}
