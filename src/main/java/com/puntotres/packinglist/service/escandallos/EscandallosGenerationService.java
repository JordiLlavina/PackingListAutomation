package com.puntotres.packinglist.service.escandallos;

import java.util.ArrayList;
import java.util.List;

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
                escandallos.add(lectura.escandallo());
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
}
