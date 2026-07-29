package com.puntotres.packinglist.service.etiquetas;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.CajaFisica;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.service.EnvioImportado;
import com.puntotres.packinglist.service.ExcelGenerado;
import com.puntotres.packinglist.service.etiquetas.AmiEtiquetasExcelBuilder.EtiquetaCaja;

/**
 * Etiquetas de caja de AMI: tres destinaciones (China, Japan, France; el
 * JSON suele llamar PARIS a la de France). Necesita del usuario el excel
 * del pedido completo de la temporada (ej. "AMI EAN H26.xlsx") para el
 * order number (columna PO, que distingue destinación por sufijo) y el
 * color code. Una caja física = un numeroCaja: los cinturones multi-talla
 * llegan como varias CajaData del mismo número y comparten par de
 * etiquetas (SIZE "85-90-95", QUANTITY "4-85,33-95,...").
 */
@Service
public class AmiEtiquetasGenerador implements GeneradorEtiquetasCliente {

    static final CampoEtiquetas CAMPO_PEDIDO =
            new CampoEtiquetas("pedido", "Introducir excel del pedido de AMI");

    private static final Locale ESPANOL = Locale.forLanguageTag("es-ES");

    private static final Map<String, AmiEtiquetaLayout> LAYOUT_POR_DESTINO = Map.of(
            "CHINA", AmiEtiquetaLayout.CHINA,
            "JAPAN", AmiEtiquetaLayout.JAPAN,
            "FRANCE", AmiEtiquetaLayout.FRANCE,
            "PARIS", AmiEtiquetaLayout.FRANCE);

    private final AmiEtiquetasExcelBuilder builder;

    public AmiEtiquetasGenerador(AmiEtiquetasExcelBuilder builder) {
        this.builder = builder;
    }

    @Override
    public String claveCliente() {
        return "AMI";
    }

    @Override
    public boolean soportaDestino(String nombreDestino) {
        return nombreDestino != null
                && LAYOUT_POR_DESTINO.containsKey(normalizar(nombreDestino));
    }

    @Override
    public List<CampoEtiquetas> camposRequeridos(List<DestinoData> destinos) {
        boolean alguno = destinos.stream()
                .anyMatch(destino -> soportaDestino(destino.getNombreDestino()));
        return alguno ? List.of(CAMPO_PEDIDO) : List.of();
    }

    @Override
    public ResultadoEtiquetas generar(List<EnvioImportado.DestinoImportado> destinos,
                                      DatosEnvio envio, Map<String, byte[]> archivos)
            throws IOException {
        byte[] contenidoPedido = archivos.get(CAMPO_PEDIDO.nombre());
        if (contenidoPedido == null) {
            throw new IllegalArgumentException("Falta el excel del pedido de AMI");
        }
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(contenidoPedido);

        ResultadoEtiquetas resultado = new ResultadoEtiquetas();
        for (EnvioImportado.DestinoImportado importado : destinos) {
            DestinoData destino = importado.getDestino();
            AmiEtiquetaLayout layout =
                    LAYOUT_POR_DESTINO.get(normalizar(destino.getNombreDestino()));
            if (layout == null) {
                resultado.getAvisos().add("Destinación '" + destino.getNombreDestino()
                        + "' sin etiquetas de AMI implementadas: se omite");
                continue;
            }
            resultado.getExcels().add(
                    generarDestino(destino, layout, pedido, envio, resultado.getAvisos()));
        }
        return resultado;
    }

    private ExcelGenerado generarDestino(DestinoData destino, AmiEtiquetaLayout layout,
                                         AmiPedidoExcel pedido, DatosEnvio envio,
                                         List<String> avisos) throws IOException {
        // Una caja física por numeroCaja, en orden ascendente.
        List<CajaFisica> cajasFisicas = CajaFisica.agrupar(destino.getCajas().stream()
                .sorted(Comparator.comparingInt(CajaData::getNumeroCaja))
                .toList());

        List<EtiquetaCaja> etiquetas = new ArrayList<>();
        List<CajaData> cajasPendientes = new ArrayList<>();
        int posicion = 0;
        int total = cajasFisicas.size();
        for (CajaFisica caja : cajasFisicas) {
            posicion++;
            etiquetas.add(etiquetaDe(caja, posicion, total, layout, pedido, envio,
                    destino.getNombreDestino(), avisos, cajasPendientes));
        }

        String nombreFichero = ("Etiquetas_AMI_" + destino.getNombreDestino() + "_"
                + envio.getNumeroFactura() + ".xlsx").replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        byte[] contenido = builder.generar(layout, etiquetas);
        return new ExcelGenerado(destino.getNombreDestino(), nombreFichero,
                contenido, cajasPendientes);
    }

    private EtiquetaCaja etiquetaDe(CajaFisica caja, int posicion, int total,
                                    AmiEtiquetaLayout layout, AmiPedidoExcel pedido,
                                    DatosEnvio envio, String nombreDestino,
                                    List<String> avisos, List<CajaData> cajasPendientes) {
        List<CajaData> lineas = caja.lineas();
        CajaData lider = caja.lider();

        // Caja mixta de verdad (varias referencias o colores): el spec no la
        // contempla para etiquetas; se etiqueta con la primera y se avisa.
        Set<String> refsColores = new LinkedHashSet<>();
        for (CajaData linea : lineas) {
            refsColores.add(claveRefColor(linea));
        }
        if (refsColores.size() > 1) {
            avisos.add("La caja " + lider.getNumeroCaja() + " de " + nombreDestino
                    + " mezcla varias referencias/colores: la etiqueta lleva "
                    + lider.getReferencia() + " " + lider.getCodigoColor());
        }

        String talla;
        String cantidad;
        if (lider.esCinturon()) {
            List<CajaData> ordenadas = lineas.stream()
                    .filter(linea -> claveRefColor(lider).equals(claveRefColor(linea)))
                    .sorted(Comparator.comparingInt(AmiEtiquetasGenerador::tallaNumerica))
                    .toList();
            talla = String.join("-", ordenadas.stream()
                    .map(CajaData::getTalla).toList());
            // Los pares cantidad-talla solo tienen sentido con varias tallas;
            // con una sola, QUANTITY es la cantidad a secas (regla general).
            cantidad = ordenadas.size() == 1
                    ? String.valueOf(ordenadas.get(0).getCantidad())
                    : String.join(",", ordenadas.stream()
                            .map(linea -> linea.getCantidad() + "-" + linea.getTalla()).toList());
        } else {
            talla = "U";
            cantidad = String.valueOf(lineas.stream().mapToInt(CajaData::getCantidad).sum());
        }

        // El peso es de la caja física ENTERA y viene una sola vez, en su
        // línea líder; las demás líneas no aportan peso.
        Double peso = caja.pesoBrutoKg();
        if (peso == null) {
            cajasPendientes.add(lider);
        }

        // El ORDER NUMBER (celda y código de barras) sale SIEMPRE del campo
        // 'pedido' del JSON: es el dato por referencia del packing list. El
        // excel de pedido aporta el color code y sirve de contraste del PO.
        String pedidoJson = soloDigitos(lider.getNumeroPedido());
        String orderNumber = pedidoJson.isBlank()
                ? null : String.format("%05d", Long.parseLong(pedidoJson));
        if (orderNumber == null) {
            avisos.add("Caja " + lider.getNumeroCaja() + " de " + nombreDestino
                    + " sin campo 'pedido' en el JSON: etiqueta sin order number "
                    + "ni código de barras");
        }

        Optional<AmiPedidoExcel.FilaPedido> fila = pedido.buscar(lider.getReferencia(),
                lider.getCodigoColor(), lider.esCinturon() ? lider.getTalla() : null,
                layout.sufijoPo());
        String colorCode;
        if (fila.isPresent()) {
            colorCode = fila.get().colorCode();
            if (orderNumber != null
                    && Long.parseLong(fila.get().orderNumber()) != Long.parseLong(orderNumber)) {
                avisos.add("Caja " + lider.getNumeroCaja() + " de " + nombreDestino
                        + ": el pedido del JSON (" + orderNumber
                        + ") no coincide con el PO del excel de pedido ("
                        + fila.get().orderNumber() + "); la etiqueta lleva el del JSON");
            }
        } else {
            avisos.add("Referencia '" + lider.getReferencia() + "' (" + nombreDestino
                    + ") no encontrada en el excel de pedido: el color code sale del JSON");
            colorCode = lider.getCodigoColor();
        }

        String pesoTexto = peso == null
                ? null : String.format(ESPANOL, "%.2f KGS", peso);
        return new EtiquetaCaja(envio.getTemporada(), lider.getReferencia(), colorCode,
                talla, cantidad, pesoTexto, posicion + " / " + total, orderNumber);
    }

    private static String claveRefColor(CajaData caja) {
        return caja.getReferencia() + "|" + caja.getCodigoColor();
    }

    private static int tallaNumerica(CajaData caja) {
        try {
            return Integer.parseInt(caja.getTalla().trim());
        } catch (RuntimeException e) {
            return Integer.MAX_VALUE; // tallas raras al final, sin romper
        }
    }

    private static String soloDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("[^0-9]", "");
    }

    private static String normalizar(String nombre) {
        return nombre.trim().toUpperCase(Locale.ROOT);
    }
}
