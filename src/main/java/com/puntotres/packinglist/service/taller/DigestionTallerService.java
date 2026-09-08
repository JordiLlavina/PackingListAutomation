package com.puntotres.packinglist.service.taller;

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
        digestion.getAvisos().addAll(taller.avisos());

        List<LineaTaller> lineas = soloLasDelCliente(clienteClave, taller.lineas(), digestion);

        ResultadoObjetivos objetivos = objetivosDe(clienteClave, lineas, excelPedido);
        digestion.getAvisos().addAll(objetivos.getAvisos());
        digestion.getBloqueos().addAll(objetivos.getBloqueos());

        montarGrupos(clienteClave, lineas, objetivos, digestion);
        return digestion;
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

    private static ResultadoObjetivos objetivosDelPropioTaller(String clienteClave,
                                                               List<LineaTaller> lineas,
                                                               boolean faltaElPedido) {
        ResultadoObjetivos resultado = new ResultadoObjetivos();
        if (faltaElPedido) {
            resultado.getAvisos().add("No se ha subido el excel de pedido de " + clienteClave
                    + ": la cantidad a enviar de cada cosa es la que ha llegado del taller, "
                    + "y hay que revisarla");
        }
        // Lo que ha llegado de cada artículo y destinación, ya sumado entre
        // filas repetidas. Se suma AQUÍ y no al fusionar las filas porque el
        // objetivo tiene que ser una propiedad del artículo y la destinación
        // —como en los clientes que sí traen pedido—, no de cada apunte del
        // taller: así fusionar dos filas nunca duplica lo que se envía.
        Map<String, Integer> llegado = new LinkedHashMap<>();
        for (LineaTaller linea : lineas) {
            llegado.merge(claveArticuloDestino(clienteClave, linea), linea.cantidad(),
                    Integer::sum);
        }
        for (LineaTaller linea : lineas) {
            resultado.anadir(linea, new ObjetivoDestino(destinoDelTaller(clienteClave, linea),
                    llegado.get(claveArticuloDestino(clienteClave, linea)), null));
        }
        return resultado;
    }

    /** Un cliente de destino único no escribe destinación: es su propio nombre. */
    private static String destinoDelTaller(String clienteClave, LineaTaller linea) {
        return linea.destinoTaller().isBlank()
                ? clienteClave.toUpperCase(Locale.ROOT)
                : linea.destinoTaller();
    }

    private static String claveArticuloDestino(String clienteClave, LineaTaller linea) {
        return String.join("|", linea.referencia(), linea.color(), linea.talla(),
                destinoDelTaller(clienteClave, linea));
    }

    // --- Montaje de la tabla ---

    private void montarGrupos(String clienteClave, List<LineaTaller> lineas,
                              ResultadoObjetivos objetivos, DigestionTaller digestion) {
        Map<String, GrupoReferencia> grupos = new LinkedHashMap<>();
        Set<String> destinos = new LinkedHashSet<>();

        for (LineaTaller linea : lineas) {
            List<ObjetivoDestino> suyos = objetivos.objetivosDe(linea);
            suyos.forEach(objetivo -> destinos.add(objetivo.destino()));
            avisarSiElTallerDiceOtraDestinacion(linea, suyos, digestion);

            GrupoReferencia grupo = grupos.computeIfAbsent(linea.referencia(),
                    referencia -> nuevoGrupo(clienteClave, referencia, linea));
            // "Sin pedido" lo dice el lector, no el que la fila tenga o no
            // objetivos: en AMI una fila que el pedido no reconoce recibe el PO
            // de sus hermanas de referencia con cantidad cero, así que TIENE
            // objetivos y sigue siendo una fila sin pedido que hay que mirar.
            boolean sinPedido = !objetivos.estaEnElPedido(linea);
            // El mismo artículo escrito en varias filas del taller es UNA fila
            // aquí: una por destinación suya, o dos entregas del mismo color.
            FilaDigerida yaEsta = filaDe(grupo, linea.color(), linea.talla());
            if (yaEsta != null) {
                yaEsta.fusionar(linea.cantidad(), suyos, sinPedido);
            } else {
                grupo.getFilas().add(new FilaDigerida(linea.color(), linea.talla(),
                        linea.cantidad(), suyos, sinPedido));
            }
        }

        if (destinos.isEmpty()) {
            // Ninguna fila ha casado con el pedido. Sin columnas no habría
            // dónde teclear a mano cuánto va a cada sitio, y la pantalla de
            // ajuste se quedaría sin salida: se ofrecen las destinaciones que
            // el cliente tiene declaradas.
            destinos.addAll(destinacionesDeclaradasDe(clienteClave));
        }
        digestion.getGrupos().addAll(grupos.values());
        digestion.getDestinosActivos().addAll(destinos);
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

    /**
     * La cascada del cartón: lo que se usó la última vez, lo que sugiere el
     * taller, y por último el cartón estándar con las unidades sin rellenar.
     */
    private GrupoReferencia nuevoGrupo(String clienteClave, String referencia, LineaTaller linea) {
        Optional<MemoriaReferencias.DatosCaja> recordado =
                memoria.buscar(clienteClave, referencia);
        if (recordado.isPresent()) {
            return new GrupoReferencia(referencia, recordado.get().medidaCaja(),
                    recordado.get().unidadesPorCaja(),
                    brutoDe(recordado.get().pesoNetoKg(), recordado.get().medidaCaja()),
                    OrigenDato.MEMORIA);
        }
        if (linea.unidadesPorCajaTaller() != null && linea.unidadesPorCajaTaller() > 0) {
            return new GrupoReferencia(referencia, reglas.getMedidaCajaPorDefecto(),
                    linea.unidadesPorCajaTaller(), OrigenDato.TALLER);
        }
        return new GrupoReferencia(referencia, reglas.getMedidaCajaPorDefecto(),
                null, OrigenDato.POR_DEFECTO);
    }

    /**
     * La columna DESTINATION del taller es informativa: manda el pedido del
     * cliente. Pero si no coinciden conviene decirlo, porque suele significar
     * que el taller ha etiquetado el material pensando en otro sitio.
     */
    private static void avisarSiElTallerDiceOtraDestinacion(LineaTaller linea,
                                                            List<ObjetivoDestino> objetivos,
                                                            DigestionTaller digestion) {
        String delTaller = linea.destinoTaller();
        if (delTaller.isBlank() || objetivos.isEmpty()) {
            return;
        }
        List<String> delPedido = objetivos.stream().map(ObjetivoDestino::destino).toList();
        boolean cuadra = delPedido.stream()
                .anyMatch(destino -> destino.startsWith(delTaller) || delTaller.startsWith(destino));
        if (!cuadra) {
            digestion.getAvisos().add("En la fila " + linea.fila() + ", el taller apunta '"
                    + delTaller + "' para " + linea.referencia() + " " + linea.color()
                    + ", y el pedido la manda a " + String.join(" y ", delPedido)
                    + ". Manda el pedido");
        }
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
