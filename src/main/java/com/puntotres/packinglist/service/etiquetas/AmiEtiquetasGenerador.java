package com.puntotres.packinglist.service.etiquetas;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
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
 * color code y para los dos códigos de barras que no salen del JSON: el
 * EAN-13 del artículo y el Code 128 de la columna EAN128, que codifica el
 * producto y el PO juntos y por eso es distinto en cada destinación.
 *
 * Una caja física = un numeroCaja: los cinturones multi-talla llegan como
 * varias CajaData del mismo número y comparten par de etiquetas (SIZE
 * "85-90-95", QUANTITY "4-85,33-95,..."). Como en la etiqueta solo cabe un
 * par de EAN, se usa el de la talla de la <b>línea líder</b>, la misma de la
 * que sale el peso.
 */
@Service
public class AmiEtiquetasGenerador implements GeneradorEtiquetasCliente {

    static final CampoEtiquetas CAMPO_PEDIDO =
            new CampoEtiquetas("pedido", "Introducir excel del pedido de AMI");

    private static final Locale ESPANOL = Locale.forLanguageTag("es-ES");

    /** Los bolsos van como talla única en la etiqueta y en el pedido. */
    private static final String TALLA_UNICA = "U";

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
        // Avisos de nivel de fichero (columnas ausentes) antes que los de caja.
        resultado.getAvisos().addAll(pedido.avisos());
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
        List<FilaCodigoBarrasExtra> filasExtra = new ArrayList<>();
        int posicion = 0;
        int total = cajasFisicas.size();
        for (CajaFisica caja : cajasFisicas) {
            posicion++;
            etiquetas.add(etiquetaDe(caja, posicion, total, layout, pedido, envio,
                    destino.getNombreDestino(), avisos, cajasPendientes, filasExtra));
        }

        String nombreFichero = ("Etiquetas_AMI_" + destino.getNombreDestino() + "_"
                + envio.getNumeroFactura() + ".xlsx").replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        byte[] contenido = builder.generar(layout, etiquetas, filasExtra);
        return new ExcelGenerado(destino.getNombreDestino(), nombreFichero,
                contenido, cajasPendientes);
    }

    /** Un artículo de la caja con lo que aporta el excel de pedido. */
    private record ArticuloResuelto(ArticuloEtiqueta articulo, String colorCode,
                                    String ean13, String ean128) {
    }

    private EtiquetaCaja etiquetaDe(CajaFisica caja, int posicion, int total,
                                    AmiEtiquetaLayout layout, AmiPedidoExcel pedido,
                                    DatosEnvio envio, String nombreDestino,
                                    List<String> avisos, List<CajaData> cajasPendientes,
                                    List<FilaCodigoBarrasExtra> filasExtra) {
        List<CajaData> lineas = caja.lineas();
        CajaData lider = caja.lider();
        boolean cinturones = lider.esCinturon();

        // Varias referencias/colores (bolsos) o varias tallas (cinturones):
        // solo sirve para elegir el texto del aviso de la hoja extra.
        Set<String> refsColores = new LinkedHashSet<>();
        for (CajaData linea : lineas) {
            refsColores.add(claveRefColor(linea));
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

        // Un artículo por referencia+color (bolsos) o +talla (cinturones), y
        // cada uno con su fila del pedido: su color code y sus dos EAN.
        List<ArticuloResuelto> resueltos = ArticulosDeCaja.de(caja, cinturones).stream()
                .map(articulo -> resolver(articulo, layout, pedido, orderNumber,
                        lider.getNumeroCaja(), nombreDestino, avisos))
                .toList();
        ArticuloResuelto primero = resueltos.get(0);

        // Solo el primer artículo conserva sus códigos de barras en la
        // etiqueta; los demás se imprimen en la hoja "CODIGOS BARRAS EXTRA".
        for (ArticuloResuelto sobrante : resueltos.subList(1, resueltos.size())) {
            filasExtra.add(new FilaCodigoBarrasExtra(lider.getNumeroCaja(),
                    sobrante.articulo().referencia(), sobrante.colorCode(),
                    sobrante.articulo().talla() == null
                            ? TALLA_UNICA : sobrante.articulo().talla(),
                    String.valueOf(sobrante.articulo().cantidad()),
                    sobrante.ean13(), sobrante.ean128()));
        }
        if (resueltos.size() > 1) {
            // El usuario tiene que saber que ese excel trae una hoja más.
            avisos.add("La caja " + lider.getNumeroCaja() + " de " + nombreDestino
                    + (refsColores.size() > 1
                            ? " mezcla varias referencias/colores"
                            : " lleva varias tallas")
                    + ": se han generado códigos de barra aparte para imprimir");
        }

        String referencia;
        String colorCode;
        String talla;
        String cantidad;
        if (cinturones) {
            // Los cinturones no cambian: SIZE con las tallas ordenadas y
            // QUANTITY con los pares cantidad-talla de la referencia líder.
            List<CajaData> ordenadas = lineas.stream()
                    .filter(linea -> claveRefColor(lider).equals(claveRefColor(linea)))
                    .sorted(Comparator.comparingInt(AmiEtiquetasGenerador::tallaNumerica))
                    .toList();
            referencia = lider.getReferencia();
            colorCode = primero.colorCode();
            talla = String.join("-", ordenadas.stream().map(CajaData::getTalla).toList());
            // Los pares cantidad-talla solo tienen sentido con varias tallas;
            // con una sola, QUANTITY es la cantidad a secas (regla general).
            cantidad = ordenadas.size() == 1
                    ? String.valueOf(ordenadas.get(0).getCantidad())
                    : String.join(",", ordenadas.stream()
                            .map(linea -> linea.getCantidad() + "-" + linea.getTalla()).toList());
        } else {
            // Bolsos: un valor por artículo en cada campo, en el orden del
            // packing list. La talla no se concatena: "U / U" no dice nada.
            List<ArticuloEtiqueta> articulos = resueltos.stream()
                    .map(ArticuloResuelto::articulo).toList();
            referencia = ArticulosDeCaja.unir(articulos, ArticuloEtiqueta::referencia);
            // El color code no sale del artículo sino de su fila del pedido,
            // así que este no puede pasar por ArticulosDeCaja.unir (toma
            // ArticuloEtiqueta, no String); unirValores comparte la misma
            // regla de huecos y celda en blanco con un valor ya extraído.
            colorCode = ArticulosDeCaja.unirValores(
                    resueltos.stream().map(ArticuloResuelto::colorCode).toList());
            talla = TALLA_UNICA;
            cantidad = ArticulosDeCaja.unir(articulos, a -> String.valueOf(a.cantidad()));
        }

        // El peso es de la caja física ENTERA y viene una sola vez, en su
        // línea líder; las demás líneas no aportan peso.
        Double peso = caja.pesoBrutoKg();
        if (peso == null) {
            cajasPendientes.add(lider);
        }
        String pesoTexto = peso == null
                ? null : String.format(ESPANOL, "%.2f KGS", peso);
        return new EtiquetaCaja(envio.getTemporada(), referencia, colorCode,
                talla, cantidad, pesoTexto, posicion + " / " + total, orderNumber,
                primero.ean13(), primero.ean128());
    }

    /** La fila del pedido de un artículo, o el color del JSON con aviso. */
    private ArticuloResuelto resolver(ArticuloEtiqueta articulo, AmiEtiquetaLayout layout,
                                      AmiPedidoExcel pedido, String orderNumber,
                                      int numeroCaja, String nombreDestino,
                                      List<String> avisos) {
        // La talla solo entra en la clave de los cinturones: los bolsos van
        // como talla única ("U") en el excel de pedido.
        Optional<AmiPedidoExcel.FilaPedido> fila = pedido.buscar(articulo.referencia(),
                articulo.codigoColor(), articulo.talla(), layout.sufijoPo());
        if (fila.isEmpty()) {
            avisos.add("Referencia '" + articulo.referencia() + "' (" + nombreDestino
                    + ") no encontrada en el excel de pedido: el color code sale del JSON"
                    + " y la etiqueta va sin EAN13 ni EAN128");
            return new ArticuloResuelto(articulo, articulo.codigoColor(), null, null);
        }
        for (String aviso : fila.get().avisosEan()) {
            avisos.add("Caja " + numeroCaja + " de " + nombreDestino + ": " + aviso);
        }
        if (orderNumber != null
                && Long.parseLong(fila.get().orderNumber()) != Long.parseLong(orderNumber)) {
            avisos.add("Caja " + numeroCaja + " de " + nombreDestino
                    + ": el pedido del JSON (" + orderNumber
                    + ") no coincide con el PO del excel de pedido ("
                    + fila.get().orderNumber() + "); la etiqueta lleva el del JSON");
        }
        return new ArticuloResuelto(articulo, fila.get().colorCode(),
                fila.get().ean13(), fila.get().ean128());
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
