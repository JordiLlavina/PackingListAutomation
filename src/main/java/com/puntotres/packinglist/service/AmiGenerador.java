package com.puntotres.packinglist.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
 * <b>PRESS es la excepción</b>: es el material de marketing (desde SS27), va
 * a París con los documentos de France y se manda en UN solo packing list
 * con todos sus modelos. En DESTINATION lleva "France", que es el valor del
 * desplegable de la plantilla (decisión del usuario); que es PRESS se ve en
 * el nombre del fichero. Si lleva algún cinturón sale en la plantilla de
 * cinturones, con las filas de bolso en la rejilla "U" que esa plantilla
 * también trae en su leyenda; si solo lleva bolsos, en la de bolsos.
 *
 * Las cajas con pesos sin resolver no bloquean la generación: el excel
 * sale con esas celdas vacías y las cajas se devuelven en
 * {@link ExcelGenerado#getCajasPendientes()} para la pantalla de revisión.
 *
 * Un mismo bulto puede repartirse entre VARIOS de estos excels (dos colores
 * o dos referencias en la misma caja). El peso es del bulto, así que sale
 * repetido en todos ellos: cada packing list describe ese cartón entero y
 * uno sin peso no sirve para expedir. Dentro de cada excel, en cambio, se
 * escribe una sola vez (ver {@link #filasDe}).
 *
 * No usa los palets de la destinación (AMI no los imprime en el excel).
 *
 * El tipo de artículo se reconoce por el prefijo de la referencia: "UBL"
 * es cinturón (plantilla de matriz de tallas); "ULL" (bolso) y "USL"
 * (cartera) comparten la plantilla de bolsos, talla única.
 */
@Service
public class AmiGenerador implements GeneradorPackingListCliente {

    /** La destinación de marketing, que va en un solo packing list. */
    static final String PRESS = "PRESS";

    /** Lo que se escribe en DESTINATION del packing de PRESS. */
    private static final String DESTINO_EN_PACKING_PRESS = "France";

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

        if (esPress(destino)) {
            return List.of(generarPress(destino, envio, bultos));
        }

        Map<String, List<CajaData>> grupos = new LinkedHashMap<>();
        for (CajaData caja : destino.getCajas()) {
            String clave = caja.getReferencia() + "|" + caja.getCodigoColor();
            grupos.computeIfAbsent(clave, c -> new ArrayList<>()).add(caja);
        }

        List<ExcelGenerado> resultado = new ArrayList<>();
        for (List<CajaData> grupo : grupos.values()) {
            List<CajaData> cajas = ordenadas(grupo);

            String referencia = cajas.get(0).getReferencia();
            String color = cajas.get(0).getCodigoColor();

            boolean esCinturon = referencia.startsWith(CajaData.PREFIJO_CINTURON);
            AmiLayout layout = esCinturon ? AmiLayout.BELTS : AmiLayout.BAGS;
            PackingListData data = mapearCabecera(destino, envio);
            data.setCajas(filasDe(cajas, bultos));

            // El product order es el mismo para todo el grupo (viene por
            // bloque de referencia en el JSON): basta con el de la primera caja.
            String pedido = cajas.get(0).getNumeroPedido();

            byte[] excel = excelBuilder.generar(data, layout);
            resultado.add(new ExcelGenerado(destino.getNombreDestino(), referencia, color,
                    AmiNombreFichero.componer(envio.getFechaEnvio(), pedido, referencia, color,
                            envio.getTemporada(), destino.getNombreDestino()),
                    excel, pendientesDe(cajas, bultos)));
        }
        return resultado;
    }

    static boolean esPress(DestinoData destino) {
        return destino.getNombreDestino() != null
                && PRESS.equals(destino.getNombreDestino().trim().toUpperCase(Locale.ROOT));
    }

    /**
     * El packing único de PRESS: todas las cajas en orden de número. El
     * nombre del fichero lleva el PO (en el pedido real PRESS tiene uno solo
     * para todos sus artículos; si llegaran varios, van todos unidos por
     * guiones) y no lleva artículo, porque son todos.
     */
    private ExcelGenerado generarPress(DestinoData destino, DatosEnvio envio,
                                       Map<Integer, CajaFisica> bultos) throws IOException {
        List<CajaData> cajas = ordenadas(destino.getCajas());
        boolean algunCinturon = cajas.stream().anyMatch(CajaData::esCinturon);
        AmiLayout layout = algunCinturon ? AmiLayout.BELTS : AmiLayout.BAGS;

        PackingListData data = mapearCabecera(destino, envio);
        data.setDestino(DESTINO_EN_PACKING_PRESS);
        data.setCajas(filasDe(cajas, bultos));

        String pedidos = cajas.stream()
                .map(CajaData::getNumeroPedido)
                .filter(pedido -> pedido != null && !pedido.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining("-"));

        byte[] excel = excelBuilder.generar(data, layout);
        return new ExcelGenerado(destino.getNombreDestino(),
                AmiNombreFichero.componer(envio.getFechaEnvio(), pedidos, null, null,
                        envio.getTemporada(), destino.getNombreDestino()),
                excel, pendientesDe(cajas, bultos));
    }

    /** Por número de caja, conservando el orden de las líneas dentro de cada caja. */
    private static List<CajaData> ordenadas(List<CajaData> cajas) {
        List<CajaData> copia = new ArrayList<>(cajas);
        copia.sort(Comparator.comparingInt(CajaData::getNumeroCaja));
        return copia;
    }

    /**
     * Pendiente = bulto que sale con las celdas de peso vacías en ESTE
     * excel. Se mira el bulto de la destinación, no el del grupo: la caja
     * compartida ya no está pendiente aquí solo porque su peso lo trajera la
     * línea de otro artículo.
     */
    private static List<CajaData> pendientesDe(List<CajaData> cajas,
                                               Map<Integer, CajaFisica> bultos) {
        return cajas.stream()
                .map(CajaData::getNumeroCaja)
                .distinct()
                .map(bultos::get)
                .filter(bulto -> !bulto.tienePesosCompletos())
                .map(CajaFisica::lider)
                .toList();
    }

    private PackingListData mapearCabecera(DestinoData destino, DatosEnvio envio) {
        PackingListData data = new PackingListData();
        // El nombre de destinación se escribe tal cual llega; el posible
        // mapeo ciudad -> destino AMI queda pendiente de ver imágenes reales.
        data.setDestino(destino.getNombreDestino());
        data.setTemporada(envio.getTemporada());
        // Todos los excels de una destinación comparten su factura: la
        // tecleada en su cabecera de la revisión o, si no hay, la del envío.
        data.setNumeroFactura(envio.facturaPara(destino));
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
     * Las filas de plantilla de unas cajas ya ordenadas por número.
     *
     * Bolsos y carteras (talla única): una fila por línea. Cinturones
     * (matriz de tallas): una caja puede mezclar tallas, así que las líneas
     * con la misma caja, referencia y color (una por talla) se agregan en UNA
     * fila. La referencia y el color van en la clave porque en PRESS una
     * caja puede llevar dos cinturones distintos, y sumar sus tallas en una
     * fila con la referencia del primero diría algo que no es cierto.
     *
     * El peso que se escribe es el del BULTO, no el de la línea: así la caja
     * que comparten dos artículos sale con su peso en los dos packing lists
     * (es el mismo cartón y pesa lo mismo en los dos). Pero solo se escribe
     * en la PRIMERA fila que nombra ese bulto en este excel: repetirlo lo
     * contaría dos veces en el SUM de la fila de totales.
     */
    private List<PackingListData.Caja> filasDe(List<CajaData> cajas,
                                               Map<Integer, CajaFisica> bultos) {
        List<PackingListData.Caja> filas = new ArrayList<>();
        Map<String, PackingListData.Caja> filaDeCinturon = new LinkedHashMap<>();
        Set<Integer> bultosYaPesados = new HashSet<>();
        for (CajaData caja : cajas) {
            if (caja.esCinturon()) {
                if (caja.getTalla() == null || caja.getTalla().isBlank()) {
                    throw new IllegalArgumentException("La caja " + caja.getNumeroCaja()
                            + " de la referencia " + caja.getReferencia()
                            + " es un cinturón (UBL) pero no trae talla");
                }
                String clave = caja.getNumeroCaja() + "|" + caja.getReferencia()
                        + "|" + caja.getCodigoColor();
                PackingListData.Caja existente = filaDeCinturon.get(clave);
                if (existente != null) {
                    existente.getCantidadesPorTalla()
                            .merge(caja.getTalla(), caja.getCantidad(), Integer::sum);
                    continue;
                }
                PackingListData.Caja fila = filaBase(caja, bultos, bultosYaPesados);
                Map<String, Integer> cantidadesPorTalla = new LinkedHashMap<>();
                cantidadesPorTalla.put(caja.getTalla(), caja.getCantidad());
                fila.setCantidadesPorTalla(cantidadesPorTalla);
                filaDeCinturon.put(clave, fila);
                filas.add(fila);
            } else {
                PackingListData.Caja fila = filaBase(caja, bultos, bultosYaPesados);
                fila.setCantidad(caja.getCantidad());
                filas.add(fila);
            }
        }
        return filas;
    }

    private static PackingListData.Caja filaBase(CajaData caja, Map<Integer, CajaFisica> bultos,
                                                 Set<Integer> bultosYaPesados) {
        PackingListData.Caja fila = new PackingListData.Caja();
        fila.setNumeroCaja(caja.getNumeroCaja());
        fila.setNumeroPedido(caja.getNumeroPedido());
        fila.setReferencia(caja.getReferencia());
        fila.setCodigoColor(caja.getCodigoColor());
        fila.setTamanoCaja(caja.getTamanoCaja());
        if (bultosYaPesados.add(caja.getNumeroCaja())) {
            CajaFisica bulto = bultos.get(caja.getNumeroCaja());
            fila.setPesoNetoKg(bulto.pesoNetoKg());
            fila.setPesoBrutoKg(bulto.pesoBrutoKg());
        }
        return fila;
    }
}
