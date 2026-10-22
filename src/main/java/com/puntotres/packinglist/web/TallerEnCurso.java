package com.puntotres.packinglist.web;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.service.taller.DigestionTaller;

/**
 * Estado de la entrada por taller entre el paso de carga y el de ajuste (una
 * sesión = una entrega de taller en curso).
 *
 * Guarda los dos excels subidos además de la digestión porque desde la
 * pantalla de revisión se puede volver al ajuste, corregir un cartón y
 * regenerar: sin los ficheros habría que volver a subirlos, y quien está
 * corrigiendo un dato ya no los tiene a mano.
 *
 * La digestión es MUTABLE y se edita en sitio, igual que el envío en curso:
 * lo que teclea el usuario en el paso de ajuste se escribe sobre estos mismos
 * objetos.
 */
@Component
@SessionScope
public class TallerEnCurso {

    private String claveCliente;
    private DatosEnvio cabecera;
    private byte[] excelTaller;
    private String nombreExcelTaller;
    private byte[] excelPedido;
    private String nombreExcelPedido;
    private Integer alturaMaximaPaletCm;
    private DigestionTaller digestion;

    /**
     * El excel de un taller con formato propio ya pasado al formato Puntotres
     * por Claude ("Excel Formato Taller"). Va aparte del {@code excelTaller}
     * porque todavía no es el de la entrega: solo lo es cuando se le da a
     * digerir con ese formato elegido. Mezclarlos haría que un "Formato
     * Puntotres" sin fichero digiriera en silencio una conversión anterior.
     */
    private byte[] excelConvertido;
    private String nombreExcelConvertido;

    public boolean estaVacio() {
        return digestion == null;
    }

    public void reiniciar() {
        excelConvertido = null;
        nombreExcelConvertido = null;
        claveCliente = null;
        cabecera = null;
        excelTaller = null;
        nombreExcelTaller = null;
        excelPedido = null;
        nombreExcelPedido = null;
        alturaMaximaPaletCm = null;
        digestion = null;
    }

    public String getClaveCliente() { return claveCliente; }
    public void setClaveCliente(String claveCliente) { this.claveCliente = claveCliente; }

    public DatosEnvio getCabecera() { return cabecera; }
    public void setCabecera(DatosEnvio cabecera) { this.cabecera = cabecera; }

    public byte[] getExcelTaller() { return excelTaller; }
    public String getNombreExcelTaller() { return nombreExcelTaller; }

    public void setExcelTaller(byte[] contenido, String nombre) {
        this.excelTaller = contenido;
        this.nombreExcelTaller = nombre;
    }

    public byte[] getExcelConvertido() { return excelConvertido; }
    public String getNombreExcelConvertido() { return nombreExcelConvertido; }

    public void setExcelConvertido(byte[] contenido, String nombre) {
        this.excelConvertido = contenido;
        this.nombreExcelConvertido = nombre;
    }

    public byte[] getExcelPedido() { return excelPedido; }
    public String getNombreExcelPedido() { return nombreExcelPedido; }

    public void setExcelPedido(byte[] contenido, String nombre) {
        this.excelPedido = contenido;
        this.nombreExcelPedido = nombre;
    }

    public Integer getAlturaMaximaPaletCm() { return alturaMaximaPaletCm; }

    public void setAlturaMaximaPaletCm(Integer alturaMaximaPaletCm) {
        this.alturaMaximaPaletCm = alturaMaximaPaletCm;
    }

    public DigestionTaller getDigestion() { return digestion; }
    public void setDigestion(DigestionTaller digestion) { this.digestion = digestion; }
}
