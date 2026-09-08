package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.EnvioInput;
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

    public PreparacionRevisionService(EnvioImportService importador,
                                      ResolutorDestinosPadre resolutorDestinos,
                                      PedidoCompletionService completadorPedidos,
                                      PaletAssignmentService asignadorPalets,
                                      WeightInferenceService inferidorPesos) {
        this.importador = importador;
        this.resolutorDestinos = resolutorDestinos;
        this.completadorPedidos = completadorPedidos;
        this.asignadorPalets = asignadorPalets;
        this.inferidorPesos = inferidorPesos;
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
     * Vuelve a inferir los pesos de todo el envío. Se llama tras cada edición
     * de la revisión porque corregir un tamaño de caja cambia su tara, y esa
     * tara puede propagarse a otras cajas de la misma referencia.
     */
    public void reinferir(EnvioEnCurso envioEnCurso) {
        List<List<CajaData>> cajasPorDestino = new ArrayList<>();
        for (EnvioImportado.DestinoImportado destino : envioEnCurso.getImportado().getDestinos()) {
            cajasPorDestino.add(destino.getDestino().getCajas());
        }
        envioEnCurso.getAvisosInferencia().clear();
        envioEnCurso.getAvisosInferencia().addAll(
                inferidorPesos.inferirPesosDelEnvio(cajasPorDestino).getAvisos());
    }

}
