package com.puntotres.packinglist.service.corte;

import java.util.ArrayList;
import java.util.List;

/**
 * Una línea de un pedido de SMS leído con IA tal como queda en la pantalla de
 * revisión: lo que leyó Claude, corregido por quien la revisa. Solo las
 * {@code incluir} salen en el excel. {@code hoja} y {@code fila} son las del
 * documento, con las que se busca el croquis de la línea.
 */
public record LineaSmsRevisada(boolean incluir, String hoja, Integer fila, String referencia, String nombreModelo,
                               String codigoColor, String descripcionColor, String piel, String colorPiel,
                               String forro, String colorForro, List<PielCorte> combinaciones, Integer muestras) {

    public LineaSmsRevisada {
        hoja = limpio(hoja);
        referencia = limpio(referencia).replaceAll("\\s+", "");
        nombreModelo = limpio(nombreModelo);
        codigoColor = limpio(codigoColor);
        descripcionColor = limpio(descripcionColor);
        piel = limpio(piel);
        colorPiel = limpio(colorPiel);
        forro = limpio(forro);
        colorForro = limpio(colorForro);
        combinaciones = combinaciones == null ? List.of() : combinaciones.stream()
                .filter(c -> c != null && c.existe()).limit(4).toList();
    }

    /**
     * Las líneas de partida de la revisión. Una sin referencia o sin muestras
     * se trae desmarcada: se ve y se decide. El color de la piel principal
     * arranca con el color del bolso, como en las de APC (decisión del
     * usuario: el cortador lee el mismo color arriba y en la piel); si el
     * documento no dice el del bolso, con el de la piel que leyó Claude.
     */
    public static List<LineaSmsRevisada> de(LecturaSmsIa lectura) {
        List<LineaSmsRevisada> lineas = new ArrayList<>();
        for (LecturaSmsIa.Linea linea : lectura.lineas()) {
            ColorCorte color = new ColorCorte(linea.codigoColor(), linea.descripcionColor(), 0);
            boolean incluir = !linea.referencia().isEmpty() && linea.muestras() != null && linea.muestras() > 0;
            lineas.add(new LineaSmsRevisada(incluir, linea.hoja(), linea.fila(), linea.referencia(),
                    linea.nombreModelo(), linea.codigoColor(), linea.descripcionColor(), linea.piel(),
                    color.nombre().isEmpty() ? linea.colorPiel() : color.nombre(), linea.forro(),
                    linea.colorForro(), linea.combinaciones().stream()
                            .map(c -> new PielCorte(c.nombre(), c.color())).toList(),
                    linea.muestras()));
        }
        return lineas;
    }

    /** Lo que impide generar esta línea si está incluida; vacío si nada. */
    public List<String> errores() {
        List<String> errores = new ArrayList<>();
        if (!incluir) {
            return errores;
        }
        if (referencia.isEmpty()) {
            errores.add("falta la referencia");
        }
        if (muestras == null || muestras <= 0) {
            errores.add("faltan las muestras");
        }
        if (codigoColor.isEmpty() && descripcionColor.isEmpty()) {
            errores.add("falta el color");
        }
        return errores;
    }

    public ColorCorte color() {
        return new ColorCorte(codigoColor, descripcionColor, muestras == null ? 0 : muestras);
    }

    public PielesArticulo pieles() {
        return new PielesArticulo(new PielCorte(piel, colorPiel), new PielCorte(forro, colorForro), combinaciones);
    }

    /** «SMS, fila 7», o la hoja sola en un PDF. */
    public String donde() {
        return fila == null ? hoja : hoja + ", fila " + fila;
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.trim();
    }
}
