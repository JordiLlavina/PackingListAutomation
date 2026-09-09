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
import com.puntotres.packinglist.model.PaletData;
import com.puntotres.packinglist.service.EnvioImportado;
import com.puntotres.packinglist.service.ExcelGenerado;
import com.puntotres.packinglist.service.etiquetas.ApcEtiquetasExcelBuilder.EtiquetaCajaApc;
import com.puntotres.packinglist.service.etiquetas.ApcEtiquetasExcelBuilder.EtiquetaPaletApc;

/**
 * Etiquetas de caja y palet de APC: cinco destinaciones con plantilla
 * propia (JAPAN, KOREA, USA, WH CROSSLOG y RETAIL); ver ApcEtiquetaLayout.
 * No pide archivos al usuario: los datos estáticos van horneados en cada plantilla,
 * y el Order N° y el Livraison code salen de la propia caja, que ya los trae
 * rellenos del packing list (PedidoCompletionService completa el pedido desde
 * el excel del cliente y ResolutorDestinosPadre estampa el código). Solo se
 * imprime "NOT FOUND" cuando de verdad falten.
 *
 * Una caja física = un numeroCaja; los cinturones (línea con talla, APC no
 * usa el prefijo UBL) agrupan unidades por talla en SIZE/PIECES. El peso es
 * el de la línea líder de cada caja ({@link CajaFisica}) y el del palet, la
 * suma de los de sus cajas más la tara (10 kg si el JSON no la trae).
 */
@Service
public class ApcEtiquetasGenerador implements GeneradorEtiquetasCliente {

    private static final String NO_DISPONIBLE = "NOT FOUND";
    private static final double TARA_PALET_KG_DEFECTO = 10.0;
    private static final Locale ESPANOL = Locale.forLanguageTag("es-ES");

    private final ApcEtiquetasExcelBuilder builder;

    public ApcEtiquetasGenerador(ApcEtiquetasExcelBuilder builder) {
        this.builder = builder;
    }

    @Override
    public String claveCliente() {
        return "APC";
    }

    @Override
    public boolean soportaDestino(String nombreDestino) {
        return ApcEtiquetaLayout.paraDestino(nombreDestino).isPresent();
    }

    @Override
    public List<CampoEtiquetas> camposRequeridos(List<DestinoData> destinos) {
        return List.of();
    }

    @Override
    public ResultadoEtiquetas generar(List<EnvioImportado.DestinoImportado> destinos,
                                      DatosEnvio envio, Map<String, byte[]> archivos)
            throws IOException {
        ResultadoEtiquetas resultado = new ResultadoEtiquetas();
        for (EnvioImportado.DestinoImportado importado : destinos) {
            DestinoData destino = importado.getDestino();
            Optional<ApcEtiquetaLayout> layout =
                    ApcEtiquetaLayout.paraDestino(destino.getNombreDestino());
            if (layout.isEmpty()) {
                resultado.getDetalle().add(AvisoEtiqueta.deDestino(destino.getNombreDestino(),
                        "sin etiquetas de APC implementadas", "Se omite"));
                continue;
            }
            resultado.getExcels().add(generarDestino(destino, importado.getPalets(),
                    layout.get(), envio, resultado.getDetalle()));
        }
        return resultado;
    }

    private ExcelGenerado generarDestino(DestinoData destino, List<PaletData> palets,
                                         ApcEtiquetaLayout layout, DatosEnvio envio,
                                         List<AvisoEtiqueta> avisos) throws IOException {
        // Una caja física por numeroCaja, en orden ascendente.
        List<CajaFisica> cajasFisicas = CajaFisica.agrupar(destino.getCajas().stream()
                .sorted(Comparator.comparingInt(CajaData::getNumeroCaja))
                .toList());

        List<EtiquetaCajaApc> etiquetas = new ArrayList<>();
        List<CajaData> cajasPendientes = new ArrayList<>();
        int posicion = 0;
        int total = cajasFisicas.size();
        for (CajaFisica caja : cajasFisicas) {
            posicion++;
            etiquetas.add(etiquetaDe(caja, posicion, total,
                    destino.getNombreDestino(), avisos, cajasPendientes));
        }

        List<EtiquetaPaletApc> etiquetasPalet =
                etiquetasDePalet(cajasFisicas, destino, palets, avisos);

        if (destino.getCajas().stream().anyMatch(caja -> caja.getNumeroPalet() == null)) {
            avisos.add(AvisoEtiqueta.deDestino(destino.getNombreDestino(),
                    "hay cajas sin palet asignado", "No salen en ninguna etiqueta de palet"));
        }

        String nombreFichero = ("Etiquetas_APC_" + destino.getNombreDestino() + "_"
                + envio.getNumeroFactura() + ".xlsx").replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        byte[] contenido = builder.generar(layout, etiquetas, etiquetasPalet);
        return new ExcelGenerado(destino.getNombreDestino(), nombreFichero,
                contenido, cajasPendientes);
    }

    private EtiquetaCajaApc etiquetaDe(CajaFisica caja, int posicion, int total,
                                       String nombreDestino, List<AvisoEtiqueta> avisos,
                                       List<CajaData> cajasPendientes) {
        List<CajaData> lineas = caja.lineas();
        CajaData lider = caja.lider();

        List<CajaData> propias = lineas.stream()
                .filter(linea -> claveRefColor(lider).equals(claveRefColor(linea)))
                .toList();

        // Cinturón = línea con talla (APC no usa el prefijo UBL). Las
        // unidades se agrupan por talla, tallas en orden numérico.
        Map<String, Integer> unidadesPorTalla = new LinkedHashMap<>();
        propias.stream()
                .filter(linea -> linea.getTalla() != null)
                .sorted(Comparator.comparingInt(ApcEtiquetasGenerador::tallaNumerica))
                .forEach(linea -> unidadesPorTalla.merge(
                        linea.getTalla(), linea.getCantidad(), Integer::sum));
        boolean cinturones = !unidadesPorTalla.isEmpty();

        // Caja mixta de verdad (varias referencias o colores): la etiqueta ya
        // lleva TODOS los artículos en Order N°, Reference y Colour, así que
        // lo único que se queda corto en una caja de cinturones son SIZE y
        // PIECES BY SIZE, que solo caben para un artículo.
        Set<String> refsColores = new LinkedHashSet<>();
        for (CajaData linea : lineas) {
            refsColores.add(claveRefColor(linea));
        }
        if (refsColores.size() > 1 && cinturones) {
            avisos.add(AvisoEtiqueta.deCaja(nombreDestino, lider.getNumeroCaja(),
                    "Mezcla de referencias/colores",
                    "SIZE y PIECES BY SIZE son solo de " + lider.getReferencia()
                    + " " + lider.getCodigoColor()));
        }

        // Un artículo = referencia + color, en el orden del packing list. Los
        // tres campos que lo identifican (Order N°, Reference y Colour) se
        // concatenan con " / " y en el MISMO orden, para poder leer la
        // etiqueta en paralelo: el pedido de la posición n es el de la
        // referencia de la posición n. El Document d'achat de APC es una
        // destinación, un artículo y un color, así que cada artículo de la
        // caja lleva el suyo y quedarse con el de la línea líder ponía en la
        // etiqueta un pedido que no era el de los demás.
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(caja, false);
        String referencia = ArticulosDeCaja.unir(articulos, ArticuloEtiqueta::referencia);
        String colour = ArticulosDeCaja.unir(articulos, ArticuloEtiqueta::codigoColor);
        String orderNumber = ArticulosDeCaja.unirValores(pedidosDe(caja, articulos));

        String size;
        String piezas;
        if (unidadesPorTalla.isEmpty()) {
            // Bolsos: un valor por artículo también en las unidades. SIZE no
            // se concatena: "U / U" no dice nada.
            size = "U";
            piezas = ArticulosDeCaja.unir(articulos, a -> String.valueOf(a.cantidad()));
        } else {
            // Cinturones: las unidades van agrupadas por talla, y solo caben
            // las del artículo líder (ver el aviso de arriba).
            if (unidadesPorTalla.size() == 1) {
                var unica = unidadesPorTalla.entrySet().iterator().next();
                size = unica.getKey();
                piezas = String.valueOf(unica.getValue());
            } else {
                size = String.join("-", unidadesPorTalla.keySet());
                piezas = String.join(",", unidadesPorTalla.entrySet().stream()
                        .map(e -> e.getValue() + "-" + e.getKey()).toList());
            }
        }

        // El peso es de la caja física ENTERA y viene una sola vez, en su
        // línea líder; las demás líneas no aportan peso.
        Double peso = caja.pesoBrutoKg();
        if (peso == null) {
            cajasPendientes.add(lider);
        }
        // El Order N° y el Livraison code los trae ya la caja: el packing list
        // los completó al importar (PedidoCompletionService y
        // ResolutorDestinosPadre). "NOT FOUND" queda solo para cuando falten.
        // El Livraison code sí es uno solo: es de la destinación entera.
        return new EtiquetaCajaApc(orderNumber,
                oNoDisponible(lider.getLivraisonCode()), referencia,
                colour, size, piezas, posicion + " / " + total, kg(peso));
    }

    /**
     * El número de pedido de cada artículo, en el orden en que los devuelve
     * {@link ArticulosDeCaja}. Un artículo sin pedido deja "NOT FOUND" en SU
     * posición, no un hueco: la etiqueta se lee en paralelo y un hueco haría
     * dudar de a qué referencia le falta el pedido.
     */
    private static List<String> pedidosDe(CajaFisica caja, List<ArticuloEtiqueta> articulos) {
        Map<String, String> porArticulo = new LinkedHashMap<>();
        for (CajaData linea : caja.lineas()) {
            if (linea.getNumeroPedido() != null && !linea.getNumeroPedido().isBlank()) {
                porArticulo.putIfAbsent(claveRefColor(linea), linea.getNumeroPedido());
            }
        }
        return articulos.stream()
                .map(articulo -> oNoDisponible(
                        porArticulo.get(articulo.referencia() + "|" + articulo.codigoColor())))
                .toList();
    }

    private static String oNoDisponible(String valor) {
        return (valor == null || valor.isBlank()) ? NO_DISPONIBLE : valor;
    }

    /**
     * Peso del palet: la suma de los pesos de sus cajas FÍSICAS (uno por
     * caja, el de su línea líder) más la tara. En blanco, con aviso, si
     * alguna de esas cajas no trae peso.
     *
     * Una destinación que se manda suelta (cajas con
     * {@link CajaData#SIN_PALET}) no lleva hoja de palets ni aviso: no hay
     * ningún palet que etiquetar, así que no falta nada.
     */
    private List<EtiquetaPaletApc> etiquetasDePalet(List<CajaFisica> cajasFisicas,
                                                    DestinoData destino,
                                                    List<PaletData> palets,
                                                    List<AvisoEtiqueta> avisos) {
        if (palets.isEmpty()) {
            if (!seMandaSuelta(destino)) {
                avisos.add(AvisoEtiqueta.deDestino(destino.getNombreDestino(),
                        "sin palets", "La hoja de etiquetas de palet sale en blanco"));
            }
            return List.of();
        }
        String nombreDestino = destino.getNombreDestino();
        List<EtiquetaPaletApc> etiquetas = new ArrayList<>();
        for (PaletData palet : palets.stream()
                .sorted(Comparator.comparingInt(PaletData::getNumeroPalet)).toList()) {
            int numeroCajas = palet.getCajaFin() - palet.getCajaInicio() + 1;
            Double peso = null;
            boolean completo = true;
            for (CajaFisica caja : cajasFisicas) {
                if (!Integer.valueOf(palet.getNumeroPalet()).equals(caja.numeroPalet())) {
                    continue;
                }
                if (caja.pesoBrutoKg() == null) {
                    completo = false;
                } else {
                    peso = (peso == null ? 0 : peso) + caja.pesoBrutoKg();
                }
            }
            if (!completo || peso == null) {
                avisos.add(AvisoEtiqueta.dePalet(nombreDestino, palet.getNumeroPalet(),
                        "con cajas sin peso", "Etiqueta de palet sin peso"));
                peso = null;
            } else {
                peso += palet.getTara() != null ? palet.getTara() : TARA_PALET_KG_DEFECTO;
            }
            etiquetas.add(new EtiquetaPaletApc(numeroCajas, kg(peso)));
        }
        return etiquetas;
    }

    /**
     * Envío suelto: TODAS las cajas de la destinación llevan
     * {@link CajaData#SIN_PALET}. Basta con que una lleve palet de verdad
     * para que la hoja de palets vuelva a hacer falta y su ausencia sea un
     * aviso.
     */
    private static boolean seMandaSuelta(DestinoData destino) {
        return !destino.getCajas().isEmpty() && destino.getCajas().stream()
                .allMatch(caja -> Integer.valueOf(CajaData.SIN_PALET)
                        .equals(caja.getNumeroPalet()));
    }

    private static String kg(Double peso) {
        return peso == null ? null : String.format(ESPANOL, "%.2f Kg", peso);
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
}
