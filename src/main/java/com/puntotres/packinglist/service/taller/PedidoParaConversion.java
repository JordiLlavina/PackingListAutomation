package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.service.ApcPedidoExcel;
import com.puntotres.packinglist.service.etiquetas.AmiPedidoExcel;

/**
 * Lo que se sabe del pedido del cliente al convertir un excel de taller: el
 * excel de pedido ya leído (el de APC o el de AMI, según la plantilla), la
 * tabla de colores del cliente (código → nombre, de la pantalla Colores y
 * Pieles) y el catálogo de destinaciones del yml.
 *
 * Lo consumen dos piezas con papeles distintos: {@link IndiceCatalogoConversion}
 * lo convierte en el texto que viaja a Claude, para VALIDAR y TRADUCIR lo que
 * lee; y {@link AjustadorConversion} lo cruza en Java con lo que Claude ha
 * devuelto, para DECIDIR (completar la referencia, resolver el código de
 * pedido) de forma determinista y auditable.
 *
 * Sin excel de pedido es {@link #vacio}: la conversión sigue, sin índice y
 * sin ajuste.
 */
public final class PedidoParaConversion {

    private final TipoPlantilla plantilla;
    private final ApcPedidoExcel apc;
    private final AmiPedidoExcel ami;
    private final Map<String, String> coloresCliente;
    private final ClienteConfig cliente;
    private final List<String> avisos;

    private PedidoParaConversion(TipoPlantilla plantilla, ApcPedidoExcel apc, AmiPedidoExcel ami,
                                 Map<String, String> coloresCliente, ClienteConfig cliente,
                                 List<String> avisos) {
        this.plantilla = plantilla;
        this.apc = apc;
        this.ami = ami;
        this.coloresCliente = Collections.unmodifiableMap(coloresCliente);
        this.cliente = cliente;
        this.avisos = List.copyOf(avisos);
    }

    public static PedidoParaConversion vacio(TipoPlantilla plantilla, ClienteConfig cliente) {
        return new PedidoParaConversion(plantilla, null, null, new LinkedHashMap<>(), cliente,
                List.of());
    }

    /**
     * Lee el excel de pedido según la plantilla. Un excel que no se deja leer
     * no para la conversión: se avisa y se sigue sin él, como hace
     * {@code CatalogoPedido} en la extracción por imágenes.
     *
     * @param coloresCliente la tabla de colores del cliente, código → nombre
     *                       (nombre vacío si no se sabe); null si no hay
     */
    public static PedidoParaConversion cargar(TipoPlantilla plantilla, byte[] excelPedido,
                                              Map<String, String> coloresCliente,
                                              ClienteConfig cliente) {
        Map<String, String> colores = new LinkedHashMap<>();
        if (coloresCliente != null) {
            coloresCliente.forEach((codigo, nombre) -> {
                if (codigo != null && !codigo.isBlank()) {
                    colores.put(clave(codigo), nombre == null ? "" : nombre.trim());
                }
            });
        }
        if (plantilla == null || excelPedido == null || excelPedido.length == 0) {
            return new PedidoParaConversion(plantilla, null, null, colores, cliente, List.of());
        }
        List<String> avisos = new ArrayList<>();
        try {
            return switch (plantilla) {
                case APC -> new PedidoParaConversion(plantilla,
                        ApcPedidoExcel.desdeBytes(excelPedido), null, colores, cliente, avisos);
                case AMI -> new PedidoParaConversion(plantilla, null,
                        AmiPedidoExcel.desdeBytes(excelPedido), colores, cliente, avisos);
                case GENERIC -> new PedidoParaConversion(plantilla, null, null, colores, cliente,
                        avisos);
            };
        } catch (Exception e) {
            avisos.add("No se ha podido leer el excel de pedido para contrastar la conversión ("
                    + e.getMessage() + "): se ha convertido sin él");
            return new PedidoParaConversion(plantilla, null, null, colores, cliente, avisos);
        }
    }

    public TipoPlantilla plantilla() {
        return plantilla;
    }

    public boolean estaVacio() {
        return apc == null && ami == null;
    }

    public Optional<ApcPedidoExcel> apc() {
        return Optional.ofNullable(apc);
    }

    public Optional<AmiPedidoExcel> ami() {
        return Optional.ofNullable(ami);
    }

    /** Código → nombre, en mayúsculas; el nombre vacío si no se sabe. */
    public Map<String, String> coloresCliente() {
        return coloresCliente;
    }

    /** El código de un nombre de color ("GRIS" → "LAW") si la tabla lo dice, o vacío. */
    public Optional<String> codigoDeColor(String nombreOCodigo) {
        if (nombreOCodigo == null || nombreOCodigo.isBlank()) {
            return Optional.empty();
        }
        String buscado = clave(nombreOCodigo);
        if (coloresCliente.containsKey(buscado)) {
            return Optional.of(buscado);
        }
        List<String> candidatos = coloresCliente.entrySet().stream()
                .filter(entrada -> !entrada.getValue().isEmpty()
                        && clave(entrada.getValue()).equals(buscado))
                .map(Map.Entry::getKey)
                .toList();
        return candidatos.size() == 1 ? Optional.of(candidatos.get(0)) : Optional.empty();
    }

    /** El destino PADRE del catálogo del cliente al que va una destinación, si lo hay. */
    public Optional<String> destinoPadre(String destinacion) {
        if (cliente == null || destinacion == null || destinacion.isBlank()) {
            return Optional.empty();
        }
        return cliente.destinoPadrePara(destinacion).map(ClienteConfig.DestinoResuelto::nombrePadre);
    }

    public List<String> avisos() {
        return avisos;
    }

    static String clave(String texto) {
        return texto == null ? "" : texto.trim().toUpperCase(Locale.ROOT);
    }
}
