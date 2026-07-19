package com.puntotres.packinglist.service;

import org.springframework.stereotype.Service;
import com.puntotres.packinglist.model.*;
import java.util.*;

@Service
public class VolcadoErpGenerationService {

    public VolcadoErpData generar(List<CajaData> cajas, DatosEnvio envio) {
        // Agrupar por referencia + talla + color (una línea por combinación única)
        Map<String, VolcadoErpLineaBuilder> grupos = new LinkedHashMap<>();
        Map<String, String> colorCodis = new LinkedHashMap<>();  // color → codigo (preserva orden inserción)
        int colorCodiCounter = 1;

        for (CajaData caja : cajas) {
            String key = caja.getReferencia() + "|" + caja.getTalla() + "|" + caja.getCodigoColor();

            if (!grupos.containsKey(key)) {
                grupos.put(key, new VolcadoErpLineaBuilder()
                    .article(caja.getReferencia())
                    .talla(caja.getTalla())
                    .color(caja.getCodigoColor())
                );
            }

            // Registrar color si es nuevo
            if (!colorCodis.containsKey(caja.getCodigoColor())) {
                colorCodis.put(caja.getCodigoColor(), String.format("%03d", colorCodiCounter++));
            }

            // Sumar cantidad a esta línea
            grupos.get(key).addQuantitat(caja.getCantidad());
        }

        // Construir líneas finales con COLORCODI
        List<VolcadoErpLinea> lineas = new ArrayList<>();
        for (VolcadoErpLineaBuilder builder : grupos.values()) {
            String colorCodi = colorCodis.get(builder.getColor());
            lineas.add(builder
                .colorCodi(colorCodi)
                .sistall(1)
                .sisgrup(1)
                .build());
        }

        // Misma sanitización que el nombre del ZIP en /descargar-todo
        String facturaSaneada = envio.getNumeroFactura().replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        String nombreFichero = "Volcado_ERP_" + facturaSaneada + ".xlsx";
        return new VolcadoErpData(lineas, nombreFichero);
    }

    // Inner builder class para facilitar construcción
    private static class VolcadoErpLineaBuilder {
        private String article;
        private String talla;
        private String color;
        private String colorCodi;
        private int sistall;
        private int sisgrup;
        private int quantitat = 0;

        public VolcadoErpLineaBuilder article(String article) { this.article = article; return this; }
        public VolcadoErpLineaBuilder talla(String talla) { this.talla = talla; return this; }
        public VolcadoErpLineaBuilder color(String color) { this.color = color; return this; }
        public VolcadoErpLineaBuilder colorCodi(String colorCodi) { this.colorCodi = colorCodi; return this; }
        public VolcadoErpLineaBuilder sistall(int sistall) { this.sistall = sistall; return this; }
        public VolcadoErpLineaBuilder sisgrup(int sisgrup) { this.sisgrup = sisgrup; return this; }
        public VolcadoErpLineaBuilder addQuantitat(int qty) { this.quantitat += qty; return this; }
        public String getColor() { return color; }

        public VolcadoErpLinea build() {
            return new VolcadoErpLinea(article, talla, colorCodi, color, sistall, sisgrup, quantitat);
        }
    }
}
