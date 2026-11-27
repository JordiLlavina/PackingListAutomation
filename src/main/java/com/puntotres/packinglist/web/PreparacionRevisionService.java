package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.EnvioInput;
import com.puntotres.packinglist.persistence.MemoriaReferencias;
import com.puntotres.packinglist.persistence.TablaColores;
import com.puntotres.packinglist.service.ColoresApc;
import com.puntotres.packinglist.service.EnvioImportService;
import com.puntotres.packinglist.service.EnvioImportado;
import com.puntotres.packinglist.service.PaletAssignmentService;
import com.puntotres.packinglist.service.PedidoCompletionService;
import com.puntotres.packinglist.service.ResolutorDestinosPadre;
import com.puntotres.packinglist.service.ResultadoAsignacion;
import com.puntotres.packinglist.service.ResultadoDestinos;
import com.puntotres.packinglist.service.WeightInferenceService;

/**
 * El tramo que va del JSON del envío a la pantalla de revisión: importar,
 * resolver destinaciones hijas, completar pedidos, asignar palets e inferir
 * pesos.
 *
 * Vive aparte porque lo comparten las CUATRO vías de entrada —JSON pegado,
 * formulario, fotos con Claude y packing list de taller— y es idéntico en
 * todas: a partir del {@code EnvioInput} ya no hay nada que distinga de dónde
 * salieron los datos. Copiarlo en cada controlador significaría que un cambio
 * en la cadena habría que hacerlo en cuatro sitios y acordarse de los cuatro.
 *
 * Está en el paquete web y no en service porque escribe en {@link EnvioEnCurso},
 * que es estado de sesión: mover el estado a la capa de dominio para poder
 * mover esta clase sería peor negocio.
 *
 * El ORDEN importa y no es casual: las destinaciones hijas se resuelven a su
 * padre ANTES de asignar palets, para que la asignación y la inferencia
 * trabajen ya sobre las destinaciones definitivas.
 */
@Service
public class PreparacionRevisionService {

    private final EnvioImportService importador;
    private final ResolutorDestinosPadre resolutorDestinos;
    private final PedidoCompletionService completadorPedidos;
    private final PaletAssignmentService asignadorPalets;
    private final WeightInferenceService inferidorPesos;
    private final MemoriaReferencias memoria;
    private final TablaColores tablaColores;

    public PreparacionRevisionService(EnvioImportService importador,
                                      ResolutorDestinosPadre resolutorDestinos,
                                      PedidoCompletionService completadorPedidos,
                                      PaletAssignmentService asignadorPalets,
                                      WeightInferenceService inferidorPesos,
                                      MemoriaReferencias memoria,
                                      TablaColores tablaColores) {
        this.tablaColores = tablaColores;
        this.importador = importador;
        this.resolutorDestinos = resolutorDestinos;
        this.completadorPedidos = completadorPedidos;
        this.asignadorPalets = asignadorPalets;
        this.inferidorPesos = inferidorPesos;
        this.memoria = memoria;
    }

    /**
     * Deja el envío listo en la sesión para la pantalla de revisión.
     *
     * @param excelPedido     el excel de pedido ya leído, o null
     * @param nombreExcelPedido nombre del fichero, para poder nombrarlo en
     *                          la pantalla de resultados
     * @param avisosPrevios   lo que la vía de entrada quiera contar (dudas de
     *                        lectura, sobrantes del reparto...), que se
     *                        muestra encima de los avisos de la importación
     */
    public void preparar(EnvioInput envio, DatosEnvio cabecera, ClienteConfig cliente,
                         byte[] excelPedido, String nombreExcelPedido,
                         List<String> avisosPrevios, EnvioEnCurso envioEnCurso) {
        EnvioImportado importado = importador.importar(envio);
        importado.getAvisos().addAll(0, avisosPrevios);
        // El color va SIEMPRE en mayúsculas, llegue como llegue (la IA lo lee
        // de hojas manuscritas y el taller lo teclea a su manera): «noir» y
        // «NOIR» son el mismo color y partirían en dos una fila de la revisión.
        for (EnvioImportado.DestinoImportado destino : importado.getDestinos()) {
            for (CajaData caja : destino.getDestino().getCajas()) {
                if (caja.getCodigoColor() != null) {
                    caja.setCodigoColor(caja.getCodigoColor().toUpperCase(Locale.ROOT));
                }
            }
        }

        envioEnCurso.reiniciar();
        envioEnCurso.setCabecera(cabecera);
        envioEnCurso.setImportado(importado);
        if (excelPedido != null && excelPedido.length > 0) {
            envioEnCurso.setExcelPedidoCliente(excelPedido, nombreExcelPedido);
        }

        ResultadoDestinos resueltos = resolutorDestinos.resolver(
                importado.getDestinos(), cliente, cabecera.getFechaEnvio());
        importado.getDestinos().clear();
        importado.getDestinos().addAll(resueltos.getDestinos());
        importado.getAvisos().addAll(resueltos.getAvisos());

        // Solo APC completa el pedido: es el único con un excel de pedido del
        // que sacar el número entero a partir de la referencia. Corre aquí y
        // no en los recálculos de la revisión, que pisarían lo tecleado.
        if (cliente.getPlantilla() == TipoPlantilla.APC) {
            importado.getAvisos().addAll(
                    completadorPedidos.completar(importado.getDestinos(), excelPedido).getAvisos());
            // Y el color, como lo escribe el cliente: código-nombre, con la
            // tabla de Colores y Pieles. Detrás de completar, que es quien
            // deja el pedido entero con el que se comprueba el color.
            importado.getAvisos().addAll(normalizarColoresApc(
                    importado.getDestinos(), cabecera.getClaveCliente(), excelPedido));
        }

        for (EnvioImportado.DestinoImportado destino : importado.getDestinos()) {
            ResultadoAsignacion asignacion =
                    asignadorPalets.asignar(destino.getDestino(), destino.getPalets());
            envioEnCurso.getAvisosPalets().addAll(asignacion.getAvisos());
            envioEnCurso.getCajasSinPalet().addAll(asignacion.getCajasSinPalet());
        }
        reinferir(envioEnCurso);
    }

    /**
     * Deja los colores de un envío de APC en la forma del cliente
     * ("LZZ-NOIR") con la tabla de colores de ese cliente
     * ({@link ColoresApc}). Corre al importar, venga el envío de donde venga,
     * y otra vez al generar, por los colores tecleados en la revisión: es
     * idempotente.
     */
    public List<String> normalizarColoresApc(List<EnvioImportado.DestinoImportado> destinos,
                                             String claveCliente, byte[] excelPedido) {
        Map<String, String> colores = new LinkedHashMap<>();
        if (claveCliente != null && !claveCliente.isBlank()) {
            tablaColores.de(claveCliente)
                    .forEach(color -> colores.put(color.getCodigo(), color.getDescripcion()));
        }
        return ColoresApc.normalizar(destinos, colores, excelPedido);
    }

    /**
     * Vuelve a inferir los pesos de todo el envío. Se llama tras cada edición
     * de la revisión porque corregir un tamaño de caja cambia su tara, y esa
     * tara puede propagarse a otras cajas de la misma referencia.
     *
     * Las referencias sin ninguna caja pesada en el envío toman el peso por
     * unidad que recuerda el catálogo de modelos para el cliente del envío.
     */
    public void reinferir(EnvioEnCurso envioEnCurso) {
        List<List<CajaData>> cajasPorDestino = new ArrayList<>();
        for (EnvioImportado.DestinoImportado destino : envioEnCurso.getImportado().getDestinos()) {
            cajasPorDestino.add(destino.getDestino().getCajas());
        }
        String cliente = envioEnCurso.getCabecera().getClaveCliente();
        envioEnCurso.getAvisosInferencia().clear();
        envioEnCurso.getAvisosInferencia().addAll(inferidorPesos.inferirPesosDelEnvio(cajasPorDestino,
                referencia -> memoria.pesoNetoUnidad(cliente, referencia)).getAvisos());
    }

}
