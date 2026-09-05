package com.puntotres.packinglist.service.taller;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.ReglaClienteTaller;
import com.puntotres.packinglist.config.ReglasTallerProperties;
import com.puntotres.packinglist.service.etiquetas.AmiPedidoExcel;

/**
 * Cuánto pide AMI de cada artículo y para qué destinación, sacado del mismo
 * excel de pedido de la temporada que ya se sube para las etiquetas.
 *
 * Dos columnas que ese fichero traía y nadie leía:
 * <ul>
 * <li><b>Commandé</b>: las unidades pedidas de esa línea.</li>
 * <li>el <b>sufijo del PO</b> ("07704 CH"), que es la destinación. Traducirlo
 * a un nombre lo dice la configuración, no el fichero.</li>
 * </ul>
 *
 * Un sufijo que la configuración no conoce es BLOQUEANTE: sin saber a dónde
 * va ese pedido, cualquier reparto sería un bulto camino del sitio
 * equivocado, y eso no se arregla en la pantalla siguiente. Una referencia
 * que no aparece en el pedido, en cambio, es solo un aviso: el usuario teclea
 * la cantidad y sigue.
 */
@Service
public class ObjetivosPedidoAmi implements ObjetivosPedido {

    public static final String CLIENTE = "AMI";

    private final ReglasTallerProperties reglas;

    public ObjetivosPedidoAmi(ReglasTallerProperties reglas) {
        this.reglas = reglas;
    }

    /**
     * Las normas de AMI. Se resuelven en cada llamada y no en el constructor
     * porque un cliente sin bloque en el yml no es un error de arranque: se
     * queda sin traducción de sufijos y cada pedido acaba como bloqueo, que es
     * un mensaje que el usuario entiende.
     */
    private ReglaClienteTaller reglasDeAmi() {
        return reglas.clienteTaller(CLIENTE).orElseGet(ReglaClienteTaller::new);
    }

    @Override
    public String clienteSoportado() {
        return CLIENTE;
    }

    @Override
    public ResultadoObjetivos objetivosPara(List<LineaTaller> lineas, byte[] excelPedido) {
        ResultadoObjetivos resultado = new ResultadoObjetivos();

        AmiPedidoExcel pedido;
        try {
            pedido = AmiPedidoExcel.desdeBytes(excelPedido);
        } catch (IOException | RuntimeException e) {
            resultado.getAvisos().add("No se ha podido leer el excel de pedido de AMI ("
                    + e.getMessage() + "): las cantidades hay que teclearlas a mano");
            return resultado;
        }
        resultado.getAvisos().addAll(pedido.avisos());

        // Un sufijo sin traducción se avisa UNA vez, no una por línea: en un
        // envío entero saldría decenas de veces y el aviso dejaría de leerse.
        Set<String> sufijosAvisados = new HashSet<>();

        for (LineaTaller linea : lineas) {
            List<AmiPedidoExcel.Comanda> comandas =
                    pedido.comandasDe(linea.referencia(), linea.color(), linea.talla());
            if (comandas.isEmpty()) {
                resultado.getAvisos().add(sinPedido(linea));
                continue;
            }
            for (AmiPedidoExcel.Comanda comanda : comandas) {
                Optional<String> destino = reglasDeAmi().destinoDeSufijo(comanda.poSufijo());
                if (destino.isEmpty()) {
                    if (sufijosAvisados.add(String.valueOf(comanda.poSufijo()))) {
                        resultado.getBloqueos().add(sinDestinacion(comanda));
                    }
                    continue;
                }
                resultado.anadir(linea, new ObjetivoDestino(
                        destino.get(), comanda.cantidad(), comanda.poNumerico()));
            }
        }
        return resultado;
    }

    private static String sinPedido(LineaTaller linea) {
        return "El pedido de AMI no tiene ninguna línea de " + linea.referencia()
                + " color " + linea.color() + talla(linea)
                + ": hay que decir a mano cuánto se envía y a dónde";
    }

    private static String sinDestinacion(AmiPedidoExcel.Comanda comanda) {
        return "El pedido " + comanda.poNumerico() + " lleva el sufijo '"
                + comanda.poSufijo() + "', que no está en las destinaciones conocidas de AMI: "
                + "no se puede saber a dónde va y no se genera nada hasta resolverlo";
    }

    private static String talla(LineaTaller linea) {
        return "U".equals(linea.talla()) ? "" : " talla " + linea.talla();
    }
}
