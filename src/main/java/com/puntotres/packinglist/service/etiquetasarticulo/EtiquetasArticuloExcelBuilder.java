package com.puntotres.packinglist.service.etiquetasarticulo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.puntotres.packinglist.service.etiquetas.BloqueEtiquetaArticulo;
import com.puntotres.packinglist.service.etiquetas.CodigoBarrasEan13;
import com.puntotres.packinglist.service.etiquetas.EtiquetaArticulo;
import com.puntotres.packinglist.service.etiquetas.RejillaEtiquetas;

/**
 * Escribe un excel de etiquetas de artículo: una hoja por HojaEtiquetas y,
 * dentro de cada hoja, la MISMA etiqueta repetida en una rejilla de 4 × 10
 * que cabe justa en un A4 para imprimir, recortar y enganchar.
 *
 * La maquetación de la rejilla vive en RejillaEtiquetas y el contenido de
 * cada bloque en BloqueEtiquetaArticulo (capa común, service/etiquetas);
 * este builder es el orquestador: agrupa las hojas, cachea la imagen del
 * código de barras y escribe el libro.
 *
 * No es específico de AMI: cualquier cliente con esta misma rejilla lo
 * reutiliza pasándole sus HojaEtiquetas ya formateadas.
 */
@Service
public class EtiquetasArticuloExcelBuilder {

    public byte[] generar(List<HojaEtiquetas> hojas) throws IOException {
        if (hojas.isEmpty()) {
            throw new IllegalArgumentException(
                    "No hay ninguna hoja que generar: Excel no abre un libro sin hojas");
        }
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            BloqueEtiquetaArticulo bloques = new BloqueEtiquetaArticulo(libro);
            // POI compara los nombres de hoja con equalsIgnoreCase (locale-
            // independiente): la red de unicidad tiene que usar la misma
            // semántica o dos nombres que solo difieran en mayúsculas
            // ("NOIR" / "Noir") tumban la generación entera.
            Set<String> nombresUsados = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (HojaEtiquetas hoja : hojas) {
                XSSFSheet destino = RejillaEtiquetas.crearHojaMaquetada(libro,
                        RejillaEtiquetas.nombreUnico(nombresUsados, hoja.nombreHoja()),
                        RejillaEtiquetas.BLOQUES_POR_PAGINA);
                rellenar(libro, destino, hoja.etiqueta(), bloques);
            }
            libro.setActiveSheet(0);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private static void rellenar(XSSFWorkbook libro, XSSFSheet hoja,
                                 EtiquetaArticulo etiqueta, BloqueEtiquetaArticulo bloques) {
        int imagen = indiceImagen(libro, etiqueta.ean13());
        XSSFDrawing dibujo = imagen >= 0 ? hoja.createDrawingPatriarch() : null;
        for (int bloque = 0; bloque < RejillaEtiquetas.BLOQUES_POR_PAGINA; bloque++) {
            int base = RejillaEtiquetas.filaBase(bloque);
            for (int izquierda : RejillaEtiquetas.COLUMNAS_IZQUIERDA) {
                bloques.escribirTextos(hoja, base, izquierda, etiqueta);
                if (dibujo != null) {
                    dibujo.createPicture(
                            BloqueEtiquetaArticulo.anclajeCodigo(hoja, base, izquierda), imagen);
                }
            }
        }
    }

    /**
     * Añade el PNG del código de barras al libro UNA vez y devuelve su
     * índice para que los 40 anclajes de la hoja lo reutilicen: así lo
     * guarda Excel en los ficheros del cliente, una imagen y 40 anclajes.
     * -1 si la etiqueta no trae un EAN13 válido.
     */
    private static int indiceImagen(XSSFWorkbook libro, String ean13) {
        Optional<byte[]> png = CodigoBarrasEan13.png(ean13);
        return png.map(bytes -> libro.addPicture(bytes, Workbook.PICTURE_TYPE_PNG)).orElse(-1);
    }
}
