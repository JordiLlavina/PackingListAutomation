package com.puntotres.packinglist.modelos.api.dto;

import java.util.List;

import com.puntotres.packinglist.persistence.ResumenFoto;

/**
 * Todo lo que enseña la ficha de un modelo.
 *
 * @param temporadas      los nombres de las temporadas en que ha ido
 * @param codigos         los nombres que ha tenido, del más nuevo al más viejo
 * @param documentos      sus documentos, sin bytes, del más nuevo al más viejo
 * @param lineaTiempo     lo que le ha pasado, de lo más nuevo a lo más viejo
 * @param fusionadosDesde los ids de los modelos que se han juntado con este (y
 *                        ya no existen), del más reciente al más antiguo: para
 *                        el «antes era el #12» de la cabecera
 */
public record ModeloFicha(ModeloResumen modelo, List<String> temporadas, List<CodigoHistorico> codigos,
                          List<ResumenFoto> fotos, List<DocumentoResumen> documentos, List<Evento> lineaTiempo,
                          List<Long> fusionadosDesde) {
}
