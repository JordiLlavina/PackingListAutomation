package com.puntotres.packinglist.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.AmiExcelBuilder;
import com.puntotres.packinglist.AmiLayout;
import com.puntotres.packinglist.PackingListData;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

/**
 * Genera los excels AMI de una destinación: uno por cada combinación de
 * referencia (modelo) + color, reutilizando el {@link AmiExcelBuilder}.
 *
 * Las cajas con pesos sin resolver no bloquean la generación: el excel
 * sale con esas celdas vacías y las cajas se devuelven en
 * {@link ExcelGenerado#getCajasPendientes()} para la pantalla de revisión.
 *
 * No usa los palets de la destinación (AMI no los imprime en el excel).
 *
 * El tipo de artículo se reconoce por el prefijo de la referencia: "UBL"
 * es cinturón (plantilla de matriz de tallas); "ULL" (bolso) y "USL"
 * (cartera) comparten la plantilla de bolsos, talla única.
 */
@Service
public class AmiGenerador implements GeneradorPackingListCliente {

    private static final String PREFIJO_CINTURON = "UBL";

    private final AmiExcelBuilder excelBuilder;

    public AmiGenerador(AmiExcelBuilder excelBuilder) {
        this.excelBuilder = excelBuilder;
    }

    @Override
    public TipoPlantilla tipo() {
        return TipoPlantilla.AMI;
    }

    @Override
    public List<ExcelGenerado> generar(DestinoData destino, List<PaletData> palets,
                                       DatosEnvio envio, ClienteConfig cliente) throws IOException {
        Map<String, List<CajaData>> grupos = new LinkedHashMap<>();
        for (CajaData caja : destino.getCajas()) {
            String clave = caja.getReferencia() + "|" + caja.getCodigoColor();
            grupos.computeIfAbsent(clave, c -> new ArrayList<>()).add(caja);
        }

        List<ExcelGenerado> resultado = new ArrayList<>();
        for (List<CajaData> grupo : grupos.values()) {
            List<CajaData> cajas = new ArrayList<>(grupo);
            cajas.sort(Comparator.comparingInt(CajaData::getNumeroCaja));

            String referencia = cajas.get(0).getReferencia();
            String color = cajas.get(0).getCodigoColor();

            List<CajaData> pendientes = cajas.stream()
                    .filter(c -> !c.tienePesosCompletos())
                    .toList();

            boolean esCinturon = referencia.startsWith(PREFIJO_CINTURON);
            AmiLayout layout = esCinturon ? AmiLayout.BELTS : AmiLayout.BAGS;
            PackingListData data = mapearCabecera(destino, envio);
            data.setCajas(esCinturon ? mapearCajasCinturon(cajas) : mapearCajasBolso(cajas));

            byte[] excel = excelBuilder.generar(data, layout);
            resultado.add(new ExcelGenerado(destino.getNombreDestino(), referencia, color,
                    nombreFichero(destino.getNombreDestino(), referencia, color),
                    excel, pendientes));
        }
        return resultado;
    }

    private PackingListData mapearCabecera(DestinoData destino, DatosEnvio envio) {
        PackingListData data = new PackingListData();
        // El nombre de destinación se escribe tal cual llega; el posible
        // mapeo ciudad -> destino AMI queda pendiente de ver imágenes reales.
        data.setDestino(destino.getNombreDestino());
        data.setTemporada(envio.getTemporada());
        data.setNumeroFactura(envio.getNumeroFactura());
        data.setFechaFactura(envio.getFechaFactura());
        data.setFechaEnvio(envio.getFechaEnvio());
        if (envio.getCiudadProveedor() != null && !envio.getCiudadProveedor().isBlank()) {
            data.setCiudadProveedor(envio.getCiudadProveedor());
        }
        if (envio.getPaisProveedor() != null && !envio.getPaisProveedor().isBlank()) {
            data.setPaisProveedor(envio.getPaisProveedor());
        }
        return data;
    }

    /** Bolsos/carteras (talla única): una fila de plantilla por caja física. */
    private List<PackingListData.Caja> mapearCajasBolso(List<CajaData> cajas) {
        List<PackingListData.Caja> filas = new ArrayList<>();
        for (CajaData caja : cajas) {
            PackingListData.Caja fila = new PackingListData.Caja();
            fila.setNumeroCaja(caja.getNumeroCaja());
            fila.setNumeroPedido(caja.getNumeroPedido());
            fila.setReferencia(caja.getReferencia());
            fila.setCodigoColor(caja.getCodigoColor());
            fila.setCantidad(caja.getCantidad());
            fila.setTamanoCaja(caja.getTamanoCaja());
            fila.setPesoNetoKg(caja.getPesoNetoKg());
            fila.setPesoBrutoKg(caja.getPesoBrutoKg());
            filas.add(fila);
        }
        return filas;
    }

    /**
     * Cinturones (matriz de tallas): una caja física puede mezclar tallas,
     * así que las entradas del JSON con el mismo número de caja (una por
     * talla, igual que ya ocurre con los colores) se agregan en UNA fila de
     * plantilla. El peso de la fila es la suma de los pesos de sus tallas
     * (cada entrada aporta el peso de su porción de la caja).
     */
    private List<PackingListData.Caja> mapearCajasCinturon(List<CajaData> cajas) {
        Map<Integer, List<CajaData>> porNumeroCaja = new LinkedHashMap<>();
        for (CajaData caja : cajas) {
            porNumeroCaja.computeIfAbsent(caja.getNumeroCaja(), n -> new ArrayList<>()).add(caja);
        }

        List<PackingListData.Caja> filas = new ArrayList<>();
        for (List<CajaData> entradas : porNumeroCaja.values()) {
            CajaData primera = entradas.get(0);
            PackingListData.Caja fila = new PackingListData.Caja();
            fila.setNumeroCaja(primera.getNumeroCaja());
            fila.setNumeroPedido(primera.getNumeroPedido());
            fila.setReferencia(primera.getReferencia());
            fila.setCodigoColor(primera.getCodigoColor());
            fila.setTamanoCaja(primera.getTamanoCaja());
            fila.setPesoNetoKg(sumarPesos(entradas, CajaData::getPesoNetoKg));
            fila.setPesoBrutoKg(sumarPesos(entradas, CajaData::getPesoBrutoKg));

            Map<String, Integer> cantidadesPorTalla = new LinkedHashMap<>();
            for (CajaData entrada : entradas) {
                if (entrada.getTalla() == null || entrada.getTalla().isBlank()) {
                    throw new IllegalArgumentException("La caja " + entrada.getNumeroCaja()
                            + " de la referencia " + entrada.getReferencia()
                            + " es un cinturón (UBL) pero no trae talla");
                }
                cantidadesPorTalla.merge(entrada.getTalla(), entrada.getCantidad(), Integer::sum);
            }
            fila.setCantidadesPorTalla(cantidadesPorTalla);
            filas.add(fila);
        }
        return filas;
    }

    /** Suma los pesos de las entradas; null si a alguna le falta (no se inventa). */
    private static Double sumarPesos(List<CajaData> entradas, Function<CajaData, Double> extractor) {
        double total = 0;
        for (CajaData entrada : entradas) {
            Double valor = extractor.apply(entrada);
            if (valor == null) {
                return null;
            }
            total += valor;
        }
        return Math.round(total * 100.0) / 100.0;
    }

    private static String nombreFichero(String destino, String referencia, String color) {
        String base = "PKL_" + destino + "_" + referencia + "_" + color + ".xlsx";
        return base.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }
}
