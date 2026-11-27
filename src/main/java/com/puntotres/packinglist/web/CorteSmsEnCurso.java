package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.puntotres.packinglist.service.corte.CorteSmsService.ResultadoSms;
import com.puntotres.packinglist.service.corte.Imagen;
import com.puntotres.packinglist.service.corte.LecturaSmsIa;
import com.puntotres.packinglist.service.corte.LineaSmsRevisada;
import com.puntotres.packinglist.service.corte.NovedadesSms.Novedades;

/**
 * El corte de SMS de esta sesión: la revisión de lo leído con IA (solo los
 * clientes sin lector propio), las hojas generadas para la descarga y las
 * novedades que se pueden entrar en el sistema. Solo el último: no se guarda
 * nada más.
 */
@Component
@SessionScope
public class CorteSmsEnCurso {

    private String claveCliente;
    private String cliente;
    private String temporada;
    private ResultadoSms resultado;
    private Novedades novedades;

    private LecturaSmsIa lectura;
    private String nombreFichero;
    private List<LineaSmsRevisada> lineas = List.of();
    private Map<String, Map<Integer, List<Imagen>>> imagenes = Map.of();
    private List<String> avisosLectura = List.of();

    /** Empieza una revisión: lo leído con IA, aún sin generar. */
    public void empezarRevision(String claveCliente, String cliente, String temporada, String nombreFichero,
                                LecturaSmsIa lectura, Map<String, Map<Integer, List<Imagen>>> imagenes,
                                List<String> avisosImagenes) {
        this.claveCliente = claveCliente;
        this.cliente = cliente;
        this.temporada = temporada;
        this.nombreFichero = nombreFichero;
        this.lectura = lectura;
        this.lineas = LineaSmsRevisada.de(lectura);
        this.imagenes = Map.copyOf(imagenes);
        List<String> avisos = new ArrayList<>(lectura.avisos());
        avisos.addAll(avisosImagenes);
        this.avisosLectura = List.copyOf(avisos);
        this.resultado = null;
        this.novedades = null;
    }

    /** Un pedido de APC, que no pasa por la revisión: la de un pedido anterior ya no vale. */
    public void olvidarRevision() {
        this.lectura = null;
        this.nombreFichero = null;
        this.lineas = List.of();
        this.imagenes = Map.of();
        this.avisosLectura = List.of();
    }

    /** Lo tecleado en la revisión, para no perderlo si hay que volver a ella. */
    public void actualizarLineas(List<LineaSmsRevisada> lineas) {
        this.lineas = List.copyOf(lineas);
    }

    public void guardar(String claveCliente, String cliente, String temporada, ResultadoSms resultado,
                        Novedades novedades) {
        this.claveCliente = claveCliente;
        this.cliente = cliente;
        this.temporada = temporada;
        this.resultado = resultado;
        this.novedades = novedades;
    }

    public boolean enRevision() {
        return lectura != null;
    }

    public String getClaveCliente() {
        return claveCliente;
    }

    public String getCliente() {
        return cliente;
    }

    public String getTemporada() {
        return temporada;
    }

    public ResultadoSms getResultado() {
        return resultado;
    }

    public Novedades getNovedades() {
        return novedades;
    }

    public LecturaSmsIa getLectura() {
        return lectura;
    }

    public String getNombreFichero() {
        return nombreFichero;
    }

    public List<LineaSmsRevisada> getLineas() {
        return lineas;
    }

    public Map<String, Map<Integer, List<Imagen>>> getImagenes() {
        return imagenes;
    }

    public List<String> getAvisosLectura() {
        return avisosLectura;
    }
}
