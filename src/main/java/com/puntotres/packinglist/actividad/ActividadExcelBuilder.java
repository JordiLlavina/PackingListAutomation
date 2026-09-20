package com.puntotres.packinglist.actividad;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * El resultado del filtro del dashboard, en un excel.
 *
 * Sin plantilla .xlsx: la maquetación son las constantes de esta clase, igual
 * que en EscandallosExcelBuilder.
 */
public class ActividadExcelBuilder {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MOMENTO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] construir(ResumenActividad resumen, FiltroActividad filtro) {
        try (Workbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            hojaResumen(libro, resumen, filtro);
            hojaPorUsuario(libro, resumen);
            hojaPorCliente(libro, resumen);
            hojaCronologia(libro, resumen);

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException fallo) {
            throw new UncheckedIOException("No se ha podido montar el excel de actividad", fallo);
        }
    }

    private void hojaResumen(Workbook libro, ResumenActividad resumen, FiltroActividad filtro) {
        Sheet hoja = libro.createSheet("Resumen");
        escribir(hoja, 0, "Actividad del " + filtro.desde().format(DIA)
                + " al " + filtro.hasta().format(DIA));
        escribir(hoja, 2, "Personas", resumen.personas());
        escribir(hoja, 3, "Accesos", resumen.accesos());
        escribir(hoja, 4, "Documentos generados", resumen.documentos());
        escribir(hoja, 5, "Envíos procesados", resumen.envios());

        int fila = 7;
        escribir(hoja, fila++, "Por sección");
        for (ResumenActividad.LineaSeccion linea : resumen.porSeccion()) {
            escribir(hoja, fila++, linea.seccion().getRotulo(), linea.acciones());
        }
        hoja.autoSizeColumn(0);
        hoja.autoSizeColumn(1);
    }

    private void hojaPorUsuario(Workbook libro, ResumenActividad resumen) {
        Sheet hoja = libro.createSheet("Por persona");
        cabecera(hoja, "Correo", "Último acceso", "Accesos", "Acciones");
        int fila = 1;
        for (ResumenActividad.LineaUsuario linea : resumen.porUsuario()) {
            Row f = hoja.createRow(fila++);
            f.createCell(0).setCellValue(linea.correo());
            f.createCell(1).setCellValue(
                    linea.ultimoAcceso() == null ? "" : linea.ultimoAcceso().format(MOMENTO));
            f.createCell(2).setCellValue(linea.accesos());
            f.createCell(3).setCellValue(linea.documentos());
        }
        for (int c = 0; c < 4; c++) {
            hoja.autoSizeColumn(c);
        }
    }

    private void hojaPorCliente(Workbook libro, ResumenActividad resumen) {
        Sheet hoja = libro.createSheet("Por cliente");
        cabecera(hoja, "Cliente", "Documentos", "Acciones");
        int fila = 1;
        for (ResumenActividad.LineaCliente linea : resumen.porCliente()) {
            Row f = hoja.createRow(fila++);
            f.createCell(0).setCellValue(linea.cliente());
            f.createCell(1).setCellValue(linea.documentos());
            f.createCell(2).setCellValue(linea.acciones());
        }
        for (int c = 0; c < 3; c++) {
            hoja.autoSizeColumn(c);
        }
    }

    private void hojaCronologia(Workbook libro, ResumenActividad resumen) {
        Sheet hoja = libro.createSheet("Movimientos");
        cabecera(hoja, "Cuándo", "Quién", "Qué", "Sección", "Cliente", "Cantidad", "Detalle");
        int fila = 1;
        for (EventoActividad evento : resumen.cronologia()) {
            Row f = hoja.createRow(fila++);
            f.createCell(0).setCellValue(evento.getMomento().format(MOMENTO));
            f.createCell(1).setCellValue(evento.getCorreo());
            f.createCell(2).setCellValue(evento.getTipo().getRotulo());
            f.createCell(3).setCellValue(
                    evento.getSeccion() == null ? "" : evento.getSeccion().getRotulo());
            f.createCell(4).setCellValue(evento.getCliente() == null ? "" : evento.getCliente());
            if (evento.getCantidad() != null) {
                f.createCell(5).setCellValue(evento.getCantidad());
            }
            f.createCell(6).setCellValue(evento.getDetalle() == null ? "" : evento.getDetalle());
        }
        for (int c = 0; c < 7; c++) {
            hoja.autoSizeColumn(c);
        }
    }

    private void cabecera(Sheet hoja, String... titulos) {
        Row fila = hoja.createRow(0);
        for (int c = 0; c < titulos.length; c++) {
            fila.createCell(c).setCellValue(titulos[c]);
        }
    }

    private void escribir(Sheet hoja, int fila, String rotulo) {
        hoja.createRow(fila).createCell(0).setCellValue(rotulo);
    }

    private void escribir(Sheet hoja, int fila, String rotulo, long valor) {
        Row f = hoja.createRow(fila);
        f.createCell(0).setCellValue(rotulo);
        f.createCell(1).setCellValue(valor);
    }
}
