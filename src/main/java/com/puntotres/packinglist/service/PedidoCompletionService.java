package com.puntotres.packinglist.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.model.CajaData;

/**
 * Completa el número de pedido de las cajas de APC: lo que llega de la imagen
 * o del formulario son los tres últimos dígitos, y el número entero vive en el
 * excel de pedido del cliente.
 *
 * Corre UNA sola vez, al importar, y nunca en los recálculos de la pantalla de
 * revisión: volver a ejecutarlo pisaría el pedido que el usuario acabe de
 * corregir a mano, igual que pasaría con {@link PaletAssignmentService}.
 *
 * Sin excel, sin fila que case o con clave ambigua: aviso y se deja el dato
 * como llegó. Un PO inventado sería peor que uno incompleto, que se ve.
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
        Optional<String> completo = pedido.pedidoCompleto(caja.getReferencia(), parcial);
        if (completo.isPresent()) {
            caja.setNumeroPedido(completo.get());
            return;
        }
        String clave = caja.getReferencia() + "|" + parcial;
        if (yaAvisadas.add(clave)) {
            resultado.getAvisos().add("El excel de pedido no tiene ninguna línea de '"
                    + caja.getReferencia() + "' con un pedido acabado en '" + parcial
                    + "': se queda como llegó");
        }
    }
}
