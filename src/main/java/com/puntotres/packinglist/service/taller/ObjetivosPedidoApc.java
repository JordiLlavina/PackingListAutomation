package com.puntotres.packinglist.service.taller;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.puntotres.packinglist.service.ApcPedidoExcel;

/**
 * Cuánto pide APC de cada artículo y para qué destinación.
 *
 * En APC el reparto no hay que calcularlo: la columna CODE de la hoja del
 * taller trae los tres últimos dígitos de un "Document d'achat", y un
 * Document d'achat es UNA destinación, UN artículo y UN color, con una fila
 * por talla. Así que el código ya dice a dónde va y cuánto se pidió.
 *
 * Eso evita el cruce que no se podría hacer: el color viene en el pedido como
 * código ("LZZ", "CAW") y en la hoja del taller como nombre ("CAMEL"), y
 * emparejarlos exigiría conocer el catálogo de colores del cliente.
 *
 * Una fila sin CODE es bloqueante —sin pedido no hay destinación—, pero un
 * CODE que no aparece en el fichero solo avisa: puede ser un pedido de otra
 * temporada y el usuario lo resuelve tecleando.
 */
public class ObjetivosPedidoApc implements ObjetivosPedido {

    public static final String CLIENTE = "APC";

    @Override
    public String clienteSoportado() {
        return CLIENTE;
    }

    @Override
    public ResultadoObjetivos objetivosPara(List<LineaTaller> lineas, byte[] excelPedido) {
        ResultadoObjetivos resultado = new ResultadoObjetivos();

        ApcPedidoExcel pedido;
        try {
            pedido = ApcPedidoExcel.desdeBytes(excelPedido);
        } catch (IOException | RuntimeException e) {
            resultado.getAvisos().add("No se ha podido leer el excel de pedido de APC ("
                    + e.getMessage() + "): las cantidades hay que teclearlas a mano");
            return resultado;
        }
        resultado.getAvisos().addAll(pedido.avisos());

        for (LineaTaller linea : lineas) {
            if (linea.code() == null || linea.code().isBlank()) {
                resultado.getBloqueos().add("La fila " + linea.fila() + " ("
                        + linea.referencia() + ") no trae CODE: sin él no se sabe a qué pedido "
                        + "ni a qué destinación va, y no se genera nada hasta resolverlo");
                continue;
            }
            List<ApcPedidoExcel.FilaPedido> candidatas =
                    pedido.filasPara(linea.referencia(), linea.code());
            if (candidatas.isEmpty()) {
                resultado.getAvisos().add("En el pedido de APC no hay ninguna línea de "
                        + linea.referencia() + " con el código " + linea.code()
                        + ": hay que decir a mano cuánto se envía y a dónde");
                continue;
            }
            if (candidatas.size() > 1) {
                resultado.getAvisos().add("En el pedido de APC, " + linea.referencia()
                        + " con el código " + linea.code() + " encaja con "
                        + candidatas.size() + " pedidos distintos: no se elige a ciegas, "
                        + "hay que decir a mano cuánto se envía y a dónde");
                continue;
            }
            Optional<ApcPedidoExcel.Comanda> comanda =
                    pedido.comandaDe(candidatas.get(0).pedido());
            if (comanda.isEmpty() || comanda.get().destino().isBlank()) {
                resultado.getAvisos().add("El pedido " + candidatas.get(0).pedido()
                        + " no dice a qué destinación va: hay que decirlo a mano");
                continue;
            }
            resultado.anadir(linea, new ObjetivoDestino(
                    normalizarDestino(comanda.get().destino()),
                    comanda.get().cantidad(),
                    comanda.get().pedido()));
        }
        return resultado;
    }

    /**
     * "Chine franch" en el pedido, "CHINE FRANCH" en la configuración. Sin
     * esto la destinación no encontraría ni su regla de reparto ni su
     * destinación padre, y el envío acabaría bloqueado por un detalle
     * tipográfico.
     */
    private static String normalizarDestino(String destino) {
        return destino.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
