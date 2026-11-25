package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.persistence.ArchivoSesionesPacking;
import com.puntotres.packinglist.persistence.ResumenSesionPacking;
import com.puntotres.packinglist.persistence.SesionPackingDatos;
import com.puntotres.packinglist.service.EnvioImportado;

/**
 * El puente entre el envío en curso (sesión HTTP) y las sesiones guardadas
 * (base de datos): guarda el envío de la revisión solo, sin que nadie pulse
 * nada, y lo vuelve a montar al cargar una sesión.
 *
 * Vive en web y no en persistence porque escribe en {@link EnvioEnCurso}, que
 * es estado de sesión, igual que {@link PreparacionRevisionService}.
 */
@Component
public class SesionesGuardadas {

    private static final Logger LOG = LoggerFactory.getLogger(SesionesGuardadas.class);

    private final ArchivoSesionesPacking archivo;
    private final PreparacionRevisionService preparacion;

    public SesionesGuardadas(ArchivoSesionesPacking archivo, PreparacionRevisionService preparacion) {
        this.archivo = archivo;
        this.preparacion = preparacion;
    }

    /**
     * Guarda el envío en curso en su sesión (la crea la primera vez).
     *
     * Un envío de prueba («CON TEXTO») no se guarda. Y un fallo al guardar
     * no para el envío: se apunta para enseñarlo en la revisión y se sigue,
     * que lo tecleado sigue estando en pantalla.
     */
    public void guardar(EnvioEnCurso envio) {
        if (envio.estaVacio() || envio.isDePrueba()) {
            return;
        }
        try {
            envio.setSesionId(archivo.guardar(envio.getSesionId(), facturasDe(envio), destinosDe(envio),
                    aDatos(envio), envio.getExcelPedidoCliente(), envio.getNombreExcelPedidoCliente()));
            envio.setAvisoGuardado(null);
        } catch (RuntimeException e) {
            LOG.error("No se ha podido guardar la sesión del packing list", e);
            envio.setAvisoGuardado("No se ha podido guardar la sesión: lo que hagas en esta pantalla "
                    + "no se podrá recuperar otro día (" + e.getMessage() + ")");
        }
    }

    public List<ResumenSesionPacking> todas() {
        return archivo.todas();
    }

    /**
     * Monta la sesión guardada como envío en curso, lista para la revisión.
     * false si la sesión ya no existe.
     */
    public boolean cargar(Long id, EnvioEnCurso envio) {
        Optional<ArchivoSesionesPacking.SesionCargada> guardada = archivo.cargar(id);
        if (guardada.isEmpty()) {
            return false;
        }
        SesionPackingDatos datos = guardada.get().datos();
        EnvioImportado importado = new EnvioImportado();
        for (SesionPackingDatos.Destino destino : datos.destinos()) {
            if (destino.destino().getCajas() == null) {
                destino.destino().setCajas(new ArrayList<>());
            }
            importado.getDestinos().add(new EnvioImportado.DestinoImportado(destino.destino(),
                    destino.palets() == null ? new ArrayList<>() : new ArrayList<>(destino.palets())));
        }
        importado.getAvisos().addAll(oVacia(datos.avisosImportacion()));

        envio.reiniciar();
        envio.setCabecera(datos.cabecera());
        envio.setImportado(importado);
        envio.setSesionId(guardada.get().id());
        envio.setDeTaller(datos.deTaller());
        envio.getAvisosPalets().addAll(oVacia(datos.avisosPalets()));
        if (guardada.get().excelPedido() != null && guardada.get().excelPedido().length > 0) {
            envio.setExcelPedidoCliente(guardada.get().excelPedido(), guardada.get().nombreExcelPedido());
        }
        for (int[] fila : oVacia(datos.filasDesplegadas())) {
            if (fila != null && fila.length == 2) {
                envio.alternarFilaDesplegada(fila[0], fila[1]);
            }
        }
        // Lo que no se guarda porque se calcula: las cajas sin palet se leen
        // del dato y los avisos de pesos los da la inferencia, que además
        // recoge las taras y los pesos del catálogo tal como están HOY.
        for (EnvioImportado.DestinoImportado destino : importado.getDestinos()) {
            destino.getDestino().getCajas().stream()
                    .filter(caja -> caja.getNumeroPalet() == null)
                    .forEach(envio.getCajasSinPalet()::add);
        }
        preparacion.reinferir(envio);
        return true;
    }

    /** Borra la sesión; devuelve su cliente, o vacío si ya no estaba. */
    public Optional<String> eliminar(Long id, EnvioEnCurso envio) {
        // Si es la del envío en curso, este deja de apuntar a ella: el
        // siguiente guardado abre una sesión nueva en vez de buscar la borrada.
        if (id != null && id.equals(envio.getSesionId())) {
            envio.setSesionId(null);
        }
        return archivo.eliminar(id);
    }

    private static SesionPackingDatos aDatos(EnvioEnCurso envio) {
        List<SesionPackingDatos.Destino> destinos = envio.getImportado().getDestinos().stream()
                .map(destino -> new SesionPackingDatos.Destino(destino.getDestino(), destino.getPalets()))
                .toList();
        return new SesionPackingDatos(envio.getCabecera(), destinos,
                envio.getImportado().getAvisos(), envio.getAvisosPalets(),
                envio.isDeTaller(), envio.filasDesplegadas());
    }

    /** Las facturas del envío, una por destinación y sin repetir: «FA-1 · FA-2». */
    private static String facturasDe(EnvioEnCurso envio) {
        DatosEnvio cabecera = envio.getCabecera();
        return String.join(" · ", envio.getImportado().getDestinos().stream()
                .map(destino -> cabecera.facturaPara(destino.getDestino()))
                .filter(factura -> factura != null && !factura.isBlank())
                .map(String::trim)
                .distinct()
                .toList());
    }

    /** Las destinaciones con sus bultos: «CHINA (12 cajas) · JAPAN (15 cajas)». */
    private static String destinosDe(EnvioEnCurso envio) {
        List<String> partes = new ArrayList<>();
        for (EnvioImportado.DestinoImportado destino : envio.getImportado().getDestinos()) {
            DestinoData datos = destino.getDestino();
            long cajas = datos.getCajas().stream().map(CajaData::getNumeroCaja).distinct().count();
            partes.add(datos.getNombreDestino() + " (" + cajas + (cajas == 1 ? " caja)" : " cajas)"));
        }
        return String.join(" · ", partes);
    }

    private static <T> List<T> oVacia(List<T> lista) {
        return lista == null ? List.of() : lista;
    }
}
