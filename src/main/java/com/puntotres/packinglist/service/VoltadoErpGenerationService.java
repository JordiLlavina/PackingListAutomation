package com.puntotres.packinglist.service;

import org.springframework.stereotype.Service;
import com.puntotres.packinglist.model.*;
import java.util.*;

@Service
public class VoltadoErpGenerationService {

    public VoltadoErpData generar(List<CajaData> cajas, DatosEnvio envio) {
        // Agrupar por referencia + talla (una línea por combinación única, agregando colores)
        Map<String, VoltadoErpLineaBuilder> grupos = new LinkedHashMap<>();
        Map<String, String> colorCodis = new LinkedHashMap<>();  // color → codigo (preserva orden inserción)
        int colorCodiCounter = 1;

        for (CajaData caja : cajas) {
            String key = caja.getReferencia() + "|" + caja.getTalla();

            if (!grupos.containsKey(key)) {
                grupos.put(key, new VoltadoErpLineaBuilder()
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
        List<VoltadoErpLinea> lineas = new ArrayList<>();
        for (VoltadoErpLineaBuilder builder : grupos.values()) {
            String colorCodi = colorCodis.get(builder.getColor());
            lineas.add(builder
                .colorCodi(colorCodi)
                .sistall(1)
                .sisgrup(1)
                .build());
        }

        String nombreFichero = "Volcado_ERP_" + envio.getNumeroFactura() + ".xlsx";
        return new VoltadoErpData(lineas, nombreFichero);
    }

    // Inner builder class para facilitar construcción
    private static class VoltadoErpLineaBuilder {
        private String article;
        private String talla;
        private String color;
        private String colorCodi;
        private int sistall;
        private int sisgrup;
        private int quantitat = 0;

        public VoltadoErpLineaBuilder article(String article) { this.article = article; return this; }
        public VoltadoErpLineaBuilder talla(String talla) { this.talla = talla; return this; }
        public VoltadoErpLineaBuilder color(String color) { this.color = color; return this; }
        public VoltadoErpLineaBuilder colorCodi(String colorCodi) { this.colorCodi = colorCodi; return this; }
        public VoltadoErpLineaBuilder sistall(int sistall) { this.sistall = sistall; return this; }
        public VoltadoErpLineaBuilder sisgrup(int sisgrup) { this.sisgrup = sisgrup; return this; }
        public VoltadoErpLineaBuilder addQuantitat(int qty) { this.quantitat += qty; return this; }
        public String getColor() { return color; }

        public VoltadoErpLinea build() {
            return new VoltadoErpLinea(article, talla, colorCodi, color, sistall, sisgrup, quantitat);
        }
    }
}
