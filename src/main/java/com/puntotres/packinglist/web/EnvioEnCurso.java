package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.VolcadoErpData;
import com.puntotres.packinglist.service.EnvioImportado;
import com.puntotres.packinglist.service.ExcelGenerado;
import com.puntotres.packinglist.service.etiquetas.AvisoEtiqueta;

/**
 * Estado del asistente entre pantallas (una sesión = un envío en curso).
 *
 * Guarda el envío importado con sus CajaData mutables: la edición de pesos
 * desde la pantalla de revisión y la re-inferencia escriben sobre estos
 * mismos objetos, igual que hacen los servicios de dominio.
 */
@Component
@SessionScope
public class EnvioEnCurso {

    private DatosEnvio cabecera;
    private EnvioImportado importado;
    private final List<String> avisosPalets = new ArrayList<>();
    private final List<String> avisosInferencia = new ArrayList<>();
    private final List<CajaData> cajasSinPalet = new ArrayList<>();
    private final List<ExcelGenerado> excels = new ArrayList<>();
    private final List<String> avisosGeneracion = new ArrayList<>();
    private VolcadoErpData volcadoErp;
    private final List<ExcelGenerado> etiquetas = new ArrayList<>();
    private final List<AvisoEtiqueta> avisosEtiquetas = new ArrayList<>();
    private final Set<ClaveFila> filasDesplegadas = new HashSet<>();
    private byte[] excelPedidoCliente;
    private String nombreExcelPedidoCliente;
    /** La sesión guardada en la que se va escribiendo este envío; null si aún no tiene. */
    private Long sesionId;
    /** El packing lo repartió el programa desde el excel del taller. */
    private boolean deTaller;
    /**
     * El envío entró por «CON TEXTO», que es una vía de PRUEBA: no escribe
     * nada en la base de datos —ni sesión guardada ni pesos aprendidos—.
     */
    private boolean dePrueba;
    /** Por qué no se ha podido guardar la sesión la última vez, o null. */
    private String avisoGuardado;

    /**
     * Una fila compactada de la tabla de revisión que el usuario ha desplegado,
     * identificada por la POSICIÓN de su primera caja dentro de la destinación
     * —como todo en esa pantalla, nunca por número de caja, que se repite en
     * las cajas mixtas.
     */
    private record ClaveFila(int destino, int indice) {
    }

    public boolean estaVacio() {
        return importado == null;
    }

    /** Deja la sesión lista para un envío nuevo. */
    public void reiniciar() {
        cabecera = null;
        importado = null;
        avisosPalets.clear();
        avisosInferencia.clear();
        cajasSinPalet.clear();
        excels.clear();
        avisosGeneracion.clear();
        volcadoErp = null;
        etiquetas.clear();
        avisosEtiquetas.clear();
        filasDesplegadas.clear();
        excelPedidoCliente = null;
        nombreExcelPedidoCliente = null;
        sesionId = null;
        deTaller = false;
        dePrueba = false;
        avisoGuardado = null;
    }

    /** Despliega la fila compactada que arranca ahí, o la vuelve a plegar. */
    public void alternarFilaDesplegada(int destino, int indice) {
        ClaveFila clave = new ClaveFila(destino, indice);
        if (!filasDesplegadas.remove(clave)) {
            filasDesplegadas.add(clave);
        }
    }

    /** Posiciones desplegadas de UNA destinación, tal como las pide el agrupador. */
    public Set<Integer> filasDesplegadasDe(int destino) {
        return filasDesplegadas.stream()
                .filter(clave -> clave.destino() == destino)
                .map(ClaveFila::indice)
                .collect(Collectors.toSet());
    }

    /** Las filas desplegadas como pares (destino, índice), para guardarlas con la sesión. */
    public List<int[]> filasDesplegadas() {
        return filasDesplegadas.stream()
                .map(clave -> new int[] {clave.destino(), clave.indice()})
                .toList();
    }

    public Long getSesionId() { return sesionId; }
    public void setSesionId(Long sesionId) { this.sesionId = sesionId; }

    public boolean isDeTaller() { return deTaller; }
    public void setDeTaller(boolean deTaller) { this.deTaller = deTaller; }

    public boolean isDePrueba() { return dePrueba; }
    public void setDePrueba(boolean dePrueba) { this.dePrueba = dePrueba; }

    public String getAvisoGuardado() { return avisoGuardado; }
    public void setAvisoGuardado(String avisoGuardado) { this.avisoGuardado = avisoGuardado; }

    public DatosEnvio getCabecera() { return cabecera; }
    public void setCabecera(DatosEnvio cabecera) { this.cabecera = cabecera; }

    public EnvioImportado getImportado() { return importado; }
    public void setImportado(EnvioImportado importado) { this.importado = importado; }

    public List<String> getAvisosPalets() { return avisosPalets; }
    public List<String> getAvisosInferencia() { return avisosInferencia; }
    public List<CajaData> getCajasSinPalet() { return cajasSinPalet; }
    public List<ExcelGenerado> getExcels() { return excels; }
    public List<String> getAvisosGeneracion() { return avisosGeneracion; }

    public VolcadoErpData getVolcadoErp() { return volcadoErp; }
    public void setVolcadoErp(VolcadoErpData volcadoErp) { this.volcadoErp = volcadoErp; }

    public List<ExcelGenerado> getEtiquetas() { return etiquetas; }
    public List<AvisoEtiqueta> getAvisosEtiquetas() { return avisosEtiquetas; }

    public byte[] getExcelPedidoCliente() { return excelPedidoCliente; }
    public String getNombreExcelPedidoCliente() { return nombreExcelPedidoCliente; }

    /** El excel de pedido subido en la entrada; lo reutiliza el Paso 2 de etiquetas. */
    public void setExcelPedidoCliente(byte[] contenido, String nombre) {
        this.excelPedidoCliente = contenido;
        this.nombreExcelPedidoCliente = nombre;
    }
}
