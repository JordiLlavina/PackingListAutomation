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

    public DigestionTallerService(List<ObjetivosPedido> objetivosPorCliente,
                                  MemoriaReferencias memoria,
                                  ReglasTallerProperties reglas) {
        this.objetivosPorCliente = objetivosPorCliente;
        this.memoria = memoria;
        this.reglas = reglas;
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

        List<LineaTaller> lineas = taller.lineas();
        comprobarQueTodoEsDelMismoCliente(clienteClave, lineas, digestion);

        ResultadoObjetivos objetivos = objetivosDe(clienteClave, lineas, excelPedido);
        digestion.getAvisos().addAll(objetivos.getAvisos());
        digestion.getBloqueos().addAll(objetivos.getBloqueos());

        montarGrupos(clienteClave, lineas, objetivos, digestion);
        return digestion;
    }

    /**
     * Una hoja con dos clientes distintos casi siempre es un escaneo de más o
     * un cliente mal elegido. Generar con ella mandaría bolsos de un cliente
     * en el packing list de otro, así que para el envío.
     */
    private static void comprobarQueTodoEsDelMismoCliente(String clienteClave,
                                                          List<LineaTaller> lineas,
                                                          DigestionTaller digestion) {
        Set<String> otros = new LinkedHashSet<>();
        for (LineaTaller linea : lineas) {
            if (!linea.cliente().isBlank()
                    && !linea.cliente().equalsIgnoreCase(clienteClave.trim())) {
                otros.add(linea.cliente());
            }
        }
        if (!otros.isEmpty()) {
            digestion.getBloqueos().add("La hoja del taller trae filas de "
                    + String.join(", ", otros) + " y has elegido " + clienteClave
                    + ". Quita esas filas del fichero o elige el cliente correcto");
        }
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
        for (LineaTaller linea : lineas) {
            String destino = linea.destinoTaller().isBlank()
                    ? clienteClave.toUpperCase(Locale.ROOT)
                    : linea.destinoTaller();
            resultado.anadir(linea, new ObjetivoDestino(destino, linea.cantidad(), null));
        }
        return resultado;
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
            grupo.getFilas().add(new FilaDigerida(linea.color(), linea.talla(),
                    linea.cantidad(), suyos, suyos.isEmpty()));
        }

        digestion.getGrupos().addAll(grupos.values());
        digestion.getDestinosActivos().addAll(destinos);
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
                    recordado.get().unidadesPorCaja(), OrigenDato.MEMORIA);
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

    /** Guarda lo aprendido de cada referencia, solo si está completo. */
    public void memorizar(String clienteClave, DigestionTaller digestion) {
        for (GrupoReferencia grupo : digestion.getGrupos()) {
            memoria.recordar(clienteClave, grupo.getReferencia(),
                    grupo.getMedidaCaja(), grupo.getUnidadesPorCaja());
        }
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
