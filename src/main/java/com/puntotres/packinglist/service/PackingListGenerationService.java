package com.puntotres.packinglist.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.AmiExcelBuilder;
import com.puntotres.packinglist.PackingListData;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;

/**
 * Genera los excels AMI de una destinación: uno por cada combinación de
 * referencia (modelo) + color, reutilizando el {@link AmiExcelBuilder}.
 *
 * Las cajas con pesos sin resolver no bloquean la generación: el excel
 * sale con esas celdas vacías y las cajas se devuelven en
 * {@link ExcelGenerado#getCajasPendientes()} para la pantalla de revisión.
 */
@Service
public class PackingListGenerationService {

    private final AmiExcelBuilder excelBuilder;

    public PackingListGenerationService(AmiExcelBuilder excelBuilder) {
        this.excelBuilder = excelBuilder;
    }

    /** Un excel por (referencia, color) con las cajas de la destinación. */
    public List<ExcelGenerado> generarPorModeloYColor(DestinoData destino, DatosEnvio envio)
            throws IOException {

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

            byte[] excel = excelBuilder.generar(mapear(destino, envio, cajas));
            resultado.add(new ExcelGenerado(referencia, color,
                    nombreFichero(destino.getNombreDestino(), referencia, color),
                    excel, pendientes));
        }
        return resultado;
    }

    private PackingListData mapear(DestinoData destino, DatosEnvio envio, List<CajaData> cajas) {
        PackingListData data = new PackingListData();
        // El nombre de destinación se escribe tal cual llega; el posible
        // mapeo ciudad -> destino AMI queda pendiente de ver imágenes reales.
        data.setDestino(destino.getNombreDestino());
        data.setTemporada(envio.getTemporada());
        data.setNumeroFactura(envio.getNumeroFactura());
        data.setFechaFactura(envio.getFechaFactura());
        data.setFechaEnvio(envio.getFechaEnvio());

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
        data.setCajas(filas);
        return data;
    }

    private static String nombreFichero(String destino, String referencia, String color) {
        String base = "PKL_" + destino + "_" + referencia + "_" + color + ".xlsx";
        return base.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }
}
