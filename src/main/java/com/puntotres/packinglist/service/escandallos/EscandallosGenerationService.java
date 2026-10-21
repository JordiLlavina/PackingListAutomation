package com.puntotres.packinglist.service.escandallos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Punto de entrada del procesado de escandallos: de los ficheros que suelta el
 * ERP al único excel con una hoja por escandallo.
 *
 * Es el sitio donde se aplica la regla del proyecto de no bloquear: un fichero
 * que no se puede leer se queda fuera con un aviso y los demás se procesan
 * igual, porque volver a empezar con los diez buenos por culpa del malo no le
 * sirve a nadie. Solo se queda sin excel el caso en el que no hay ni un
 * escandallo legible.
 */
@Service
public class EscandallosGenerationService {

    /**
     * Materiales del escandallo que no se quieren en el excel descargable, por
     * decisión del usuario: la espuma, el papel y la tinta. Se quitan aquí y no
     * en {@link EscandalloReader}, que sigue leyendo lo que trae el ERP tal
     * cual; y sin aviso, porque salen en casi todos los escandallos.
     */
    static final Set<String> ARTICULOS_EXCLUIDOS = Set.of("R-FOAM", "R-PP00", "R-TT00");

    private final EscandalloReader reader;
    private final EscandallosExcelBuilder builder;

    public EscandallosGenerationService(EscandalloReader reader,
                                        EscandallosExcelBuilder builder) {
        this.reader = reader;
        this.builder = builder;
    }

    public ExcelEscandallos procesar(List<FicheroEscandallo> ficheros) {
        List<Escandallo> escandallos = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        for (FicheroEscandallo fichero : ficheros) {
            try {
                LecturaEscandallo lectura = reader.leer(fichero.contenido(), fichero.nombre());
                escandallos.add(sinExcluidos(lectura.escandallo()));
                avisos.addAll(lectura.avisos());
            } catch (IllegalArgumentException e) {
                // El reader ya redacta sus mensajes en español y con el nombre
                // del fichero delante; se pueden enseñar tal cual.
                avisos.add(e.getMessage());
            }
        }
        if (escandallos.isEmpty()) {
            return new ExcelEscandallos(null, List.of(), List.copyOf(avisos));
        }
        ExcelEscandallos excel = builder.construir(escandallos);
        avisos.addAll(excel.avisos());
        return new ExcelEscandallos(excel.contenido(), excel.hojas(), List.copyOf(avisos));
    }

    private static Escandallo sinExcluidos(Escandallo escandallo) {
        List<LineaEscandallo> lineas = escandallo.lineas().stream()
                .filter(linea -> !ARTICULOS_EXCLUIDOS.contains(
                        linea.article().trim().toUpperCase(Locale.ROOT)))
                .toList();
        return new Escandallo(escandallo.modelo(), escandallo.descripcion(),
                escandallo.color(), lineas, escandallo.origen());
    }
}
