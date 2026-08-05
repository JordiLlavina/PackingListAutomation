package com.puntotres.packinglist.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.AmiExcelBuilder;
import com.puntotres.packinglist.AmiLayout;
import com.puntotres.packinglist.PackingListData;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.CajaFisica;
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
 * Un mismo bulto puede repartirse entre VARIOS de estos excels (dos colores
 * o dos referencias en la misma caja). El peso es del bulto, así que sale
 * repetido en todos ellos: cada packing list describe ese cartón entero y
 * uno sin peso no sirve para expedir. Dentro de cada excel, en cambio, se
 * escribe una sola vez (ver {@link #mapearCajasBolso}).
 *
 * No usa los palets de la destinación (AMI no los imprime en el excel).
 *
 * El tipo de artículo se reconoce por el prefijo de la referencia: "UBL"
 * es cinturón (plantilla de matriz de tallas); "ULL" (bolso) y "USL"
 * (cartera) comparten la plantilla de bolsos, talla única.
 */
@Service
public class AmiGenerador implements GeneradorPackingListCliente {

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
        // El peso es del BULTO y lo lleva su línea líder dentro de la
        // destinación ENTERA, así que hay que resolverlo antes de partir en
        // grupos: si una caja la comparten dos artículos, su líder cae en el
        // excel de uno de ellos y desde el grupo del otro no se ve.
        Map<Integer, CajaFisica> bultos = new LinkedHashMap<>();
        for (CajaFisica bulto : CajaFisica.agrupar(destino.getCajas())) {
            bultos.put(bulto.numeroCaja(), bulto);
        }

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

            // Pendiente = bulto que sale con las celdas de peso vacías en ESTE
            // excel. Se mira el bulto de la destinación, no el del grupo: la
            // caja compartida ya no está pendiente aquí solo porque su peso lo
            // trajera la línea de otro artículo.
            List<CajaData> pendientes = cajas.stream()
                    .map(CajaData::getNumeroCaja)
                    .distinct()
                    .map(bultos::get)
                    .filter(bulto -> !bulto.tienePesosCompletos())
                    .map(CajaFisica::lider)
                    .toList();

            boolean esCinturon = referencia.startsWith(CajaData.PREFIJO_CINTURON);
            AmiLayout layout = esCinturon ? AmiLayout.BELTS : AmiLayout.BAGS;
            PackingListData data = mapearCabecera(destino, envio);
            data.setCajas(esCinturon
                    ? mapearCajasCinturon(cajas, bultos) : mapearCajasBolso(cajas, bultos));

            // El product order es el mismo para todo el grupo (viene por
            // bloque de referencia en el JSON): basta con el de la primera caja.
            String pedido = cajas.get(0).getNumeroPedido();

            byte[] excel = excelBuilder.generar(data, layout);
            resultado.add(new ExcelGenerado(destino.getNombreDestino(), referencia, color,
                    AmiNombreFichero.componer(envio.getFechaEnvio(), pedido, referencia, color,
                            envio.getTemporada(), destino.getNombreDestino()),
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

    /**
     * Bolsos/carteras (talla única): una fila de plantilla por línea.
     *
     * El peso que se escribe es el del BULTO, no el de la línea: así la caja
     * que comparten dos artículos sale con su peso en los dos packing lists
     * (es el mismo cartón y pesa lo mismo en los dos). Pero solo se escribe
     * en la PRIMERA fila que nombra ese bulto en este excel: repetirlo lo
     * contaría dos veces en el SUM de la fila de totales.
     */
    private List<PackingListData.Caja> mapearCajasBolso(List<CajaData> cajas,
                                                        Map<Integer, CajaFisica> bultos) {
        List<PackingListData.Caja> filas = new ArrayList<>();
        Set<Integer> bultosYaPesados = new HashSet<>();
        for (CajaData caja : cajas) {
            PackingListData.Caja fila = new PackingListData.Caja();
            fila.setNumeroCaja(caja.getNumeroCaja());
            fila.setNumeroPedido(caja.getNumeroPedido());
            fila.setReferencia(caja.getReferencia());
            fila.setCodigoColor(caja.getCodigoColor());
            fila.setCantidad(caja.getCantidad());
            fila.setTamanoCaja(caja.getTamanoCaja());
            if (bultosYaPesados.add(caja.getNumeroCaja())) {
                CajaFisica bulto = bultos.get(caja.getNumeroCaja());
                fila.setPesoNetoKg(bulto.pesoNetoKg());
                fila.setPesoBrutoKg(bulto.pesoBrutoKg());
            }
            filas.add(fila);
        }
        return filas;
    }

    /**
     * Cinturones (matriz de tallas): una caja física puede mezclar tallas,
     * así que las entradas del JSON con el mismo número de caja (una por
     * talla, igual que ya ocurre con los colores) se agregan en UNA fila de
     * plantilla — por eso aquí no hace falta callar ningún peso repetido.
     *
     * El peso es el del BULTO de la destinación, no el de las líneas que
     * caen en este excel: si la caja la comparte con un bolso, su líder está
     * en el packing list del bolso y aquí solo se ve el resto de líneas.
     */
    private List<PackingListData.Caja> mapearCajasCinturon(List<CajaData> cajas,
                                                           Map<Integer, CajaFisica> bultos) {
        List<PackingListData.Caja> filas = new ArrayList<>();
        for (CajaFisica cajaFisica : CajaFisica.agrupar(cajas)) {
            List<CajaData> entradas = cajaFisica.lineas();
            CajaData primera = cajaFisica.lider();
            CajaFisica bulto = bultos.get(primera.getNumeroCaja());
            PackingListData.Caja fila = new PackingListData.Caja();
            fila.setNumeroCaja(primera.getNumeroCaja());
            fila.setNumeroPedido(primera.getNumeroPedido());
            fila.setReferencia(primera.getReferencia());
            fila.setCodigoColor(primera.getCodigoColor());
            fila.setTamanoCaja(primera.getTamanoCaja());
            fila.setPesoNetoKg(bulto.pesoNetoKg());
            fila.setPesoBrutoKg(bulto.pesoBrutoKg());

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
}
