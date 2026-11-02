package com.puntotres.packinglist.service.embalaje;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.ReglaClienteTaller;
import com.puntotres.packinglist.config.ReglasTallerProperties;
import com.puntotres.packinglist.model.DatosHojaEmbalaje;
import com.puntotres.packinglist.model.DatosHojaEmbalaje.DestinacionHoja;
import com.puntotres.packinglist.persistence.ArchivoHojasEmbalaje;
import com.puntotres.packinglist.persistence.Articulo;
import com.puntotres.packinglist.persistence.ColorCliente;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ResumenFoto;
import com.puntotres.packinglist.persistence.TablaColores;
import com.puntotres.packinglist.service.ApcPedidoExcel;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;
import com.puntotres.packinglist.service.corte.DocumentosCorteService;
import com.puntotres.packinglist.service.etiquetas.AmiPedidoExcel;

/**
 * Convierte el excel de pedido del cliente en la lista de artículos del
 * formulario de «Info de embalaje», con las destinaciones y todo lo que el
 * catálogo y las hojas anteriores ya saben.
 *
 * <b>Aquí sí se suman las tallas</b>: en las etiquetas cada talla de un
 * cinturón es un artículo con su propio EAN, pero la hoja de embalaje no lleva
 * EAN, solo le dice al taller cuántas unidades van a cada pedido y destino. Por
 * eso las tallas de un mismo PO (AMI) se agrupan en una sola fila; en APC ya
 * vienen juntas porque un {@code Document d'achat} es una destinación, un
 * artículo y un color.
 *
 * <b>La memoria del artículo solo se lee</b> (medida de caja, unidades por
 * caja, nombre del modelo, foto, y bolsa, etiqueta interior e instrucción de la
 * última hoja del modelo): lo que se imprime lo decide quien rellena el
 * formulario, y escribir aquí en el catálogo lo cambiaría para todos los demás
 * flujos sin que nadie lo hubiera pedido. Lo que se guarda va a la hoja
 * ({@link ArchivoHojasEmbalaje}), nunca de vuelta al catálogo.
 *
 * Un artículo sin cantidad pedida (Commandé vacío o a cero) sale igualmente en
 * el desplegable, pero sin destinaciones: una fila con cero unidades no es una
 * instrucción para el taller. Los avisos del lector de AMI (columnas de EAN o
 * Made in que faltan) no se trasladan porque esta hoja no usa esos datos.
 */
@Service
public class PedidoEmbalajeService {

    private static final String AMI = "AMI";
    private static final String APC = "APC";

    private final ReglasTallerProperties reglas;
    private final TablaColores colores;
    private final CatalogoModelos catalogo;
    private final FotosCatalogo fotos;
    private final DocumentosCorteService corte;
    private final ArchivoHojasEmbalaje hojas;

    public PedidoEmbalajeService(ReglasTallerProperties reglas, TablaColores colores,
                                 CatalogoModelos catalogo, FotosCatalogo fotos,
                                 DocumentosCorteService corte, ArchivoHojasEmbalaje hojas) {
        this.reglas = reglas;
        this.colores = colores;
        this.catalogo = catalogo;
        this.fotos = fotos;
        this.corte = corte;
        this.hojas = hojas;
    }

    /** ¿Sabemos leer el pedido de este cliente? Solo AMI y APC. */
    public boolean soporta(String cliente) {
        String clave = clave(cliente);
        return AMI.equals(clave) || APC.equals(clave);
    }

    /**
     * Los artículos del pedido, uno por referencia + código de color, en el
     * orden del fichero. Propaga la excepción del lector si el excel no se deja
     * leer; los datos que un humano puede corregir salen como avisos.
     */
    public ArticulosEmbalaje articulosDe(String cliente, byte[] excelPedido) throws IOException {
        String clave = clave(cliente);
        if (AMI.equals(clave)) {
            return desdeAmi(AmiPedidoExcel.desdeBytes(excelPedido));
        }
        if (APC.equals(clave)) {
            return desdeApc(ApcPedidoExcel.desdeBytes(excelPedido));
        }
        throw new IllegalArgumentException("El cliente " + cliente
                + " todavía no tiene lector de pedido para la info de embalaje");
    }

    // ---- AMI ----

    private ArticulosEmbalaje desdeAmi(AmiPedidoExcel pedido) {
        List<String> avisos = new ArrayList<>();
        Set<String> sufijosAvisados = new LinkedHashSet<>();
        ReglaClienteTaller reglaAmi = reglas.clienteTaller(AMI).orElseGet(ReglaClienteTaller::new);

        // Una entrada por referencia + código de color, en el orden del fichero.
        Map<String, List<AmiPedidoExcel.LineaCatalogo>> porArticulo = new LinkedHashMap<>();
        for (AmiPedidoExcel.LineaCatalogo linea : pedido.catalogo()) {
            porArticulo.computeIfAbsent(linea.referencia() + "\u0000" + linea.colorCode(),
                    k -> new ArrayList<>()).add(linea);
        }

        List<ArticuloEmbalaje> articulos = new ArrayList<>();
        for (List<AmiPedidoExcel.LineaCatalogo> lineas : porArticulo.values()) {
            AmiPedidoExcel.LineaCatalogo primera = lineas.get(0);
            Set<String> tallas = new LinkedHashSet<>();
            lineas.forEach(l -> tallas.add(l.talla()));

            // Se suman las tallas de un mismo PO: la clave es el PO numérico.
            Map<String, AmiPedidoExcel.Comanda> porPo = new LinkedHashMap<>();
            for (String talla : tallas) {
                for (AmiPedidoExcel.Comanda comanda
                        : pedido.comandasDe(primera.referencia(), primera.colorCode(), talla)) {
                    porPo.merge(comanda.poNumerico(), comanda,
                            (previa, nueva) -> new AmiPedidoExcel.Comanda(previa.poNumerico(),
                                    previa.poSufijo(), previa.cantidad() + nueva.cantidad()));
                }
            }

            List<DestinacionHoja> destinaciones = new ArrayList<>();
            for (AmiPedidoExcel.Comanda comanda : porPo.values()) {
                if (comanda.cantidad() <= 0) {
                    continue;
                }
                String sufijo = comanda.poSufijo();
                String destino = reglaAmi.destinoDeSufijo(sufijo)
                        .map(d -> d.toUpperCase(Locale.ROOT))
                        .orElseGet(() -> {
                            if (sufijosAvisados.add(String.valueOf(sufijo))) {
                                avisos.add("El sufijo de pedido '" + sufijo + "' no tiene destinación "
                                        + "en la configuración: se ha puesto el propio sufijo como destino");
                            }
                            return sufijo == null ? "" : sufijo.toUpperCase(Locale.ROOT);
                        });
                String numero = sufijo == null || sufijo.isBlank() ? comanda.poNumerico()
                        : comanda.poNumerico() + " " + sufijo;
                destinaciones.add(new DestinacionHoja(destino, numero, comanda.cantidad()));
            }

            articulos.add(montar(AMI, primera.referencia(), primera.colorCode(),
                    primera.colorNombre(), "", destinaciones));
        }
        return new ArticulosEmbalaje(articulos, avisos);
    }

    // ---- APC ----

    private ArticulosEmbalaje desdeApc(ApcPedidoExcel pedido) {
        List<String> avisos = new ArrayList<>(pedido.avisos());

        Map<String, List<ApcPedidoExcel.LineaCatalogo>> porArticulo = new LinkedHashMap<>();
        for (ApcPedidoExcel.LineaCatalogo linea : pedido.catalogo()) {
            porArticulo.computeIfAbsent(linea.referencia() + "\u0000" + linea.color(),
                    k -> new ArrayList<>()).add(linea);
        }

        List<ArticuloEmbalaje> articulos = new ArrayList<>();
        for (List<ApcPedidoExcel.LineaCatalogo> lineas : porArticulo.values()) {
            ApcPedidoExcel.LineaCatalogo primera = lineas.get(0);
            // Cantidad de ESTE artículo y color, no del Document d'achat entero:
            // un pedido puede llevar varios colores (ver ApcPedidoExcel.comandasDe).
            List<DestinacionHoja> destinaciones = new ArrayList<>();
            for (ApcPedidoExcel.Comanda c : pedido.comandasDe(primera.referencia(), primera.color())) {
                if (c.cantidad() > 0) {
                    destinaciones.add(new DestinacionHoja(
                            c.destino() == null ? "" : c.destino().toUpperCase(Locale.ROOT),
                            c.pedido(), c.cantidad()));
                }
            }

            String descripcion = colores.buscar(APC, primera.color())
                    .map(ColorCliente::getDescripcion).orElse("");
            String nombre = lineas.stream().map(ApcPedidoExcel.LineaCatalogo::designacion)
                    .filter(d -> d != null && !d.isBlank()).findFirst()
                    .map(d -> d.trim().toUpperCase(Locale.ROOT)).orElse("");

            articulos.add(montar(APC, primera.referencia(), primera.color(), descripcion,
                    nombre, destinaciones));
        }
        return new ArticulosEmbalaje(articulos, avisos);
    }

    // ---- Lo común: catálogo, foto y última hoja ----

    private ArticuloEmbalaje montar(String cliente, String referencia, String codigoColor,
                                    String descripcionColor, String nombrePedido,
                                    List<DestinacionHoja> destinaciones) {
        String codigoModelo = corte.reglasDeProduccion(cliente).partir(referencia).modelo();

        String nombre = nombrePedido;
        if (nombre.isBlank()) {
            nombre = catalogo.modelo(cliente, codigoModelo).map(Modelo::getNombre).orElse("");
        }

        Optional<Articulo> articulo = catalogo.articulo(cliente, referencia);
        String medida = articulo.map(Articulo::getMedidaCaja).orElse("");
        Integer unidades = articulo.map(Articulo::getUnidadesPorCaja).orElse(null);

        List<ResumenFoto> deLaFoto = fotos.de(cliente, codigoModelo);
        Long fotoId = deLaFoto.stream().filter(ResumenFoto::principal).findFirst()
                .or(() -> deLaFoto.stream().findFirst())
                .map(ResumenFoto::id).orElse(null);

        Optional<DatosHojaEmbalaje> ultima = hojas.ultimaDe(cliente, codigoModelo);

        return new ArticuloEmbalaje(referencia, codigoColor, descripcionColor,
                nombre == null ? "" : nombre, codigoModelo, fotoId,
                medida == null ? "" : medida, unidades,
                ultima.map(DatosHojaEmbalaje::bolsaTela).orElse(""),
                ultima.map(DatosHojaEmbalaje::etiquetaInterior).orElse(""),
                ultima.map(DatosHojaEmbalaje::instruccionEspecial).orElse(""),
                destinaciones);
    }

    private static String clave(String cliente) {
        return cliente == null ? "" : cliente.trim().toUpperCase(Locale.ROOT);
    }
}
