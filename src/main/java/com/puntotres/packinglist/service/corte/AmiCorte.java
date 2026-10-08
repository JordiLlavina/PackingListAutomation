package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.service.etiquetas.AmiPedidoExcel;

/**
 * El pedido de AMI para el corte: referencia "MODELO.PIEL" ("ULL712.AL0103")
 * y color con su código y su nombre, los dos del pedido ("001" de COLORIS y
 * "BLACK" del Libellé coloris): AMI no necesita la Tabla colores Clientes.
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
        List<LineaCorte> lineas = lineas(excel).stream()
                .filter(linea -> !CajaData.esCinturon(ReferenciaCorte.normalizar(linea.referencia())))
                .toList();
        return PedidoCorte.agrupar(lineas, this::partir);
    }

    /** Con los de los cinturones, que el corte deja fuera pero la tabla de colores no. */
    @Override
    public Map<String, String> coloresDelPedido(byte[] excel) throws IOException {
        return ClienteCorte.colores(lineas(excel));
    }

    @Override
    public Set<String> pielesDelPedido(byte[] excel) throws IOException {
        return ClienteCorte.pieles(lineas(excel), this::partir);
    }

    private static List<LineaCorte> lineas(byte[] excel) throws IOException {
        return AmiPedidoExcel.desdeBytes(excel).lineasConCantidad().stream()
                .map(linea -> new LineaCorte(linea.referencia(), linea.codigoColor(),
                        linea.nombreColor(), linea.cantidad(), ""))
                .toList();
    }

    @Override
    public ReferenciaCorte partir(String referencia) {
        return ReferenciaCorte.deAmi(referencia);
    }
}
