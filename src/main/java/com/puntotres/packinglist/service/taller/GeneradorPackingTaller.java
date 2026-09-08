package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.CatalogoTaras;
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
 * destinación HIJA (CHINE FRANCH se sirve de las primeras). El fichero, la
 * hoja, la dirección y la numeración van por destinación PADRE (WHOLESALE),
 * que es la que acaba en el packing list. Pero las CAJAS y los PALETS van por
 * hija: dos hijas del mismo padre viajan al mismo almacén y salen en el mismo
 * excel, y aun así no comparten ni bulto ni palet, porque allí se reciben por
 * separado y un palet mixto habría que deshacerlo al llegar.
 *
 * <b>La numeración es lo último.</b> Se numera palet a palet, y dentro de cada
 * palet pila a pila, de modo que cada palet ocupa un rango contiguo de números
 * por construcción. Sin eso, {@code PaletAssignmentService} no podría volver a
 * asignar los palets al importar el JSON generado. La numeración CONTINUA
 * afecta a cajas Y palets: con las cajas seguidas entre destinaciones, dos
 * palets llamados "1" serían dos bultos físicos con el mismo número en el
 * mismo envío. Con POR_DESTINACION reinician los dos, porque cada destinación
 * es una entrega aparte.
 */
@Service
public class GeneradorPackingTaller {

    private final ReglasTallerProperties reglas;
    private final ClientesProperties clientes;
    private final RepartoDestinaciones reparto;
    private final AgrupadorCajas agrupador;
    private final ApiladorPalets apilador;
    /** Para repartir el peso declarado entre cartón y mercancía. */
    private final CatalogoTaras taras;

    public GeneradorPackingTaller(ReglasTallerProperties reglas, ClientesProperties clientes,
                                  RepartoDestinaciones reparto, AgrupadorCajas agrupador,
                                  ApiladorPalets apilador, CatalogoTaras taras) {
        this.reglas = reglas;
        this.clientes = clientes;
        this.reparto = reparto;
        this.agrupador = agrupador;
        this.apilador = apilador;
        this.taras = taras;
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

        // La numeración continua vale para las cajas Y para los palets: con
        // las cajas seguidas entre destinaciones, dos palets llamados "1"
        // serían dos bultos físicos con el mismo número en el mismo envío.
        int contadorCajas = 1;
        int contadorPalets = 1;
        boolean numeracionContinua = numeracionContinua(clienteClave);

        for (Map.Entry<String, List<ArticuloDestinado>> entrada : porPadre.entrySet()) {
            String padre = entrada.getKey();
            List<ArticuloDestinado> articulos = entrada.getValue();

            List<CajaGenerada> cajas = agrupador.agrupar(
                    articulos, reglas.mezclaDe(clienteClave, padre));
            ResultadoApilado apilado = apilarPorDestinoHijo(
                    clienteClave, padre, cajas, alturaTecleadaCm);
            resultado.getBloqueos().addAll(apilado.getBloqueos());
            if (!resultado.sePuedeGenerar()) {
                continue;
            }

            int primeraCaja = numeracionContinua ? contadorCajas : 1;
            int primerPalet = numeracionContinua ? contadorPalets : 1;
            EnvioInput.DestinoInput destino = montarDestino(padre, articulos,
                    apilado.getPalets(), primeraCaja, primerPalet, resultado.getAvisos());
            envio.getDestinos().add(destino);
            resultado.getResumen().add(resumenDe(padre, articulos, apilado,
                    alturaUtilDe(clienteClave, padre, articulos, alturaTecleadaCm)));

            contadorCajas = primeraCaja + apilado.getPalets().stream()
                    .mapToInt(palet -> palet.cajas().size()).sum();
            contadorPalets = primerPalet + apilado.getPalets().size();
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
     * Apila las cajas del padre, pero <b>un palet por destinación hija</b>.
     *
     * Las hijas comparten fichero, hoja y dirección —por eso van juntas en el
     * packing list—, pero no comparten bulto ni palet: el almacén las recibe
     * por separado y un palet mixto habría que deshacerlo al llegar. Cuesta
     * palets (dos hijas con tres cajas cada una llenan dos palets donde cabría
     * uno) y es a propósito.
     *
     * Cada hija se apila con SU altura útil y no con la más baja de todas: al
     * no compartir palet, lo que aguante una no limita a la otra.
     *
     * Los palets salen en el orden en que aparecen las hijas, así que cada
     * hija ocupa palets consecutivos y, con ellos, un rango de números de caja
     * contiguo: es lo que después permite a {@code PaletAssignmentService}
     * reasignar los palets al importar el JSON generado.
     */
    private ResultadoApilado apilarPorDestinoHijo(String clienteClave, String padre,
                                                  List<CajaGenerada> cajas,
                                                  Integer alturaTecleadaCm) {
        ResultadoApilado total = new ResultadoApilado();
        for (Map.Entry<String, List<CajaGenerada>> hija : porDestinoHijo(cajas).entrySet()) {
            ResultadoApilado suyo = apilador.apilar(hija.getValue(),
                    reglas.alturaUtilCm(clienteClave, hija.getKey(), padre, alturaTecleadaCm),
                    reglas.getPosicionesPalet());
            total.getPalets().addAll(suyo.getPalets());
            total.getBloqueos().addAll(suyo.getBloqueos());
        }
        return total;
    }

    /** Las cajas por destinación hija, en orden de primera aparición. */
    private static Map<String, List<CajaGenerada>> porDestinoHijo(List<CajaGenerada> cajas) {
        Map<String, List<CajaGenerada>> porHija = new LinkedHashMap<>();
        for (CajaGenerada caja : cajas) {
            porHija.computeIfAbsent(caja.destino(), clave -> new ArrayList<>()).add(caja);
        }
        return porHija;
    }

    /**
     * La altura útil que se enseña en el resumen, que tiene una sola línea por
     * destinación padre. Es la más baja de sus hijas: cada una se apila con la
     * suya, así que la más restrictiva es la única que se puede afirmar de la
     * destinación entera sin engañar a nadie.
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

    /**
     * Una caja concreta en la que va un artículo: su número, cuántas unidades
     * de ese artículo lleva y si el bulto es SOLO suyo. Lo último decide si se
     * le puede poner el peso bruto declarado de la referencia.
     */
    private record CajaDeArticulo(int numero, int unidades, boolean pura) {
    }

    private EnvioInput.DestinoInput montarDestino(String padre, List<ArticuloDestinado> articulos,
                                                  List<PaletGenerado> palets, int primeraCaja,
                                                  int primerPalet, List<String> avisos) {
        EnvioInput.DestinoInput destino = new EnvioInput.DestinoInput();
        destino.setDestino(padre);
        destino.setPalets(new ArrayList<>());

        Map<String, List<CajaDeArticulo>> cajasPorArticulo = new LinkedHashMap<>();
        int numero = primeraCaja;
        int numeroPalet = primerPalet;

        for (PaletGenerado palet : palets) {
            EnvioInput.PaletInput paletInput = new EnvioInput.PaletInput();
            paletInput.setPalet(numeroPalet++);
            paletInput.setCajaInicio(numero);
            for (CajaGenerada caja : palet.cajas()) {
                boolean pura = caja.contenido().size() == 1;
                for (ContenidoCaja contenido : caja.contenido()) {
                    cajasPorArticulo
                            .computeIfAbsent(claveArticulo(caja.destino(), contenido),
                                    clave -> new ArrayList<>())
                            .add(new CajaDeArticulo(numero, contenido.unidades(), pura));
                }
                numero++;
            }
            paletInput.setCajaFin(numero - 1);
            destino.getPalets().add(paletInput);
        }

        destino.setReferencias(montarReferencias(padre, articulos, cajasPorArticulo, avisos));
        return destino;
    }

    /**
     * El peso bruto que le toca a una caja, o null.
     *
     * Lo que se teclea en la pantalla de ajuste es lo que pesa <em>una caja
     * llena</em>. Una caja llena se lo lleva tal cual; una que va a medias se
     * calcula escalando <b>solo la mercancía</b>, porque el cartón pesa igual
     * vaya lleno o a medias:
     *
     * <pre>peso = tara + (pesoDeclarado − tara) × unidades / unidadesPorCaja</pre>
     *
     * Hay que escalar, no copiar: con el reparto equitativo casi ninguna caja
     * sale llena —25 unidades de a 10 por caja son 9+8+8—, así que copiar el
     * peso declarado lo pondría igual en las tres y aplicarlo solo a las
     * llenas no lo pondría en ninguna. Es la misma cuenta que haría después
     * {@code WeightInferenceService} si tuviera una caja llena de la que
     * aprender; aquí se hace antes porque no la hay.
     *
     * Dos casos se quedan sin peso, y ninguno se inventa un número: un bulto
     * <b>mixto</b> (el peso declarado es de un solo artículo y ahí van varios)
     * y un cartón <b>sin tara conocida</b> (sin ella no se puede separar lo
     * que pesa el cartón de lo que pesa la mercancía).
     */
    private Double pesoBrutoDe(ArticuloDestinado articulo, CajaDeArticulo caja) {
        if (articulo.pesoBrutoKg() == null || !caja.pura()) {
            return null;
        }
        if (caja.unidades() == articulo.unidadesPorCaja()) {
            return articulo.pesoBrutoKg();
        }
        Optional<Double> tara = taras.taraPara(articulo.medidaCaja());
        if (tara.isEmpty() || articulo.pesoBrutoKg() <= tara.get()) {
            return null;
        }
        double mercanciaLlena = articulo.pesoBrutoKg() - tara.get();
        double peso = tara.get()
                + mercanciaLlena * caja.unidades() / articulo.unidadesPorCaja();
        // Dos decimales: es lo que admite la revisión y lo que se escribe en
        // el packing list; más cifras solo serían ruido de coma flotante.
        return Math.round(peso * 100.0) / 100.0;
    }

    /**
     * Un peso tecleado que no ha acabado en ninguna caja. Pasa cuando de esa
     * referencia no sale ni una caja llena —lo que ha llegado no da para
     * una— o cuando todas van mezcladas con otro artículo. Callarlo dejaría al
     * usuario creyendo que ya ha pesado ese material.
     */
    private void avisarSiElPesoNoSeHaPodidoUsar(ArticuloDestinado articulo,
                                                       List<CajaDeArticulo> cajas,
                                                       List<String> avisos) {
        if (articulo.pesoBrutoKg() == null
                || cajas.stream().anyMatch(caja -> pesoBrutoDe(articulo, caja) != null)) {
            return;
        }
        avisos.add(articulo.destino() + ": de " + articulo.descripcion()
                + " no sale ninguna caja llena y solo suya, así que el peso bruto que has puesto "
                + "no se ha podido usar. Los pesos se rellenan en la revisión");
    }

    private List<EnvioInput.ReferenciaInput> montarReferencias(
            String padre, List<ArticuloDestinado> articulos,
            Map<String, List<CajaDeArticulo>> cajasPorArticulo, List<String> avisos) {
        Map<String, ArticuloDestinado> porClave = new LinkedHashMap<>();
        for (ArticuloDestinado articulo : articulos) {
            porClave.putIfAbsent(claveArticulo(articulo), articulo);
        }

        List<EnvioInput.ReferenciaInput> referencias = new ArrayList<>();
        for (Map.Entry<String, ArticuloDestinado> entrada : porClave.entrySet()) {
            List<CajaDeArticulo> cajas = cajasPorArticulo.get(entrada.getKey());
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
            referencia.setCantidadTotal(cajas.stream().mapToInt(CajaDeArticulo::unidades).sum());
            referencia.setCajas(comprimirEnRangos(cajas, articulo));
            avisarSiElPesoNoSeHaPodidoUsar(articulo, cajas, avisos);
            referencias.add(referencia);
        }
        return referencias;
    }

    /**
     * Cajas consecutivas con las mismas unidades se escriben como un rango, y
     * el resto como cajas sueltas: es el mismo formato que escribiría una
     * persona a mano, y el importador ya sabe leerlo.
     *
     * El peso bruto también tiene que coincidir para poder comprimir. En un
     * rango el importador se lo pone a TODAS sus cajas, así que juntar una
     * llena con una a medias le daría a la de a medias un peso que no es suyo.
     */
    private List<EnvioInput.CajaRangoInput> comprimirEnRangos(
            List<CajaDeArticulo> cajas, ArticuloDestinado articulo) {
        List<EnvioInput.CajaRangoInput> entradas = new ArrayList<>();
        int i = 0;
        while (i < cajas.size()) {
            int inicio = i;
            while (i + 1 < cajas.size()
                    && cajas.get(i + 1).numero() == cajas.get(i).numero() + 1
                    && cajas.get(i + 1).unidades() == cajas.get(inicio).unidades()
                    && Objects.equals(pesoBrutoDe(articulo, cajas.get(i + 1)),
                            pesoBrutoDe(articulo, cajas.get(inicio)))) {
                i++;
            }
            EnvioInput.CajaRangoInput entrada = new EnvioInput.CajaRangoInput();
            if (inicio == i) {
                entrada.setCaja(cajas.get(inicio).numero());
                entrada.setUnidades(cajas.get(inicio).unidades());
            } else {
                entrada.setCajaInicio(cajas.get(inicio).numero());
                entrada.setCajaFin(cajas.get(i).numero());
                entrada.setUnidadesPorCaja(cajas.get(inicio).unidades());
            }
            entrada.setPesoBruto(pesoBrutoDe(articulo, cajas.get(inicio)));
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
