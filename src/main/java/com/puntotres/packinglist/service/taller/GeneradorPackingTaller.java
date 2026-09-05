package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.config.NumeracionCajas;
import com.puntotres.packinglist.config.ReglasTallerProperties;
import com.puntotres.packinglist.model.EnvioInput;

/**
 * Del material que ha llegado del taller al mismo {@code EnvioInput} que
 * produce cualquier otra vía de entrada.
 *
 * Orquesta los cuatro pasos —repartir, agrupar en cajas, apilar y numerar— y
 * los junta en el JSON de siempre. Que la salida sea un EnvioInput es lo que
 * hace que de aquí en adelante no haya nada especial: la revisión, los packing
 * lists, las etiquetas y el volcado ERP no se enteran de que estos datos
 * vienen de un taller.
 *
 * <b>Hijas y padres.</b> El reparto y la prioridad trabajan sobre la
 * destinación HIJA (CHINE FRANCH se sirve de las primeras), pero las cajas,
 * los palets y la numeración van por destinación PADRE (WHOLESALE), que es la
 * que acaba en el packing list y cuyo almacén recibe el bulto. Un palet nunca
 * mezcla destinaciones, y dos hijas del mismo padre van al mismo sitio.
 *
 * <b>La numeración es lo último.</b> Se numera palet a palet, y dentro de cada
 * palet pila a pila, de modo que cada palet ocupa un rango contiguo de números
 * por construcción. Sin eso, {@code PaletAssignmentService} no podría volver a
 * asignar los palets al importar el JSON generado.
 */
@Service
public class GeneradorPackingTaller {

    private final ReglasTallerProperties reglas;
    private final ClientesProperties clientes;
    private final RepartoDestinaciones reparto;
    private final AgrupadorCajas agrupador;
    private final ApiladorPalets apilador;

    public GeneradorPackingTaller(ReglasTallerProperties reglas, ClientesProperties clientes,
                                  RepartoDestinaciones reparto, AgrupadorCajas agrupador,
                                  ApiladorPalets apilador) {
        this.reglas = reglas;
        this.clientes = clientes;
        this.reparto = reparto;
        this.agrupador = agrupador;
        this.apilador = apilador;
    }

    /**
     * @param alturaTecleadaCm altura máxima de palet que ha escrito el usuario,
     *                         solo para clientes sin normas propias
     */
    public ResultadoPackingTaller generar(String clienteClave, List<FilaAjustada> filas,
                                          Integer alturaTecleadaCm) {
        EnvioInput envio = new EnvioInput();
        envio.setCliente(clienteClave);
        envio.setDestinos(new ArrayList<>());
        ResultadoPackingTaller resultado = new ResultadoPackingTaller(envio);

        ResultadoReparto repartido = reparto.repartir(clienteClave, filas);
        resultado.getAvisos().addAll(repartido.getAvisos());
        resultado.getBloqueos().addAll(repartido.getBloqueos());
        if (!resultado.sePuedeGenerar()) {
            return resultado;
        }

        Map<String, List<ArticuloDestinado>> porPadre =
                agruparPorDestinoPadre(clienteClave, repartido.getArticulos());

        int contadorCajas = 1;
        boolean numeracionContinua = numeracionContinua(clienteClave);

        for (Map.Entry<String, List<ArticuloDestinado>> entrada : porPadre.entrySet()) {
            String padre = entrada.getKey();
            List<ArticuloDestinado> articulos = entrada.getValue();
            int alturaUtil = alturaUtilDe(clienteClave, padre, articulos, alturaTecleadaCm);

            List<CajaGenerada> cajas = agrupador.agrupar(
                    articulos, reglas.mezclaDe(clienteClave, padre));
            ResultadoApilado apilado = apilador.apilar(
                    cajas, alturaUtil, reglas.getPosicionesPalet());
            resultado.getBloqueos().addAll(apilado.getBloqueos());
            if (!resultado.sePuedeGenerar()) {
                continue;
            }

            int primeraCaja = numeracionContinua ? contadorCajas : 1;
            EnvioInput.DestinoInput destino = montarDestino(
                    padre, articulos, apilado.getPalets(), primeraCaja);
            envio.getDestinos().add(destino);
            resultado.getResumen().add(resumenDe(padre, articulos, apilado, alturaUtil));

            contadorCajas = primeraCaja + apilado.getPalets().stream()
                    .mapToInt(palet -> palet.cajas().size()).sum();
        }

        if (!resultado.sePuedeGenerar()) {
            envio.getDestinos().clear();
            resultado.getResumen().clear();
        }
        return resultado;
    }

    // --- Destinaciones ---

    /**
     * Los artículos agrupados por su destinación padre, en el orden en que las
     * destinaciones aparecen por primera vez. La hija se conserva dentro de
     * cada artículo: viaja al packing list en la columna del canal.
     */
    private Map<String, List<ArticuloDestinado>> agruparPorDestinoPadre(
            String clienteClave, List<ArticuloDestinado> articulos) {
        Map<String, List<ArticuloDestinado>> porPadre = new LinkedHashMap<>();
        for (ArticuloDestinado articulo : articulos) {
            porPadre.computeIfAbsent(padreDe(clienteClave, articulo.destino()),
                    clave -> new ArrayList<>()).add(articulo);
        }
        return porPadre;
    }

    private String padreDe(String clienteClave, String destino) {
        return clientes.clientePara(clienteClave)
                .flatMap(cliente -> cliente.destinoPadrePara(destino))
                .map(ClienteConfig.DestinoResuelto::nombrePadre)
                .orElse(destino);
    }

    /**
     * La altura útil de un palet de esa destinación. Si dos hijas del mismo
     * padre declararan alturas distintas, manda la más baja: sus cajas van a
     * compartir palet y el palet no puede pasarse de la más restrictiva.
     */
    private int alturaUtilDe(String clienteClave, String padre, List<ArticuloDestinado> articulos,
                             Integer alturaTecleadaCm) {
        return articulos.stream()
                .map(ArticuloDestinado::destino)
                .distinct()
                .mapToInt(hija -> reglas.alturaUtilCm(clienteClave, hija, padre, alturaTecleadaCm))
                .min()
                .orElseGet(() -> reglas.alturaUtilCm(clienteClave, padre, padre, alturaTecleadaCm));
    }

    // --- Montaje del EnvioInput ---

    private EnvioInput.DestinoInput montarDestino(String padre, List<ArticuloDestinado> articulos,
                                                  List<PaletGenerado> palets, int primeraCaja) {
        EnvioInput.DestinoInput destino = new EnvioInput.DestinoInput();
        destino.setDestino(padre);
        destino.setPalets(new ArrayList<>());

        // Una entrada por artículo y caja: (clave del artículo) -> (nº caja, unidades).
        Map<String, List<int[]>> cajasPorArticulo = new LinkedHashMap<>();
        int numero = primeraCaja;
        int numeroPalet = 1;

        for (PaletGenerado palet : palets) {
            EnvioInput.PaletInput paletInput = new EnvioInput.PaletInput();
            paletInput.setPalet(numeroPalet++);
            paletInput.setCajaInicio(numero);
            for (CajaGenerada caja : palet.cajas()) {
                for (ContenidoCaja contenido : caja.contenido()) {
                    cajasPorArticulo
                            .computeIfAbsent(claveArticulo(caja.destino(), contenido),
                                    clave -> new ArrayList<>())
                            .add(new int[] {numero, contenido.unidades()});
                }
                numero++;
            }
            paletInput.setCajaFin(numero - 1);
            destino.getPalets().add(paletInput);
        }

        destino.setReferencias(montarReferencias(padre, articulos, cajasPorArticulo));
        return destino;
    }

    private List<EnvioInput.ReferenciaInput> montarReferencias(
            String padre, List<ArticuloDestinado> articulos,
            Map<String, List<int[]>> cajasPorArticulo) {
        Map<String, ArticuloDestinado> porClave = new LinkedHashMap<>();
        for (ArticuloDestinado articulo : articulos) {
            porClave.putIfAbsent(claveArticulo(articulo), articulo);
        }

        List<EnvioInput.ReferenciaInput> referencias = new ArrayList<>();
        for (Map.Entry<String, ArticuloDestinado> entrada : porClave.entrySet()) {
            List<int[]> cajas = cajasPorArticulo.get(entrada.getKey());
            if (cajas == null || cajas.isEmpty()) {
                continue;
            }
            ArticuloDestinado articulo = entrada.getValue();
            EnvioInput.ReferenciaInput referencia = new EnvioInput.ReferenciaInput();
            referencia.setReferencia(articulo.referencia());
            referencia.setColor(articulo.color());
            referencia.setTalla(articulo.talla());
            referencia.setPedido(articulo.pedido());
            referencia.setMedidaCaja(articulo.medidaCaja());
            // El canal solo se rellena cuando la hija no es el padre: es lo
            // que APC imprime en su columna DESTINATION.
            if (!articulo.destino().equals(padre)) {
                referencia.setCanal(articulo.destino());
            }
            referencia.setCantidadTotal(cajas.stream().mapToInt(caja -> caja[1]).sum());
            referencia.setCajas(comprimirEnRangos(cajas));
            referencias.add(referencia);
        }
        return referencias;
    }

    /**
     * Cajas consecutivas con las mismas unidades se escriben como un rango, y
     * el resto como cajas sueltas: es el mismo formato que escribiría una
     * persona a mano, y el importador ya sabe leerlo.
     */
    private static List<EnvioInput.CajaRangoInput> comprimirEnRangos(List<int[]> cajas) {
        List<EnvioInput.CajaRangoInput> entradas = new ArrayList<>();
        int i = 0;
        while (i < cajas.size()) {
            int inicio = i;
            while (i + 1 < cajas.size()
                    && cajas.get(i + 1)[0] == cajas.get(i)[0] + 1
                    && cajas.get(i + 1)[1] == cajas.get(inicio)[1]) {
                i++;
            }
            EnvioInput.CajaRangoInput entrada = new EnvioInput.CajaRangoInput();
            if (inicio == i) {
                entrada.setCaja(cajas.get(inicio)[0]);
                entrada.setUnidades(cajas.get(inicio)[1]);
            } else {
                entrada.setCajaInicio(cajas.get(inicio)[0]);
                entrada.setCajaFin(cajas.get(i)[0]);
                entrada.setUnidadesPorCaja(cajas.get(inicio)[1]);
            }
            entradas.add(entrada);
            i++;
        }
        return entradas;
    }

    private static ResumenDestino resumenDe(String padre, List<ArticuloDestinado> articulos,
                                            ResultadoApilado apilado, int alturaUtil) {
        List<PaletGenerado> palets = apilado.getPalets();
        return new ResumenDestino(
                padre,
                articulos.stream().mapToInt(ArticuloDestinado::cantidad).sum(),
                palets.stream().mapToInt(palet -> palet.cajas().size()).sum(),
                palets.size(),
                palets.isEmpty() ? 0 : palets.get(palets.size() - 1).alturaMaximaCm(),
                alturaUtil);
    }

    private boolean numeracionContinua(String clienteClave) {
        return reglas.clienteTaller(clienteClave)
                .map(regla -> regla.getNumeracionCajas() == NumeracionCajas.CONTINUA)
                .orElse(false);
    }

    /**
     * Identidad de un artículo dentro del envío: lo que ocupa una fila del
     * packing list. Lleva la destinación HIJA porque el mismo artículo puede
     * viajar a dos canales del mismo almacén y hay que distinguirlos.
     */
    private static String claveArticulo(ArticuloDestinado articulo) {
        return clave(articulo.destino(), articulo.referencia(), articulo.color(),
                articulo.talla(), articulo.pedido());
    }

    private static String claveArticulo(String destino, ContenidoCaja contenido) {
        return clave(destino, contenido.referencia(), contenido.color(),
                contenido.talla(), contenido.pedido());
    }

    private static String clave(String destino, String referencia, String color,
                                String talla, String pedido) {
        return String.join("|", destino, referencia, color, talla,
                Optional.ofNullable(pedido).orElse(""));
    }
}
