package com.puntotres.packinglist.shipping.importacion;

import static com.puntotres.packinglist.shipping.importacion.LectorCsv.columna;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.shipping.service.ArticleService;
import com.puntotres.packinglist.shipping.service.DatoInvalidoException;

/**
 * CSV propio del catálogo aduanero: referencia, descripcion, codigo_hs,
 * pais_origen, peso_neto, peso_bruto, valor_unitario. Las tres primeras y el
 * país son obligatorias. La referencia manda: reimportar corrige.
 */
@Service
public class ImportadorArticulosCsv {

    static final String REFERENCIA = "referencia", DESCRIPCION = "descripcion", HS = "codigo_hs", PAIS = "pais_origen",
            NETO = "peso_neto", BRUTO = "peso_bruto", VALOR = "valor_unitario";

    private final ArticleService articulos;

    public ImportadorArticulosCsv(ArticleService articulos) { this.articulos = articulos; }

    public PrevisualizacionArticulos previsualizar(byte[] csv, String nombreFichero) {
        List<CSVRecord> registros = LectorCsv.leer(csv);
        if (registros.isEmpty() || !registros.get(0).isMapped(REFERENCIA) || !registros.get(0).isMapped(HS)) {
            throw new DatoInvalidoException("El CSV de artículos necesita las columnas: referencia, descripcion, codigo_hs, pais_origen, peso_neto, peso_bruto, valor_unitario.");
        }
        List<FilaArticulo> filas = new ArrayList<>();
        int numero = 0;
        for (CSVRecord r : registros) {
            numero++;
            List<String> errores = new ArrayList<>();
            String referencia = columna(r, REFERENCIA), descripcion = columna(r, DESCRIPCION), hs = columna(r, HS), pais = columna(r, PAIS);
            if (referencia == null) errores.add("Sin referencia.");
            if (descripcion == null) errores.add("Sin descripción aduanera.");
            if (hs == null) errores.add("Sin código HS.");
            if (pais == null || pais.length() != 2) errores.add("El país de origen es el código de dos letras.");
            boolean existe = referencia != null && articulos.todos().stream().anyMatch(a -> a.getReferencia().equalsIgnoreCase(referencia));
            filas.add(new FilaArticulo(numero, referencia, descripcion, hs, pais == null ? null : pais.toUpperCase(),
                    decimal(columna(r, NETO)), decimal(columna(r, BRUTO)), importe(columna(r, VALOR)), errores, existe));
        }
        return new PrevisualizacionArticulos(nombreFichero, filas);
    }

    @Transactional
    public ResultadoImportacion importar(PrevisualizacionArticulos prev) {
        int altas = 0, actualizadas = 0;
        List<String> errores = new ArrayList<>();
        for (FilaArticulo f : prev.filas()) {
            if (!f.valida()) { errores.add("Fila " + f.numero() + ": " + String.join(" ", f.errores())); continue; }
            try {
                articulos.guardarPorReferencia(f.referencia(), f.descripcion(), f.codigoHs(), f.paisOrigen(), f.pesoNeto(), f.pesoBruto(), f.valorUnitario());
                if (f.yaExiste()) actualizadas++; else altas++;
            } catch (DatoInvalidoException e) {
                errores.add("Fila " + f.numero() + ": " + e.getMessage());
            }
        }
        return new ResultadoImportacion(altas, actualizadas, 0, errores);
    }

    static Double decimal(String s) {
        if (s == null) return null;
        try { return Double.valueOf(s.replace(',', '.')); } catch (NumberFormatException e) { return null; }
    }

    static BigDecimal importe(String s) {
        if (s == null) return null;
        try { return new BigDecimal(s.replace(',', '.')).setScale(2, java.math.RoundingMode.HALF_UP); } catch (NumberFormatException e) { return null; }
    }
}
