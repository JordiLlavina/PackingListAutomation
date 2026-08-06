package com.puntotres.packinglist.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.model.CajaData;

/**
 * Completa el número de pedido Y la referencia de las cajas de APC: de las
 * hojas manuscritas llegan los tres últimos dígitos del pedido y la
 * referencia como la escribe el operario ("67043", "F63023"), que es un
 * sufijo del Article real del excel de pedido ("PXCBS-F67043"). Cuando la
 * búsqueda por sufijo da UNA sola fila, se estampan los dos datos completos;
 * así el packing list imprime la referencia entera y no el recorte.
 *
 * Corre UNA sola vez, al importar, y nunca en los recálculos de la pantalla de
 * revisión: volver a ejecutarlo pisaría el pedido que el usuario acabe de
 * corregir a mano, igual que pasaría con {@link PaletAssignmentService}.
 *
 * Sin excel, sin fila que case o con varias filas candidatas: aviso y se deja
 * el dato como llegó. Un PO inventado sería peor que uno incompleto, que se ve.
 */
@Service
public class PedidoCompletionService {

    public ResultadoPedidos completar(List<EnvioImportado.DestinoImportado> destinos,
                                      byte[] excelPedido) {
        ResultadoPedidos resultado = new ResultadoPedidos();
        if (excelPedido == null || excelPedido.length == 0) {
            resultado.getAvisos().add("No se ha subido el excel de pedido del cliente: "
                    + "los números de pedido se quedan como llegaron (tres dígitos)");
            return resultado;
        }

        ApcPedidoExcel pedido;
        try {
            pedido = ApcPedidoExcel.desdeBytes(excelPedido);
        } catch (Exception e) {
            resultado.getAvisos().add("No se ha podido leer el excel de pedido del cliente ("
                    + e.getMessage() + "): los números de pedido se quedan como llegaron");
            return resultado;
        }
        resultado.getAvisos().addAll(pedido.avisos());

        // Una misma referencia+parcial aparece en muchas cajas: se avisa una
        // vez por clave, no una por caja, o la revisión se llena de ruido.
        Set<String> yaAvisadas = new LinkedHashSet<>();
        for (EnvioImportado.DestinoImportado destino : destinos) {
            for (CajaData caja : destino.getDestino().getCajas()) {
                completarCaja(caja, pedido, yaAvisadas, resultado);
            }
        }
        return resultado;
    }

    private void completarCaja(CajaData caja, ApcPedidoExcel pedido,
                               Set<String> yaAvisadas, ResultadoPedidos resultado) {
        String parcial = caja.getNumeroPedido();
        if (parcial == null || parcial.isBlank()) {
            return; // sin pedido no hay nada que completar; ya avisa la importación
        }
        List<ApcPedidoExcel.FilaPedido> candidatas =
                pedido.filasPara(caja.getReferencia(), parcial);
        if (candidatas.size() == 1) {
            caja.setNumeroPedido(candidatas.get(0).pedido());
            caja.setReferencia(candidatas.get(0).referencia());
            return;
        }
        String clave = caja.getReferencia() + "|" + parcial;
        if (!yaAvisadas.add(clave)) {
            return;
        }
        if (candidatas.isEmpty()) {
            resultado.getAvisos().add("El excel de pedido no tiene ninguna línea de '"
                    + caja.getReferencia() + "' con un pedido acabado en '" + parcial
                    + "': se queda como llegó");
        } else {
            resultado.getAvisos().add("En el excel de pedido, varias referencias acaban en '"
                    + caja.getReferencia() + "' con un pedido acabado en '" + parcial + "' ("
                    + candidatas.stream().map(ApcPedidoExcel.FilaPedido::referencia)
                            .distinct().collect(Collectors.joining(", "))
                    + "): se queda como llegó, elige la buena en la revisión");
        }
    }
}
