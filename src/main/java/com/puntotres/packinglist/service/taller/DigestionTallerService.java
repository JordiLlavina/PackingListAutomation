package com.puntotres.packinglist.service.taller;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.CatalogoTaras;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.config.ReglasTallerProperties;
import com.puntotres.packinglist.persistence.MemoriaReferencias;

/**
 * Cruza las tres fuentes de la entrada por taller y deja lo que se enseña en
 * la pantalla de ajuste.
 *
 * Cada una aporta una cosa distinta y ninguna sabe lo que saben las otras:
 * <ul>
 * <li>la hoja del <b>taller</b> dice qué ha llegado y cuánto;</li>
 * <li>el excel de <b>pedido</b> dice cuánto quiere el cliente y a dónde va;</li>
 * <li>la <b>memoria</b> dice en qué cartón se empaqueta esa referencia y
 * cuántas unidades le caben, que no está escrito en ningún fichero.</li>
 * </ul>
 *
 * Lo que no cubre ninguna de las tres se queda sin rellenar y bloquea. La
 * alternativa —un valor por defecto que nadie ha mirado— generaría un packing
 * plausible, y el error solo se vería al abrir las cajas.
 */
@Service
public class DigestionTallerService {

    private final List<ObjetivosPedido> objetivosPorCliente;
    private final MemoriaReferencias memoria;
    private final ReglasTallerProperties reglas;
    private final ClientesProperties clientes;
    /** Para pasar de peso bruto (lo que se teclea) a neto (lo que se recuerda). */
    private final CatalogoTaras taras;

    public DigestionTallerService(List<ObjetivosPedido> objetivosPorCliente,
                                  MemoriaReferencias memoria,
                                  ReglasTallerProperties reglas,
                                  ClientesProperties clientes,
                                  CatalogoTaras taras) {
        this.objetivosPorCliente = objetivosPorCliente;
        this.memoria = memoria;
        this.reglas = reglas;
        this.clientes = clientes;
        this.taras = taras;
    }

    /**
     * @param excelPedido puede ser null: los clientes de destino único no lo
     *                    mandan, y entonces el objetivo es lo que ha llegado
     */
    public DigestionTaller digerir(String clienteClave, byte[] excelTaller, byte[] excelPedido)
            throws IOException, TallerColisExcel.TallerExcelException {
        return digerir(clienteClave, excelTaller, excelPedido, null);
    }

    public DigestionTaller digerir(String clienteClave, byte[] excelTaller, byte[] excelPedido,
                                   String nombreHoja)
            throws IOException, TallerColisExcel.TallerExcelException {
        TallerColisExcel taller = TallerColisExcel.desdeBytes(excelTaller, nombreHoja);
        DigestionTaller digestion = new DigestionTaller();
        digestion.getDetalle().addAll(taller.detalleAvisos());
        avisarDeLasColumnasQueFaltanYHacenFalta(clienteClave, taller, digestion);

        List<LineaTaller> lineas = soloLasDelCliente(clienteClave, taller.lineas(), digestion);

        ResultadoObjetivos objetivos = objetivosDe(clienteClave, lineas, excelPedido);
        digestion.getDetalle().addAll(objetivos.getDetalle());
        digestion.getBloqueos().addAll(objetivos.getBloqueos());

        montarGrupos(clienteClave, lineas, objetivos, digestion);
        return digestion;
    }

    /**
     * Avisa de las columnas opcionales que faltan, pero solo de las que este
     * envío va a echar en falta de verdad.
     *
     * El lector de la hoja no puede decidir esto: no sabe de qué cliente es
     * el envío, y de eso depende. Aquí sí se sabe, así que aquí se decide.
     *
     * Dos columnas no avisan nunca, y no es un olvido: el programa <b>no lee
     * su contenido en ningún sitio</b>. "Nº EXPEDITION PUNTOTRES" es
     * trazabilidad del taller, y "Nº DE COLIS" su numeración de cajas, que se
     * descarta a propósito porque el packing se regenera desde cero. Pedir
     * que se rellenen mandaba a arreglar algo que no cambia nada, y un aviso
     * que sale siempre y no sirve para nada empuja hacia abajo los que sí hay
     * que atender.
     */
    private static void avisarDeLasColumnasQueFaltanYHacenFalta(String clienteClave,
                                                                TallerColisExcel taller,
                                                                DigestionTaller digestion) {
        for (TallerColisExcel.Columna columna : taller.opcionalesAusentes()) {
            explicacionDe(columna, clienteClave).ifPresent(porQue -> digestion.avisar(
                    "La hoja del taller no tiene la columna '" + columna.rotulo() + "': " + porQue));
        }
    }

    /** Qué se pierde este cliente sin esa columna, o vacío si no se pierde nada. */
    private static Optional<String> explicacionDe(TallerColisExcel.Columna columna,
                                                  String clienteClave) {
        return switch (columna) {
            case TAILLE -> Optional.of("todas las tallas se toman como talla única");
            case QTITE_COLIS -> Optional.of("habrá que decir cuántas unidades entran en cada caja");
            case DESTINATION -> Optional.of("no se podrá contrastar la destinación con la del "
                    + "pedido");
            // El CODE de tres dígitos solo lo lee APC, donde es lo que dice a
            // qué "Document d'achat" va la fila. Los demás clientes no lo
            // miran, así que echarlo de menos por ellos es mandar a rellenar
            // una columna que no se va a leer.
            case CODE -> ObjetivosPedidoApc.CLIENTE.equalsIgnoreCase(clienteClave)
                    ? Optional.of("sin él no se sabe a qué pedido ni a qué destinación va cada fila")
                    : Optional.empty();
            default -> Optional.empty();
        };
    }

    /**
     * Solo las filas del cliente elegido.
     *
     * El taller trabaja para varios y manda una sola hoja con todos
     * mezclados, así que encontrar otros nombres es lo NORMAL: las de los
     * demás se apartan sin decir nada. Antes se avisaba, y como saltaba en
     * todas las entregas el aviso no informaba de nada —solo empujaba hacia
     * abajo los que sí hay que leer.
     *
     * Lo que sí para el envío es que no quede ninguna fila: ahí el cliente
     * elegido no es el de la hoja, y generar un packing vacío sería peor que
     * decirlo.
     */
    private List<LineaTaller> soloLasDelCliente(String clienteClave, List<LineaTaller> lineas,
                                                DigestionTaller digestion) {
        List<LineaTaller> suyas = new ArrayList<>();
        for (LineaTaller linea : lineas) {
            if (esDelCliente(clienteClave, linea)) {
                suyas.add(linea);
            }
        }
        if (suyas.isEmpty() && !lineas.isEmpty()) {
            digestion.getBloqueos().add("En la hoja del taller no hay ninguna fila de "
                    + clienteClave + ". Revisa el cliente elegido o el fichero subido");
        }
        return suyas;
    }

    /** Una fila sin cliente escrito se da por del cliente elegido. */
    private boolean esDelCliente(String clienteClave, LineaTaller linea) {
        if (linea.cliente().isBlank()) {
            return true;
        }
        String suyo = canonico(linea.cliente());
        return suyo.equals(canonico(clienteClave)) || suyo.equals(canonico(nombreDe(clienteClave)));
    }

    /**
     * Nombre de cliente comparable: solo letras y dígitos, en mayúsculas. El
     * taller escribe "A.P.C." donde el catálogo dice "APC", y comparando al
     * pie de la letra el envío entero se quedaría fuera.
     */
    private static String canonico(String nombre) {
        return nombre == null ? "" : nombre.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private String nombreDe(String clienteClave) {
        return clientes.clientePara(clienteClave)
                .map(ClienteConfig::getNombre)
                .orElse(clienteClave);
    }

    /**
     * Los objetivos del cliente, o los del propio taller si ese cliente no
     * tiene lector de pedido: destino único y lo que ha llegado, que el
     * usuario ajusta a mano.
     */
    private ResultadoObjetivos objetivosDe(String clienteClave, List<LineaTaller> lineas,
                                           byte[] excelPedido) {
        Optional<ObjetivosPedido> lector = objetivosPorCliente.stream()
                .filter(objetivos -> objetivos.clienteSoportado().equalsIgnoreCase(clienteClave))
                .findFirst();
        if (lector.isEmpty() || excelPedido == null || excelPedido.length == 0) {
            return objetivosDelPropioTaller(clienteClave, lineas, lector.isPresent());
        }
        return lector.get().objetivosPara(lineas, excelPedido);
    }

    private ResultadoObjetivos objetivosDelPropioTaller(String clienteClave,
                                                        List<LineaTaller> lineas,
                                                        boolean faltaElPedido) {
        ResultadoObjetivos resultado = new ResultadoObjetivos();
        if (faltaElPedido) {
            resultado.avisar("No se ha subido el excel de pedido de " + clienteClave
                    + ": la cantidad a enviar de cada cosa es la que ha llegado del taller, "
                    + "y hay que revisarla");
        }
        // Cada fila con lo suyo y su destinación. Ya no se pre-suman las
        // filas repetidas del mismo artículo: eso lo hace la fusión, que es
        // donde se juntan, desde que lo que se enseña es lo que manda el
        // taller y no lo que pide el cliente.
        for (LineaTaller linea : lineas) {
            resultado.anadir(linea, new ObjetivoDestino(destinoDelTaller(clienteClave, linea),
                    linea.cantidad(), null));
        }
        return resultado;
    }

    /**
     * La destinación de una fila cuando no hay pedido que la diga.
     *
     * Un cliente de destino único no escribe destinación: es su propio
     * nombre. Y lo que escriba se pasa por el catálogo del cliente, porque el
     * taller la abrevia ("WH", "UST") y ese nombre corto no está declarado en
     * ningún sitio: sin resolverlo, la destinación se quedaría sin norma de
     * reparto y bloquearía el envío entero por un nombre.
     */
    private String destinoDelTaller(String clienteClave, LineaTaller linea) {
        if (linea.destinoTaller().isBlank()) {
            return clienteClave.toUpperCase(Locale.ROOT);
        }
        return clientes.clientePara(clienteClave)
                .flatMap(cliente -> cliente.destinoPadrePara(linea.destinoTaller()))
                .map(ClienteConfig.DestinoResuelto::nombrePadre)
                .orElse(linea.destinoTaller());
    }

    // --- Montaje de la tabla ---

    private void montarGrupos(String clienteClave, List<LineaTaller> lineas,
                              ResultadoObjetivos objetivos, DigestionTaller digestion) {
        Map<String, GrupoReferencia> grupos = new LinkedHashMap<>();
        Set<String> destinos = new LinkedHashSet<>();
        // La caja de una referencia se lee de TODAS sus filas y no de la
        // primera: la que la describe es la más llena, y esa puede estar la
        // última. Por eso se resuelve antes de recorrerlas.
        Map<String, CajaDelTaller> cajas = cajasDelTaller(lineas);

        // Lo que pide el pedido de cada fila, para poder decirlo después: en
        // la tabla se enseña lo que manda el taller, así que el número del
        // pedido no se ve en ningún sitio.
        Map<FilaDigerida, Map<String, Integer>> pedidoDeCadaFila = new LinkedHashMap<>();

        for (LineaTaller linea : lineas) {
            List<ObjetivoDestino> delPedido = objetivos.objetivosDe(linea);
            delPedido.forEach(objetivo -> destinos.add(objetivo.destino()));
            avisarSiElTallerDiceOtraDestinacion(clienteClave, linea, delPedido, digestion);
            List<ObjetivoDestino> suyos = loQueMandaLaLinea(clienteClave, linea, delPedido,
                    digestion);

            GrupoReferencia grupo = grupos.computeIfAbsent(linea.referencia(),
                    referencia -> nuevoGrupo(clienteClave, referencia,
                            cajas.getOrDefault(referencia, CajaDelTaller.VACIA), digestion));
            // "Sin pedido" lo dice el lector, no el que la fila tenga o no
            // objetivos: en AMI una fila que el pedido no reconoce recibe el PO
            // de sus hermanas de referencia con cantidad cero, así que TIENE
            // objetivos y sigue siendo una fila sin pedido que hay que mirar.
            boolean sinPedido = !objetivos.estaEnElPedido(linea);
            // El mismo artículo escrito en varias filas del taller es UNA fila
            // aquí: una por destinación suya, o dos entregas del mismo color.
            FilaDigerida yaEsta = filaDe(grupo, linea.color(), linea.talla());
            FilaDigerida fila = yaEsta;
            if (yaEsta != null) {
                yaEsta.fusionar(linea.cantidad(), suyos, sinPedido);
            } else {
                fila = new FilaDigerida(linea.color(), linea.talla(),
                        linea.cantidad(), suyos, sinPedido);
                grupo.getFilas().add(fila);
            }
            anotarLoPedido(pedidoDeCadaFila, fila, delPedido);
        }

        if (destinos.isEmpty()) {
            // Ninguna fila ha casado con el pedido. Sin columnas no habría
            // dónde teclear a mano cuánto va a cada sitio, y la pantalla de
            // ajuste se quedaría sin salida: se ofrecen las destinaciones que
            // el cliente tiene declaradas.
            destinos.addAll(destinacionesDeclaradasDe(clienteClave));
        }
        // Al final y no dentro del bucle: una fila no está entera hasta que se
        // han fusionado todas sus apariciones en la hoja del taller.
        avisarDeLoQuePideElPedido(grupos.values(), pedidoDeCadaFila, digestion);
        digestion.getGrupos().addAll(grupos.values());
        digestion.getDestinosActivos().addAll(destinos);
    }

    /**
     * Las unidades de UNA fila del taller puestas en <b>su</b> destinación.
     *
     * La tabla del ajuste enseña lo que ha mandado el taller, no lo que pide
     * el cliente. Del pedido se conservan la destinación y el número, que es
     * lo que el taller no sabe; la cantidad la dice la hoja del taller, que es
     * lo que de verdad ha llegado al almacén.
     *
     * <p><b>Cada fila va entera a su destinación y no se reparte.</b> La hoja
     * del taller trae una fila por destinación —su columna DESTINATION en AMI,
     * su CODE en APC— así que no hay nada que repartir: repartir lo recibido
     * entre las destinaciones del pedido pondría en la casilla de un sitio
     * unidades que el taller ha mandado a otro, y quien lo lea no tiene forma
     * de saber de dónde ha salido ese número.
     *
     * <p>Las demás destinaciones de esa referencia se quedan a <b>cero y no
     * desaparecen</b>: son las columnas donde se teclea lo que vaya en otra
     * entrega, y llevan ya puesto su número de pedido.
     */
    private List<ObjetivoDestino> loQueMandaLaLinea(String clienteClave, LineaTaller linea,
                                                    List<ObjetivoDestino> delPedido,
                                                    DigestionTaller digestion) {
        if (delPedido.isEmpty()) {
            return List.of();
        }
        int suya = indiceDeSuDestinacion(clienteClave, linea, delPedido);
        if (suya < 0) {
            digestion.avisarDe(linea.referencia(), "En la fila " + linea.fila() + ", "
                    + linea.referencia() + " " + linea.color() + " no dice a qué destinación va, "
                    + "y el pedido la tiene para " + destinacionesDe(delPedido)
                    + ": hay que escribir a mano cuánto va a cada una");
        }
        List<ObjetivoDestino> suyos = new ArrayList<>();
        for (int i = 0; i < delPedido.size(); i++) {
            ObjetivoDestino objetivo = delPedido.get(i);
            suyos.add(new ObjetivoDestino(objetivo.destino(),
                    i == suya ? linea.cantidad() : 0, objetivo.pedido()));
        }
        return suyos;
    }

    /**
     * Cuál de las destinaciones del pedido es la de esta fila.
     *
     * Con una sola no hay nada que decidir, y es el caso de APC: su CODE es un
     * "Document d'achat", que ya es una destinación. Con varias —en AMI una
     * referencia puede estar pedida para tres— manda la columna DESTINATION de
     * la hoja, que el taller escribe abreviada. Si no lo dice o no casa con
     * ninguna, se devuelve -1: nadie inventa a dónde va un bulto.
     */
    private int indiceDeSuDestinacion(String clienteClave, LineaTaller linea,
                                      List<ObjetivoDestino> delPedido) {
        if (delPedido.size() == 1) {
            return 0;
        }
        String delTaller = linea.destinoTaller();
        if (delTaller.isBlank()) {
            return -1;
        }
        for (int i = 0; i < delPedido.size(); i++) {
            String destino = delPedido.get(i).destino();
            if (destino.startsWith(delTaller) || delTaller.startsWith(destino)
                    || mismoDestinoDelCatalogo(clienteClave, delTaller, destino)) {
                return i;
            }
        }
        return -1;
    }

    private static String destinacionesDe(List<ObjetivoDestino> objetivos) {
        return String.join(" y ", objetivos.stream()
                .map(ObjetivoDestino::destino)
                .distinct()
                .toList());
    }

    /** Lo que el pedido pide de una fila, por destinación y sin sumar dos veces. */
    private static void anotarLoPedido(Map<FilaDigerida, Map<String, Integer>> pedidoDeCadaFila,
                                       FilaDigerida fila, List<ObjetivoDestino> delPedido) {
        // putIfAbsent y no suma: lo pedido a una destinación es una propiedad
        // del artículo y del sitio, no de cuántas veces lo haya escrito el
        // taller. Sumarlo multiplicaría el pedido por el número de filas.
        Map<String, Integer> suyo = pedidoDeCadaFila.computeIfAbsent(fila,
                clave -> new LinkedHashMap<>());
        for (ObjetivoDestino objetivo : delPedido) {
            suyo.putIfAbsent(objetivo.destino(), objetivo.cantidad());
        }
    }

    /**
     * Dice cuánto pedía el cliente cuando no es lo que ha llegado.
     *
     * En la tabla se ve lo que manda el taller, así que el número del pedido
     * no sale por ningún sitio, y es el que dice cuánto queda por servir —o
     * cuánto se está enviando de más—. Salta en los dos sentidos y no bloquea:
     * una entrega parcial es normal, y que el taller mande de más también
     * pasa; lo que no puede es no verse.
     */
    private static void avisarDeLoQuePideElPedido(
            Collection<GrupoReferencia> grupos,
            Map<FilaDigerida, Map<String, Integer>> pedidoDeCadaFila,
            DigestionTaller digestion) {
        for (GrupoReferencia grupo : grupos) {
            for (FilaDigerida fila : grupo.getFilas()) {
                int pedido = pedidoDeCadaFila.getOrDefault(fila, Map.of()).values().stream()
                        .mapToInt(Integer::intValue).sum();
                if (pedido == fila.getRecibido() || pedido == 0) {
                    continue;
                }
                digestion.avisarDe(grupo.getReferencia(), grupo.getReferencia() + " "
                        + fila.getColor() + tallaDe(fila) + ": el pedido pide " + pedido
                        + " y del taller han llegado " + fila.getRecibido()
                        + ". Se envía lo que ha llegado");
            }
        }
    }

    private static String tallaDe(FilaDigerida fila) {
        return "U".equals(fila.getTalla()) ? "" : " talla " + fila.getTalla();
    }

    /**
     * La fila del grupo que ya lleva ese color y esa talla, o null.
     *
     * La talla separa a propósito: cada talla de un cinturón es un artículo
     * con su propio EAN y su propia línea de pedido, así que fusionarlas
     * perdería el dato.
     */
    private static FilaDigerida filaDe(GrupoReferencia grupo, String color, String talla) {
        return grupo.getFilas().stream()
                .filter(fila -> fila.getColor().equals(color) && fila.getTalla().equals(talla))
                .findFirst()
                .orElse(null);
    }

    /**
     * Las destinaciones del bloque de normas del cliente. Un cliente sin
     * bloque es de destino único y ese destino es su propio nombre, que es lo
     * que ya hace {@code objetivosDelPropioTaller}.
     */
    private List<String> destinacionesDeclaradasDe(String clienteClave) {
        return reglas.clienteTaller(clienteClave)
                .map(regla -> List.copyOf(regla.getDestinos().keySet()))
                .filter(declaradas -> !declaradas.isEmpty())
                .orElseGet(() -> List.of(clienteClave.toUpperCase(Locale.ROOT)));
    }

    /** Lo que la hoja del taller dice de la caja de cada referencia. */
    private static Map<String, CajaDelTaller> cajasDelTaller(List<LineaTaller> lineas) {
        Map<String, List<LineaTaller>> porReferencia = new LinkedHashMap<>();
        for (LineaTaller linea : lineas) {
            porReferencia.computeIfAbsent(linea.referencia(), r -> new ArrayList<>()).add(linea);
        }
        Map<String, CajaDelTaller> cajas = new LinkedHashMap<>();
        porReferencia.forEach((referencia, suyas) -> cajas.put(referencia, CajaDelTaller.de(suyas)));
        return cajas;
    }

    /**
     * La cascada de la caja de una referencia: cartón, unidades por caja y
     * peso bruto de una caja llena.
     *
     * <p><b>El cartón y el peso los manda el taller.</b> Desde que su hoja
     * trae "DIMENSIONS CAISSE" y "POIDS BRUT CAISSE", ese es el bulto que
     * acaba de salir de allí y el peso que alguien acaba de poner en la
     * báscula; la memoria dice lo del envío anterior, que es lo que vale
     * cuando la hoja no lo trae. Si los dos lo dicen y no coinciden se avisa
     * en vez de elegir a escondidas: un cartón distinto cambia la tara, el
     * volumen y cuántas cajas caben en un palet.
     *
     * <p><b>Las unidades por caja siguen siendo de la memoria</b>, que es
     * donde está lo que una persona dio por bueno; las del taller son una
     * sugerencia. Esa regla es anterior a estas columnas y no cambia.
     */
    private GrupoReferencia nuevoGrupo(String clienteClave, String referencia,
                                       CajaDelTaller taller, DigestionTaller digestion) {
        Optional<MemoriaReferencias.DatosCaja> recordado =
                memoria.buscar(clienteClave, referencia);
        String medidaTaller = normalizada(taller.medidaCaja());

        String medida = medidaTaller != null ? medidaTaller
                : recordado.map(MemoriaReferencias.DatosCaja::medidaCaja)
                        .orElse(reglas.getMedidaCajaPorDefecto());
        Integer unidades = recordado.map(MemoriaReferencias.DatosCaja::unidadesPorCaja)
                .orElse(taller.unidadesPorCaja());
        // El peso de la memoria se guarda en neto, así que hay que volver a
        // ponerle el cartón, y el de AHORA: si la referencia ha cambiado de
        // caja, la tara de la anterior ya no es la suya.
        Double pesoMemoria = brutoDe(recordado.map(MemoriaReferencias.DatosCaja::pesoNetoKg)
                .orElse(null), medida);
        Double peso = taller.pesoBrutoKg() != null ? taller.pesoBrutoKg() : pesoMemoria;

        avisarSiNoCuadranTallerYMemoria(referencia, taller, medidaTaller, recordado.orElse(null),
                pesoMemoria, unidades, digestion);

        return new GrupoReferencia(referencia, medida, unidades, peso,
                origenDe(recordado.isPresent(), taller));
    }

    /**
     * De dónde sale lo que se enseña. La memoria manda mientras exista, y el
     * taller cuenta en cuanto dice algo de la caja —no solo unidades por
     * caja, como antes de que su hoja trajera el cartón y el peso—.
     */
    private static OrigenDato origenDe(boolean hayMemoria, CajaDelTaller taller) {
        if (hayMemoria) {
            return OrigenDato.MEMORIA;
        }
        return taller.noDiceNada() ? OrigenDato.POR_DEFECTO : OrigenDato.TALLER;
    }

    /**
     * Los avisos de la caja: breves, porque se leen encima de la tarjeta de
     * su referencia y allí ya se ve lo que ha quedado puesto.
     *
     * Son tres cosas distintas y por eso son tres frases: el cartón no cuadra,
     * el peso no cuadra, o el peso que trae la hoja es de una caja que no iba
     * llena —y ese no se arregla eligiendo otra fuente, porque no hay ninguna
     * otra: hay que mirarlo—.
     */
    private static void avisarSiNoCuadranTallerYMemoria(String referencia, CajaDelTaller taller,
                                                        String medidaTaller,
                                                        MemoriaReferencias.DatosCaja recordado,
                                                        Double pesoMemoria, Integer unidades,
                                                        DigestionTaller digestion) {
        if (recordado != null && medidaTaller != null
                && !medidaTaller.equals(normalizada(recordado.medidaCaja()))) {
            digestion.avisarDe(referencia, referencia + ": el taller dice cartón " + medidaTaller
                    + " y la última vez fue " + recordado.medidaCaja()
                    + ". Se usa el del taller");
        }
        if (taller.pesoBrutoKg() != null && pesoMemoria != null
                && !enKilos(taller.pesoBrutoKg()).equals(enKilos(pesoMemoria))) {
            digestion.avisarDe(referencia, referencia + ": el taller pesa la caja en "
                    + enKilos(taller.pesoBrutoKg()) + " kg y la última vez fueron "
                    + enKilos(pesoMemoria) + " kg. Se usa el del taller");
        }
        // El peso que se enseña es el de una caja LLENA, y el programa escala
        // con él las que van a medias. Si la fila de la que sale llevaba
        // menos unidades de las que van a ir por caja, no es un peso de menos:
        // es el peso de otro bulto, y escalarlo daría un número inventado.
        if (taller.pesoBrutoKg() != null && unidades != null
                && taller.unidadesDeLaPesada() != null
                && !taller.unidadesDeLaPesada().equals(unidades)) {
            digestion.avisarDe(referencia, referencia + ": el peso del taller es de una caja de "
                    + taller.unidadesDeLaPesada() + " y aquí van " + unidades
                    + " por caja. Compruébalo");
        }
    }

    /** Un peso en kilos tal como se lee en español: "10", "9,5". */
    private static String enKilos(double kilos) {
        return BigDecimal.valueOf(Math.round(kilos * 100.0) / 100.0)
                .stripTrailingZeros().toPlainString().replace('.', ',');
    }

    /**
     * El cartón escrito por el taller, con la forma del catálogo de taras:
     * allí es "60x40x40" y en la hoja puede venir "60X40X40". Sin esto el
     * desplegable de la pantalla de ajuste no encontraría la opción y el
     * navegador elegiría la primera, cambiando el cartón en silencio.
     */
    private static String normalizada(String medidaCaja) {
        return medidaCaja == null || medidaCaja.isBlank()
                ? null : CatalogoTaras.normalizar(medidaCaja);
    }

    /**
     * La columna DESTINATION del taller es informativa: manda el pedido del
     * cliente. Pero si no coinciden conviene decirlo, porque suele significar
     * que el taller ha etiquetado el material pensando en otro sitio.
     *
     * <p>Lo que hay que comparar es el SITIO, no el texto. El taller escribe
     * la abreviatura del destino padre ("UST", "WH") y el pedido nombra la
     * destinación hija ("Douanes USA", "Australia"), así que las dos se
     * resuelven contra el catálogo del cliente y se comparan los padres. Sin
     * eso saltaba un aviso por fila diciendo que no cuadra lo que sí cuadra,
     * y un aviso que sale siempre entierra a los que hay que leer.
     *
     * <p>El prefijo sigue valiendo para los clientes sin catálogo de
     * destinos: en AMI el taller apunta "CH", "JA" y "PA" y el pedido dice
     * CHINA, JAPAN y PARIS.
     */
    private void avisarSiElTallerDiceOtraDestinacion(String clienteClave, LineaTaller linea,
                                                     List<ObjetivoDestino> objetivos,
                                                     DigestionTaller digestion) {
        String delTaller = linea.destinoTaller();
        if (delTaller.isBlank() || objetivos.isEmpty()) {
            return;
        }
        List<String> delPedido = objetivos.stream().map(ObjetivoDestino::destino).toList();
        boolean cuadra = delPedido.stream()
                .anyMatch(destino -> destino.startsWith(delTaller) || delTaller.startsWith(destino)
                        || mismoDestinoDelCatalogo(clienteClave, delTaller, destino));
        if (!cuadra) {
            digestion.avisarDe(linea.referencia(), "En la fila " + linea.fila()
                    + ", el excel tiene destino '"
                    + delTaller + "' para " + linea.referencia() + " " + linea.color()
                    + ", y el pedido tiene destino " + String.join(" y ", delPedido)
                    + ". Manda el pedido");
        }
    }

    /**
     * Los dos nombres apuntan al mismo destino del catálogo del cliente, sea
     * cada uno una clave, una hija o una abreviatura. Un cliente sin catálogo
     * (AMI, los genéricos) no resuelve nada y decide el prefijo.
     */
    private boolean mismoDestinoDelCatalogo(String clienteClave, String unNombre, String otro) {
        return clientes.clientePara(clienteClave)
                .flatMap(cliente -> cliente.destinoPadrePara(unNombre)
                        .map(ClienteConfig.DestinoResuelto::nombrePadre)
                        .flatMap(padre -> cliente.destinoPadrePara(otro)
                                .map(ClienteConfig.DestinoResuelto::nombrePadre)
                                .map(padre::equals)))
                .orElse(false);
    }

    /**
     * Guarda lo aprendido de cada referencia, solo si está completo.
     *
     * Del peso se guarda el NETO, no el bruto que se teclea: el neto es de la
     * mercancía y no cambia, mientras que el bruto lleva dentro la tara del
     * cartón, que se corrige cada vez que se vuelve a pesar y que cambia
     * entera si la referencia pasa a otra caja. Sin tara conocida no hay forma
     * de separarlos, y entonces no se guarda peso: mejor pedirlo otra vez que
     * recordar un número que no es el que se cree.
     */
    public void memorizar(String clienteClave, DigestionTaller digestion) {
        for (GrupoReferencia grupo : digestion.getGrupos()) {
            memoria.recordar(clienteClave, grupo.getReferencia(), grupo.getMedidaCaja(),
                    grupo.getUnidadesPorCaja(),
                    netoDe(grupo.getPesoBrutoKg(), grupo.getMedidaCaja()));
        }
    }

    /** Lo que pesa la mercancía de una caja llena, quitándole el cartón. */
    private Double netoDe(Double pesoBrutoKg, String medidaCaja) {
        return conTara(medidaCaja, pesoBrutoKg, (peso, tara) -> peso - tara);
    }

    /** Lo contrario: el bruto que se enseña en pantalla, con el cartón puesto. */
    private Double brutoDe(Double pesoNetoKg, String medidaCaja) {
        return conTara(medidaCaja, pesoNetoKg, (peso, tara) -> peso + tara);
    }

    /**
     * Aplica la tara del cartón a un peso, o devuelve null si falta alguno de
     * los dos o si la cuenta no da un peso positivo. Un cero o un negativo
     * significa que el peso tecleado no llega ni a lo que pesa el cartón
     * vacío, o sea que está mal: no se guarda ni se enseña.
     */
    private Double conTara(String medidaCaja, Double peso,
                           java.util.function.DoubleBinaryOperator operacion) {
        if (peso == null || medidaCaja == null) {
            return null;
        }
        Optional<Double> tara = taras.taraPara(medidaCaja);
        if (tara.isEmpty()) {
            return null;
        }
        double resultado = operacion.applyAsDouble(peso, tara.get());
        // Dos decimales: es lo que admite la pantalla y lo que se escribe en
        // el packing list; más cifras solo serían ruido de coma flotante.
        return resultado > 0 ? Math.round(resultado * 100.0) / 100.0 : null;
    }

    /** Para poder ofrecer al usuario elegir la hoja cuando no se encuentra. */
    public List<String> hojasDe(byte[] excelTaller) {
        try {
            TallerColisExcel.desdeBytes(excelTaller);
            return List.of(TallerColisExcel.HOJA_COLIS);
        } catch (TallerColisExcel.HojaNoEncontradaException e) {
            return e.hojasEncontradas();
        } catch (IOException | TallerColisExcel.TallerExcelException e) {
            return new ArrayList<>();
        }
    }
}
