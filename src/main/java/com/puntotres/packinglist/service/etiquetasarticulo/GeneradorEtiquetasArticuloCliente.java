package com.puntotres.packinglist.service.etiquetasarticulo;

import java.io.IOException;

/**
 * Estrategia de generación de etiquetas de ARTÍCULO de un cliente concreto
 * (las que se enganchan al bolso o al cinturón), análoga a
 * GeneradorEtiquetasCliente para las etiquetas de caja.
 *
 * Cada implementación sabe leer el excel de pedido de su cliente, clasificar
 * sus referencias (bolso / cinturón), agruparlas en ficheros y nombrar hojas
 * y ficheros. La maquetación de la etiqueta la pone
 * EtiquetasArticuloExcelBuilder, que es común: un cliente nuevo con la misma
 * rejilla 4 × 10 lo reutiliza y solo aporta esta clase.
 *
 * Un cliente sin implementación simplemente no tiene esta funcionalidad
 * todavía: la pantalla lo marca "en desarrollo".
 */
public interface GeneradorEtiquetasArticuloCliente {

    /** Clave del cliente en el catálogo de application.yml (ej. "AMI"). */
    String claveCliente();

    /** Rótulo del input de archivo, ej. "Introducir excel del pedido de AMI". */
    String tituloCampoPedido();

    /**
     * Genera un excel por grupo con filas.
     *
     * temporadaPorDefecto: la que ha escrito el usuario en la pantalla. Solo
     * se usa si no se puede deducir del propio excel (el nombre de la hoja
     * "EAN H26"), que es la fuente preferente.
     */
    ResultadoEtiquetasArticulo generar(byte[] excelPedido, String temporadaPorDefecto)
            throws IOException;
}
