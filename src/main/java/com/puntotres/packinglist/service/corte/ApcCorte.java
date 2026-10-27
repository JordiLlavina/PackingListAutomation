package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.puntotres.packinglist.service.ApcPedidoExcel;

/**
 * El pedido de APC para el corte: referencia "PIEL-MODELO" ("PXCBC-F67008")
 * y color con el código de la columna Couleurs ("LZZ"). El pedido de APC no
 * trae la descripción del color ("NOIR"): la pone después la Tabla colores
 * Clientes, que es un dato que se guarda y no algo que se lea del pedido.
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
        return PedidoCorte.agrupar(lineas(excel), this::partir);
    }

    /** Solo los códigos: la descripción la pone quien rellena la tabla. */
    @Override
    public Map<String, String> coloresDelPedido(byte[] excel) throws IOException {
        return ClienteCorte.colores(lineas(excel));
    }

    @Override
    public Set<String> pielesDelPedido(byte[] excel) throws IOException {
        return ClienteCorte.pieles(lineas(excel), this::partir);
    }

    private static List<LineaCorte> lineas(byte[] excel) throws IOException {
        return ApcPedidoExcel.desdeBytes(excel).lineasConCantidad().stream()
                .map(linea -> new LineaCorte(linea.referencia(), linea.color(), linea.cantidad(),
                        linea.designacion()))
                .toList();
    }

    @Override
    public ReferenciaCorte partir(String referencia) {
        return ReferenciaCorte.deApc(referencia);
    }

    /**
     * El código de APC son tres letras o cifras delante de la descripción
     * ("LZZ NEGRO", "DAB AMARILLO CLARO"). Tiene que llevar descripción
     * detrás: una palabra suelta de tres letras puede ser el color mismo
     * ("ORO"), y un bicolor ("AMARILLO CLARO") no trae código.
     */
    private static final Pattern COLOR_PRODUCCION = Pattern.compile("([A-Z0-9]{3})\\s+(\\S.*)");

    @Override
    public ColorCorte colorDeProduccion(String texto) {
        return ClienteCorte.colorConPatron(texto, COLOR_PRODUCCION);
    }
}
