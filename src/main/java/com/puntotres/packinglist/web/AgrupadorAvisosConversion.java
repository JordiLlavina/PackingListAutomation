package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.puntotres.packinglist.service.taller.PackingFormatoPuntotres;
import com.puntotres.packinglist.service.taller.PackingFormatoPuntotres.Aviso;

/**
 * Los avisos de una conversión de excel de taller como los pinta la pantalla
 * de entrada: igual que {@link AgrupadorAvisosEtiquetas}, el ajuste emite uno
 * por fila, que es la verdad del fichero, pero leídos así son la misma
 * frase veinte veces y eso entierra los que hay que atender.
 *
 * Lo que sale: los avisos del fichero entero sueltos (son pocos y van a la
 * vista: totales que no cuadran, un color que falta en la tabla, una hoja
 * que se ha dejado fuera) y, para los de fila, un bloque plegable por
 * <b>consecuencia</b> con el recuento de filas a la vista y el hecho de cada
 * fila dentro. Los informativos no se enseñan: ya van como comentario en el
 * excel, y en pantalla solo se dice cuántos hay.
 *
 * Es decisión de pantalla, y por eso vive en {@code web/}: el ajuste no
 * sabe cómo se leen sus avisos.
 */
public final class AgrupadorAvisosConversion {

    /** Un bloque plegable: la consecuencia, cuántas filas y el detalle de cada una. */
    public record Bloque(String consecuencia, String recuento, List<Linea> filas) {
    }

    public record Linea(String rotulo, String texto) {
    }

    public record Vista(List<String> generales, List<Bloque> bloques, int completados) {
    }

    private AgrupadorAvisosConversion() {
    }

    public static Vista agrupar(PackingFormatoPuntotres packing) {
        List<String> generales = new ArrayList<>();
        Map<String, List<Linea>> porConsecuencia = new LinkedHashMap<>();
        for (Aviso aviso : packing.avisosParaRevisar()) {
            if (aviso.fila() == null) {
                generales.add(aviso.texto());
            } else {
                porConsecuencia.computeIfAbsent(aviso.consecuencia(), c -> new ArrayList<>())
                        .add(new Linea("Fila " + aviso.fila(), aviso.texto()));
            }
        }
        List<Bloque> bloques = new ArrayList<>();
        porConsecuencia.forEach((consecuencia, lineas) -> bloques.add(new Bloque(consecuencia,
                lineas.size() == 1 ? "1 fila" : lineas.size() + " filas", lineas)));
        return new Vista(generales, bloques, packing.avisosInformativos().size());
    }
}
