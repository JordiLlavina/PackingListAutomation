package com.puntotres.packinglist.service.taller;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

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
 *
 * <p><b>Es un bean a propósito, y hay que acordarse de que lo sea.</b>
 * {@code DigestionTallerService} recibe la {@code List<ObjetivosPedido>} que
 * le inyecta Spring, así que sin la anotación esta clase no llega nunca al
 * envío por mucho que sus tests pasen: los tests unitarios la construyen con
 * {@code new}, que es justo lo que no comprueba el cableado. Durante un
 * tiempo faltó, y los envíos de taller de APC caían al camino genérico
 * —objetivo = lo que hubiera llegado, destinación = la que apuntara la hoja—
 * ignorando el Document d'achat entero, sin que nada avisara.
 */
@Service
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
            resultado.avisar("No se ha podido leer el excel de pedido de APC ("
                    + e.getMessage() + "): las cantidades hay que teclearlas a mano");
            return resultado;
        }
        pedido.avisos().forEach(resultado::avisar);

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
                resultado.avisarDe(linea.referencia(),
                        "En el pedido de APC no hay ninguna línea de " + linea.referencia()
                        + " con el código " + linea.code()
                        + ": hay que decir a mano cuánto se envía y a dónde");
                continue;
            }
            if (candidatas.size() > 1) {
                resultado.avisarDe(linea.referencia(),
                        "En el pedido de APC, " + linea.referencia()
                        + " con el código " + linea.code() + " encaja con "
                        + candidatas.size() + " pedidos distintos: no se elige a ciegas, "
                        + "hay que decir a mano cuánto se envía y a dónde");
                continue;
            }
            if (!casaElColor(linea, candidatas.get(0), pedido, resultado)) {
                continue;
            }
            Optional<ApcPedidoExcel.Comanda> comanda =
                    pedido.comandaDe(candidatas.get(0).pedido());
            if (comanda.isEmpty() || comanda.get().destino().isBlank()) {
                resultado.avisarDe(linea.referencia(), "El pedido " + candidatas.get(0).pedido()
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
     * El color de la fila del taller tiene que ser uno de los del pedido.
     *
     * El código de tres dígitos identifica el "Document d'achat", y con eso
     * bastaba mientras el color no se pudiera comparar. Se puede: el taller
     * escribe el mismo código que el pedido ("LAW", "GAU"), así que un color
     * que no es el de ese pedido significa que uno de los dos está mal —en el
     * fichero real, una fila de F67080 con el pedido 701 puesta como LAW
     * cuando ese pedido es GAU—. Dar esa fila por buena empaqueta el artículo
     * con el número y la destinación de <b>otro</b> color, y eso no se ve
     * hasta que el bulto llega al cliente.
     *
     * No casar es lo mismo que no encontrar la fila: se avisa, la fila se
     * queda sin cantidad y el usuario decide. No bloquea porque se arregla
     * escribiendo, y el color bueno lo sabe quien tiene la pieza delante.
     *
     * Se perdona lo tipográfico —mayúsculas y espacios— y que el taller
     * escriba el código con su nombre detrás ("GAU CAMEL"). Lo que no se
     * inventa es una traducción de nombres de color: si el taller escribe
     * solo "CAMEL", no hay con qué compararlo y la fila no casa.
     */
    private static boolean casaElColor(LineaTaller linea, ApcPedidoExcel.FilaPedido candidata,
                                       ApcPedidoExcel pedido, ResultadoObjetivos resultado) {
        Set<String> colores = pedido.coloresDe(candidata.pedido());
        if (colores.isEmpty()) {
            // El fichero no trae columna de color: no hay nada que comprobar.
            return true;
        }
        String delTaller = linea.color().trim().toUpperCase(Locale.ROOT);
        String primeraPalabra = delTaller.split("\\s+")[0];
        if (colores.contains(delTaller) || colores.contains(primeraPalabra)) {
            return true;
        }
        resultado.avisarDe(linea.referencia(), "En la fila " + linea.fila() + ", el pedido "
                + candidata.pedido() + " de " + linea.referencia() + " es del color "
                + String.join(" o ", colores) + " y la hoja del taller dice '" + linea.color()
                + "': no es el mismo artículo, hay que decir a mano cuánto se envía y a dónde");
        return false;
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
