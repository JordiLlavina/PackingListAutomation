package com.puntotres.packinglist.service.cargamodelos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.service.cargamodelos.LecturaOrigen.ArticuloLeido;
import com.puntotres.packinglist.service.cargamodelos.LecturaOrigen.ColorLeido;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.corte.ApcSmsExcel;
import com.puntotres.packinglist.service.corte.ArticuloCorte;
import com.puntotres.packinglist.service.corte.ClienteCorte;
import com.puntotres.packinglist.service.corte.ColorCorte;
import com.puntotres.packinglist.service.corte.CorteSmsService;
import com.puntotres.packinglist.service.corte.DocumentosCorteService;
import com.puntotres.packinglist.service.corte.Imagen;
import com.puntotres.packinglist.service.corte.LineaSms;
import com.puntotres.packinglist.service.corte.PedidoCorte;
import com.puntotres.packinglist.service.corte.PielCorte;
import com.puntotres.packinglist.service.corte.ProduccionCorte;
import com.puntotres.packinglist.service.corte.ProduccionPuntotresExcel;
import com.puntotres.packinglist.service.corte.ReferenciaCorte;

/**
 * Lee los tres excels de los que puede salir una carga de modelos y los deja
 * en una sola forma ({@link LecturaOrigen}). No tiene lectores propios: usa
 * los del corte, así que lo que aquí cuenta como modelo, piel o color es lo
 * mismo que en las órdenes de corte.
 *
 * Qué trae cada uno (medido contra los excels reales, no supuesto): ningún
 * PEDIDO trae el nombre de la piel y el de AMI tampoco el del bolso; el de
 * PRODUCCIÓN trae los dos y el color de cada piel; el de SMS, además, el
 * forro y un croquis.
 */
@Service
public class LectorOrigenes {

    private final DocumentosCorteService reglas;
    private final CorteSmsService sms;

    public LectorOrigenes(DocumentosCorteService reglas, CorteSmsService sms) {
        this.reglas = reglas;
        this.sms = sms;
    }

    /** ¿Tiene ese cliente lector para ese origen? Los que no, salen «en desarrollo» en la pantalla. */
    public boolean admite(OrigenCarga origen, String cliente) {
        return switch (origen) {
            case TEMPORADA -> reglas.clientePara(cliente).isPresent();
            case SMS -> sms.admite(cliente);
            case PRODUCCION, GRUPO, MANUAL -> true;
        };
    }

    /** La regla con la que ese cliente parte una referencia en modelo y piel. */
    public ClienteCorte reglasDe(String cliente) {
        return reglas.reglasDeProduccion(cliente);
    }

    /**
     * El excel de pedido de una temporada. Si la temporada guarda además su
     * excel de producción, de él salen SOLO los huecos que el pedido no puede
     * llenar: el nombre del bolso (por modelo) y, en las referencias que los
     * dos escriben igual, el nombre de la piel y el color de cada piel. Los
     * artículos que solo están en el de producción no entran: para eso está
     * su origen. En AMI el de producción escribe la piel sin el cero (AL245)
     * y el pedido con él (AL0245), así que ahí solo se aprovecha el nombre
     * del bolso; no se normaliza, por decisión del usuario.
     *
     * @param produccion el excel de producción de la misma temporada, o null
     */
    public LecturaOrigen dePedido(String cliente, byte[] pedido, byte[] produccion) throws IOException {
        ClienteCorte lector = reglas.clientePara(cliente).orElseThrow(() -> new IllegalArgumentException(
                "el pedido de " + cliente + " todavía no se sabe leer"));
        PedidoCorte leido = lector.pedidoEntero(pedido);
        List<String> avisos = new ArrayList<>(leido.avisos());
        ProduccionCorte deProduccion = null;
        if (produccion != null) {
            try {
                deProduccion = ProduccionPuntotresExcel.leer(produccion, reglas.reglasDeProduccion(cliente));
            } catch (IOException | RuntimeException e) {
                avisos.add("El excel de producción de la temporada no se ha podido leer ("
                        + motivo(e) + "): los nombres de bolso y de piel hay que ponerlos a mano");
            }
        }
        Map<String, String> nombreDelModelo = new LinkedHashMap<>();
        if (deProduccion != null) {
            for (ArticuloCorte articulo : deProduccion.pedido().articulos()) {
                if (!articulo.nombreModelo().isBlank()) {
                    nombreDelModelo.putIfAbsent(modeloDe(articulo.referencia()), articulo.nombreModelo().trim());
                }
            }
        }
        List<ArticuloLeido> articulos = new ArrayList<>();
        for (ArticuloCorte articulo : leido.articulos()) {
            String referencia = articulo.referencia().referencia();
            String modelo = modeloDe(articulo.referencia());
            String nombre = articulo.nombreModelo().isBlank()
                    ? nombreDelModelo.getOrDefault(modelo, "") : articulo.nombreModelo().trim();
            String nombrePiel = deProduccion == null ? "" : mayusculas(deProduccion.pielDe().get(referencia));
            Map<String, List<String>> coloresDePiel = deProduccion == null ? Map.of()
                    : deProduccion.coloresDe().getOrDefault(referencia, Map.of());
            articulos.add(articulo(referencia, modelo, articulo.referencia().piel(), nombre, nombrePiel, "",
                    articulo.colores(), coloresDePiel));
        }
        return new LecturaOrigen(articulos, Map.of(), avisos);
    }

    /** El excel de producción de Puntotres: vale para todos los clientes. */
    public LecturaOrigen deProduccion(String cliente, byte[] excel) throws IOException {
        ProduccionCorte produccion = ProduccionPuntotresExcel.leer(excel, reglas.reglasDeProduccion(cliente));
        List<ArticuloLeido> articulos = new ArrayList<>();
        for (ArticuloCorte articulo : produccion.pedido().articulos()) {
            String referencia = articulo.referencia().referencia();
            articulos.add(articulo(referencia, modeloDe(articulo.referencia()), articulo.referencia().piel(),
                    articulo.nombreModelo().trim(), mayusculas(produccion.pielDe().get(referencia)), "",
                    articulo.colores(), produccion.coloresDe().getOrDefault(referencia, Map.of())));
        }
        List<String> avisos = new ArrayList<>(produccion.avisos());
        produccion.pedido().avisos().stream().filter(aviso -> !avisos.contains(aviso)).forEach(avisos::add);
        return new LecturaOrigen(articulos, Map.of(), avisos);
    }

    /**
     * El excel de pedido de SMS. Una fila es una referencia en un color: las
     * de la misma referencia se juntan en un artículo con varios colores, y
     * la piel, el forro y las combinaciones son los de la primera fila que
     * los trae. El croquis de la fila pasa a ser una foto del modelo.
     */
    public LecturaOrigen deSms(String cliente, byte[] excel) throws IOException {
        if (!sms.admite(cliente)) {
            throw new IllegalArgumentException("el excel de SMS de " + cliente + " todavía está en desarrollo");
        }
        ClienteCorte lector = reglas.clientePara(cliente).orElseThrow();
        ApcSmsExcel.Lectura lectura = ApcSmsExcel.leer(excel, lector);
        Map<String, List<LineaSms>> porReferencia = new LinkedHashMap<>();
        Map<String, List<Imagen>> croquis = new LinkedHashMap<>();
        for (LineaSms linea : lectura.lineas()) {
            porReferencia.computeIfAbsent(ReferenciaCorte.normalizar(linea.referencia()),
                    referencia -> new ArrayList<>()).add(linea);
            if (!linea.croquis().isEmpty()) {
                croquis.putIfAbsent(ReferenciaCorte.normalizar(linea.modelo()), linea.croquis());
            }
        }
        List<ArticuloLeido> articulos = new ArrayList<>();
        for (Map.Entry<String, List<LineaSms>> deLaReferencia : porReferencia.entrySet()) {
            List<LineaSms> lineas = deLaReferencia.getValue();
            ReferenciaCorte partida = lector.partir(deLaReferencia.getKey());
            String nombrePiel = primero(lineas, linea -> linea.pieles().principal().nombre());
            String forro = primero(lineas, linea -> linea.pieles().forro().nombre());
            List<String> combinaciones = lineas.stream()
                    .map(linea -> linea.pieles().combinaciones().stream().map(PielCorte::nombre).toList())
                    .filter(nombres -> !nombres.isEmpty())
                    .findFirst().orElse(List.of());
            Map<String, ColorLeido> colores = new LinkedHashMap<>();
            for (LineaSms linea : lineas) {
                ColorLeido color = new ColorLeido(ReferenciaCorte.normalizar(linea.color().codigo()),
                        linea.color().descripcion(), mayusculas(linea.pieles().principal().color()),
                        mayusculas(linea.pieles().forro().color()),
                        limitadas(linea.pieles().combinaciones().stream()
                                .map(piel -> mayusculas(piel.color())).toList()));
                if (!color.clave().isEmpty()) {
                    colores.putIfAbsent(color.clave(), color);
                }
            }
            articulos.add(new ArticuloLeido(deLaReferencia.getKey(), modeloDe(partida), partida.piel(),
                    primero(lineas, LineaSms::nombreModelo), mayusculas(nombrePiel), mayusculas(forro),
                    limitadas(combinaciones.stream().map(LectorOrigenes::mayusculas).toList()),
                    List.copyOf(colores.values())));
        }
        return new LecturaOrigen(articulos, croquis, lectura.avisos());
    }

    /**
     * Un artículo del corte con lo que el excel de producción diga de sus
     * pieles. {@code coloresDePiel}: clave del color → el color de la piel
     * principal y detrás los de las combinaciones, que en ese excel llevan
     * todas el nombre de la piel (un bicolor es la misma piel en dos colores).
     */
    private static ArticuloLeido articulo(String referencia, String modelo, String refPiel, String nombreModelo,
                                          String nombrePiel, String forro, List<ColorCorte> coloresDelBolso,
                                          Map<String, List<String>> coloresDePiel) {
        Map<String, ColorLeido> colores = new LinkedHashMap<>();
        int combinaciones = 0;
        for (ColorCorte color : coloresDelBolso) {
            List<String> deLasPieles = coloresDePiel.getOrDefault(ProduccionCorte.claveDe(color), List.of());
            List<String> deCombinacion = deLasPieles.size() <= 1 ? List.of() : limitadas(
                    deLasPieles.subList(1, deLasPieles.size()).stream().map(LectorOrigenes::mayusculas).toList());
            // La piel principal arranca con el color del bolso, como en la tabla del corte.
            String colorPiel = deLasPieles.isEmpty() ? mayusculas(color.descripcion())
                    : mayusculas(deLasPieles.get(0));
            ColorLeido leido = new ColorLeido(ReferenciaCorte.normalizar(color.codigo()), color.descripcion(),
                    colorPiel, "", deCombinacion);
            if (leido.clave().isEmpty()) {
                continue;
            }
            // El mismo color de venta y de prensa es un solo color en el catálogo.
            if (colores.putIfAbsent(leido.clave(), leido) == null) {
                combinaciones = Math.max(combinaciones, deCombinacion.size());
            }
        }
        List<String> nombresCombinacion = nombrePiel.isEmpty() ? List.of()
                : Collections.nCopies(combinaciones, nombrePiel);
        return new ArticuloLeido(ReferenciaCorte.normalizar(referencia), modelo, refPiel, nombreModelo, nombrePiel,
                forro, nombresCombinacion, List.copyOf(colores.values()));
    }

    /** El modelo de una referencia: el de la regla del cliente o, si no la parte, la referencia entera. */
    private static String modeloDe(ReferenciaCorte referencia) {
        return referencia.tienePiel() ? referencia.modelo() : referencia.referencia();
    }

    private static List<String> limitadas(List<String> combinaciones) {
        return combinaciones.size() <= CatalogoModelos.MAXIMO_COMBINACIONES ? combinaciones
                : List.copyOf(combinaciones.subList(0, CatalogoModelos.MAXIMO_COMBINACIONES));
    }

    private static String primero(List<LineaSms> lineas, Function<LineaSms, String> dato) {
        return lineas.stream().map(dato).filter(texto -> texto != null && !texto.isBlank())
                .map(String::trim).findFirst().orElse("");
    }

    private static String mayusculas(String texto) {
        return texto == null ? "" : texto.trim().toUpperCase(Locale.ROOT);
    }

    private static String motivo(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "no tiene el formato esperado";
    }
}
