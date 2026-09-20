package com.puntotres.packinglist.actividad;

/**
 * Las acciones que se registran. Enum cerrado y enumerable, que es lo que
 * permite agrupar en el dashboard sin adivinar qué cadenas existen.
 */
public enum TipoEvento {

    ENTRADA("Ha entrado"),
    ENVIO_IMPORTADO("Envío importado"),
    EXTRACCION_FOTOS("Hojas leídas de fotos o PDF"),
    PACKING_LIST_GENERADO("Packing lists generados"),
    ETIQUETAS_CAJA_GENERADAS("Etiquetas de caja generadas"),
    PACKING_PUNTOTRES_IMPRESO("Packing Puntotres impreso"),
    VOLCADO_ERP_DESCARGADO("Volcado del ERP descargado"),
    ETIQUETAS_ARTICULO_GENERADAS("Etiquetas de artículo generadas"),
    ESCANDALLOS_PROCESADOS("Escandallos procesados"),
    TARA_EDITADA("Tara de cartón editada"),
    TEMPORADA_GUARDADA("Temporada guardada"),
    TEMPORADA_BORRADA("Temporada borrada"),
    PERMISOS_CAMBIADOS("Permisos cambiados"),
    USUARIO_DESACTIVADO("Usuario desactivado"),
    USUARIO_REACTIVADO("Usuario reactivado"),
    ACCESO_DENEGADO("Acceso denegado");

    private final String rotulo;

    TipoEvento(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Los que cuentan como "documentos que han salido" en el dashboard. */
    public boolean produceDocumentos() {
        return this == PACKING_LIST_GENERADO
                || this == ETIQUETAS_CAJA_GENERADAS
                || this == ETIQUETAS_ARTICULO_GENERADAS
                || this == ESCANDALLOS_PROCESADOS
                || this == VOLCADO_ERP_DESCARGADO
                || this == PACKING_PUNTOTRES_IMPRESO;
    }
}
