package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Component;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.service.etiquetas.AmiPedidoExcel;

/**
 * El pedido de AMI para el corte: referencia "MODELO.PIEL" ("ULL712.AL0103")
 * y color con su nombre ("BLACK").
 *
 * Los cinturones (UBL) no salen: se cortan con otro proceso. Se reconocen con
 * el mismo {@link CajaData#esCinturon} que usa el resto del programa, sin
 * aviso, porque saldría en todos los pedidos.
 *
 * Los avisos del lector del pedido no se trasladan: hablan de las columnas de
 * EAN y de "Made in", que al corte no le afectan.
 */
@Component
public class AmiCorte implements ClienteCorte {

    @Override
    public String clave() {
        return "AMI";
    }

    @Override
    public PedidoCorte leerPedido(byte[] excel) throws IOException {
        List<LineaCorte> lineas = AmiPedidoExcel.desdeBytes(excel).lineasConCantidad().stream()
                .filter(linea -> !CajaData.esCinturon(ReferenciaCorte.normalizar(linea.referencia())))
                .map(linea -> new LineaCorte(linea.referencia(), linea.color(), linea.cantidad()))
                .toList();
        return PedidoCorte.agrupar(lineas, this::partir);
    }

    @Override
    public ReferenciaCorte partir(String referencia) {
        return ReferenciaCorte.deAmi(referencia);
    }
}
