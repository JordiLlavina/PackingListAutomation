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
import com.puntotres.packinglist.model.PaletData;
import com.puntotres.packinglist.service.EnvioImportado;
import com.puntotres.packinglist.service.ExcelGenerado;
import com.puntotres.packinglist.service.etiquetas.AmiEtiquetasExcelBuilder.EtiquetaCaja;
import com.puntotres.packinglist.service.etiquetas.AmiEtiquetasExcelBuilder.EtiquetaPaletAmi;

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
            new CampoEtiquetas("pedido", "excel del pedido de AMI", true);

    private static final Locale ESPANOL = Locale.forLanguageTag("es-ES");

    /** Los bolsos van como talla única en la etiqueta y en el pedido. */
    private static final String TALLA_UNICA = "U";

    /** Tara de palet cuando el JSON no la trae, la misma que usa APC. */
    private static final double TARA_PALET_KG_DEFECTO = 10.0;

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
        pedido.avisos().forEach(aviso ->
                resultado.getDetalle().add(AvisoEtiqueta.deFichero(aviso, null)));
        for (EnvioImportado.DestinoImportado importado : destinos) {
            DestinoData destino = importado.getDestino();
            AmiEtiquetaLayout layout =
                    LAYOUT_POR_DESTINO.get(normalizar(destino.getNombreDestino()));
            if (layout == null) {
                resultado.getDetalle().add(AvisoEtiqueta.deDestino(destino.getNombreDestino(),
                        "sin etiquetas de AMI implementadas", "Se omite"));
                continue;
            }
            resultado.getExcels().add(generarDestino(destino, importado.getPalets(),
                    layout, pedido, envio, resultado.getDetalle()));
        }
        deduplicarAvisos(resultado);
        return resultado;
    }

    /**
     * resolver() se llama una vez por artículo, no por caja: en un cinturón
     * con varias tallas eso son varias llamadas para la misma caja, y tres de
     * sus avisos (referencia no encontrada, PO discrepante) no dependen de la
     * talla, así que salen byte-idénticos una vez por talla. Deduplicar por
     * igualdad exacta conservando el orden de la primera aparición quita ese
     * ruido sin tocar los avisos que sí difieren entre tallas (los de
     * FilaPedido.avisosEan, que llevan la talla o el EAN incrustados en el
     * texto): esos nunca coinciden byte a byte entre tallas distintas, así
     * que el dedup no les afecta.
     */
    private static void deduplicarAvisos(ResultadoEtiquetas resultado) {
        List<AvisoEtiqueta> unicos = resultado.getDetalle().stream().distinct().toList();
        resultado.getDetalle().clear();
        resultado.getDetalle().addAll(unicos);
    }

    private ExcelGenerado generarDestino(DestinoData destino, List<PaletData> palets,
                                         AmiEtiquetaLayout layout,
                                         AmiPedidoExcel pedido, DatosEnvio envio,
                                         List<AvisoEtiqueta> avisos) throws IOException {
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

        List<EtiquetaPaletAmi> etiquetasPalet =
                etiquetasDePalet(cajasFisicas, destino, palets, avisos);

        String nombreFichero = ("Etiquetas_AMI_" + destino.getNombreDestino() + "_"
                + envio.getNumeroFactura() + ".xlsx").replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        byte[] contenido = builder.generar(layout, etiquetas, filasExtra, etiquetasPalet);
        return new ExcelGenerado(destino.getNombreDestino(), nombreFichero,
                contenido, cajasPendientes);
    }

    /**
     * Las etiquetas de palet de la destinación, una por palet y en orden de
     * número de palet.
     *
     * El campo palet es OPCIONAL en el JSON, así que basta con que una caja
     * no lo traiga para que la destinación se quede sin hoja de palets: una
     * hoja a medias se imprime y se pega en bultos reales igual que una
     * completa, y quien la mire asumirá que están todas. Distinto de un peso
     * que falta, que solo deja en blanco su celda.
     *
     * El rango de cajas se lee de las CAJAS, no del rango del PaletData: ese
     * es el del JSON original y se queda viejo en cuanto el usuario corrige
     * un palet en la pantalla de revisión.
     */
    private List<EtiquetaPaletAmi> etiquetasDePalet(List<CajaFisica> cajasFisicas,
                                                    DestinoData destino,
                                                    List<PaletData> palets,
                                                    List<AvisoEtiqueta> avisos) {
        String nombreDestino = destino.getNombreDestino();
        boolean algunaSinPalet = destino.getCajas().stream()
                .anyMatch(caja -> caja.getNumeroPalet() == null);
        if (palets.isEmpty() || algunaSinPalet) {
            avisos.add(AvisoEtiqueta.deDestino(nombreDestino,
                    palets.isEmpty() ? "sin datos de palet" : "hay cajas sin palet asignado",
                    "El excel sale sin hoja de etiquetas de palet"));
            return List.of();
        }
        List<EtiquetaPaletAmi> etiquetas = new ArrayList<>();
        for (PaletData palet : palets.stream()
                .sorted(Comparator.comparingInt(PaletData::getNumeroPalet)).toList()) {
            List<CajaFisica> suyas = cajasFisicas.stream()
                    .filter(caja -> Integer.valueOf(palet.getNumeroPalet())
                            .equals(caja.numeroPalet()))
                    .toList();
            if (suyas.isEmpty()) {
                avisos.add(AvisoEtiqueta.dePalet(nombreDestino, palet.getNumeroPalet(),
                        "sin cajas asignadas", "No se le genera etiqueta"));
                continue;
            }
            Double peso = pesoDelPalet(suyas, palet);
            if (peso == null) {
                avisos.add(AvisoEtiqueta.dePalet(nombreDestino, palet.getNumeroPalet(),
                        "con cajas sin peso", "Etiqueta de palet sin peso"));
            }
            etiquetas.add(new EtiquetaPaletAmi(
                    "Nº " + suyas.stream().mapToInt(CajaFisica::numeroCaja).min().getAsInt()
                            + " à Nº "
                            + suyas.stream().mapToInt(CajaFisica::numeroCaja).max().getAsInt(),
                    peso == null ? null : String.format(ESPANOL, "%.2f Kg", peso)));
        }
        return etiquetas;
    }

    /**
     * Suma de los pesos de las cajas FÍSICAS del palet (uno por caja, el de su
     * línea líder: nunca sumar líneas) más la tara. null si a alguna caja le
     * falta el peso: sumar solo las que lo traen daría un peso más bajo que el
     * real sin que se note.
     */
    private static Double pesoDelPalet(List<CajaFisica> cajas, PaletData palet) {
        double total = 0;
        for (CajaFisica caja : cajas) {
            if (caja.pesoBrutoKg() == null) {
                return null;
            }
            total += caja.pesoBrutoKg();
        }
        return total + (palet.getTara() != null ? palet.getTara() : TARA_PALET_KG_DEFECTO);
    }

    /** Un artículo de la caja con lo que aporta el excel de pedido. */
    private record ArticuloResuelto(ArticuloEtiqueta articulo, String colorCode,
                                    String colorCompleto, String poDelPedido,
                                    String ean13, String ean128) {

        /** Los cuatro textos y el EAN-13 tal como van a la imagen y a la hoja extra. */
        EtiquetaArticulo etiquetaArticulo() {
            return new EtiquetaArticulo(articulo.referencia(),
                    "Size: " + (articulo.talla() == null ? TALLA_UNICA : articulo.talla()),
                    colorCompleto,
                    poDelPedido == null ? null : "Cde: " + poDelPedido,
                    ean13);
        }
    }

    private EtiquetaCaja etiquetaDe(CajaFisica caja, int posicion, int total,
                                    AmiEtiquetaLayout layout, AmiPedidoExcel pedido,
                                    DatosEnvio envio, String nombreDestino,
                                    List<AvisoEtiqueta> avisos, List<CajaData> cajasPendientes,
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
            avisos.add(AvisoEtiqueta.deCaja(nombreDestino, lider.getNumeroCaja(),
                    "Sin número de pedido en la entrada",
                    "Etiqueta sin order number ni código de barras"));
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
        String parcel = posicion + " / " + total;
        for (ArticuloResuelto sobrante : resueltos.subList(1, resueltos.size())) {
            filasExtra.add(new FilaCodigoBarrasExtra(parcel, nombreDestino,
                    sobrante.etiquetaArticulo(), sobrante.ean128()));
        }
        if (resueltos.size() > 1) {
            // El usuario tiene que saber que ese excel trae una hoja más.
            avisos.add(AvisoEtiqueta.deCaja(nombreDestino, lider.getNumeroCaja(),
                    refsColores.size() > 1
                            ? "Mezcla de referencias/colores"
                            : "Lleva varias tallas",
                    "Se generan códigos de barra aparte"));
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
                talla, cantidad, pesoTexto, parcel, orderNumber,
                primero.ean128(), primero.etiquetaArticulo());
    }

    /** La fila del pedido de un artículo, o el color del JSON con aviso. */
    private ArticuloResuelto resolver(ArticuloEtiqueta articulo, AmiEtiquetaLayout layout,
                                      AmiPedidoExcel pedido, String orderNumber,
                                      int numeroCaja, String nombreDestino,
                                      List<AvisoEtiqueta> avisos) {
        // La talla solo entra en la clave de los cinturones: los bolsos van
        // como talla única ("U") en el excel de pedido.
        Optional<AmiPedidoExcel.FilaPedido> fila = pedido.buscar(articulo.referencia(),
                articulo.codigoColor(), articulo.talla(), layout.sufijoPo());
        if (fila.isEmpty()) {
            // No depende de la talla (buscar() ni siquiera filtra por ella
            // en este caso: la referencia falta para todo el PO), así que en
            // un cinturón con varias tallas ausentes este mismo aviso saldría
            // repetido; "Caja X de Y" además de nombreDestino la deja
            // distinguible de la misma referencia fallando en otra caja, y
            // el dedup de generar() colapsa las repeticiones dentro de la
            // misma caja porque son, de verdad, el mismo hecho.
            avisos.add(AvisoEtiqueta.deCaja(nombreDestino, numeroCaja,
                    "Referencia '" + articulo.referencia()
                    + "' no encontrada en el excel de pedido",
                    "El color code sale de la entrada y la etiqueta va sin EAN13 ni EAN128"));
            return new ArticuloResuelto(articulo, articulo.codigoColor(),
                    articulo.codigoColor(), null, null, null);
        }
        for (AvisoEtiqueta aviso : fila.get().avisosEan()) {
            avisos.add(aviso.enCaja(nombreDestino, numeroCaja));
        }
        if (orderNumber != null
                && Long.parseLong(fila.get().orderNumber()) != Long.parseLong(orderNumber)) {
            avisos.add(AvisoEtiqueta.deCaja(nombreDestino, numeroCaja,
                    "el pedido de entrada (" + orderNumber
                    + ") no coincide con el PO del excel de pedido ("
                    + fila.get().orderNumber() + ")",
                    "la etiqueta lleva el de la entrada"));
        }
        return new ArticuloResuelto(articulo, fila.get().colorCode(),
                fila.get().colorCompleto(), fila.get().orderNumber(),
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
