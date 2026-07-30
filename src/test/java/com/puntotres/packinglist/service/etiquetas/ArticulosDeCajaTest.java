package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.CajaFisica;

class ArticulosDeCajaTest {

    private static CajaData linea(String referencia, String color, String talla, int cantidad) {
        CajaData caja = new CajaData();
        caja.setNumeroCaja(1);
        caja.setReferencia(referencia);
        caja.setCodigoColor(color);
        caja.setTalla(talla);
        caja.setCantidad(cantidad);
        return caja;
    }

    private static CajaFisica caja(CajaData... lineas) {
        return CajaFisica.de(List.of(lineas));
    }

    @Test
    void unaCajaDeUnSoloArticuloDaUnArticulo() {
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("ULL163.AL0052", "221", null, 50)), false);

        assertEquals(1, articulos.size());
        assertEquals("ULL163.AL0052", articulos.get(0).referencia());
        assertEquals("221", articulos.get(0).codigoColor());
        assertEquals(null, articulos.get(0).talla());
        assertEquals(50, articulos.get(0).cantidad());
    }

    @Test
    void enBolsosLasLineasDelMismoArticuloSeFundenSumandoCantidades() {
        // Sin agrupar por talla, dos líneas de la misma referencia y color son
        // un solo artículo: es el comportamiento de hoy (QUANTITY sumada).
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("ULL163.AL0052", "221", null, 30),
                     linea("ULL163.AL0052", "221", null, 20)), false);

        assertEquals(1, articulos.size());
        assertEquals(50, articulos.get(0).cantidad());
    }

    @Test
    void enBolsosCadaReferenciaOColorEsUnArticuloYSeConservaElOrdenDeLlegada() {
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("ULL163.AL0052", "221", null, 3),
                     linea("ULL753.AL0168", "001", null, 5),
                     linea("ULL163.AL0052", "999", null, 2)), false);

        assertEquals(List.of("ULL163.AL0052", "ULL753.AL0168", "ULL163.AL0052"),
                articulos.stream().map(ArticuloEtiqueta::referencia).toList());
        assertEquals(List.of("221", "001", "999"),
                articulos.stream().map(ArticuloEtiqueta::codigoColor).toList());
    }

    @Test
    void enBolsosLaTallaNoParteElArticulo() {
        // Un bolso no tiene talla; si alguna línea la trajera, se ignora.
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("ULL163.AL0052", "221", "U", 3),
                     linea("ULL163.AL0052", "221", null, 5)), false);

        assertEquals(1, articulos.size());
        assertEquals(8, articulos.get(0).cantidad());
    }

    @Test
    void enCinturonesCadaTallaEsUnArticuloEnOrdenDeLlegada() {
        // Cada talla tiene su propio EAN-13 en el pedido de AMI: son
        // artículos distintos aunque compartan referencia y color.
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("UBL029.AL0216", "001", "95", 33),
                     linea("UBL029.AL0216", "001", "85", 4),
                     linea("UBL029.AL0216", "001", "105", 3)), true);

        assertEquals(List.of("95", "85", "105"),
                articulos.stream().map(ArticuloEtiqueta::talla).toList());
        assertEquals(List.of(33, 4, 3),
                articulos.stream().map(ArticuloEtiqueta::cantidad).toList());
    }

    @Test
    void enCinturonesDosLineasDeLaMismaTallaSeFunden() {
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("UBL029.AL0216", "001", "85", 4),
                     linea("UBL029.AL0216", "001", "85", 6)), true);

        assertEquals(1, articulos.size());
        assertEquals(10, articulos.get(0).cantidad());
    }

    @Test
    void unirConcatenaConBarrasEnOrden() {
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("ULL163.AL0052", "221", null, 3),
                     linea("ULL753.AL0168", "001", null, 5)), false);

        assertEquals("ULL163.AL0052 / ULL753.AL0168",
                ArticulosDeCaja.unir(articulos, ArticuloEtiqueta::referencia));
        assertEquals("3 / 5",
                ArticulosDeCaja.unir(articulos, a -> String.valueOf(a.cantidad())));
    }

    @Test
    void unirRepiteLosValoresIgualesEnVezDeDeduplicar() {
        // Dos artículos distintos con el mismo color code lo repiten: la
        // etiqueta se lee en paralelo, columna a columna.
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("ULL163.AL0052", "221", null, 3),
                     linea("ULL753.AL0168", "221", null, 5)), false);

        assertEquals("221 / 221",
                ArticulosDeCaja.unir(articulos, ArticuloEtiqueta::codigoColor));
    }

    @Test
    void unirDeUnSoloArticuloNoAnadeSeparador() {
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("ULL163.AL0052", "221", null, 50)), false);

        assertEquals("ULL163.AL0052",
                ArticulosDeCaja.unir(articulos, ArticuloEtiqueta::referencia));
    }
}
