package com.puntotres.packinglist.service.taller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.config.ReglaClienteTaller;
import com.puntotres.packinglist.config.ReglaDestinoTaller;
import com.puntotres.packinglist.config.ReglasTallerProperties;
import com.puntotres.packinglist.config.TipoMezcla;

/**
 * Cuando no llega género para todas las destinaciones, alguien se queda
 * corto. Quién, lo decide la prioridad del cliente y no el orden en que
 * aparezcan las filas.
 */
class RepartoDestinacionesTest {

    /** AMI: CHINA y JAPAN empatan en cabeza, PARIS va detrás. */
    private static ReglasTallerProperties reglas() {
        ReglaClienteTaller ami = new ReglaClienteTaller();
        ami.setDestinos(Map.of(
                "CHINA", destino(1),
                "JAPAN", destino(1),
                "PARIS", destino(2)));

        ReglasTallerProperties reglas = new ReglasTallerProperties();
        reglas.setClientes(Map.of("AMI", ami));
        return reglas;
    }

    private static ReglaDestinoTaller destino(int prioridad) {
        ReglaDestinoTaller regla = new ReglaDestinoTaller();
        regla.setPrioridad(prioridad);
        regla.setMezcla(TipoMezcla.LIBRE);
        return regla;
    }

    private static ObjetivoDestino objetivo(String destino, int cantidad) {
        return new ObjetivoDestino(destino, cantidad, "07001");
    }

    private static FilaAjustada fila(String referencia, String color, int recibido,
                                     ObjetivoDestino... objetivos) {
        return new FilaAjustada(referencia, color, "U", recibido, "60x40x40", 10,
                List.of(objetivos));
    }

    private static int cantidadDe(ResultadoReparto reparto, String destino, String referencia) {
        return reparto.getArticulos().stream()
                .filter(a -> a.destino().equals(destino) && a.referencia().equals(referencia))
                .mapToInt(ArticuloDestinado::cantidad)
                .sum();
    }

    @Test
    void laDestinacionPrioritariaSeSirveEnteraYLaOtraSeQuedaCorta() {
        // 31 recibidas, CHINA pide 20 y PARIS 20.
        FilaAjustada fila = fila("BAG-A", "NOIR", 31, objetivo("CHINA", 20), objetivo("PARIS", 20));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas()).repartir("AMI", List.of(fila));

        assertEquals(20, cantidadDe(reparto, "CHINA", "BAG-A"));
        assertEquals(11, cantidadDe(reparto, "PARIS", "BAG-A"));
        assertTrue(reparto.getAvisos().stream().anyMatch(a -> a.contains("PARIS")));
    }

    @Test
    void elOrdenDeLasDestinacionesNoCambiaElReparto() {
        FilaAjustada alReves = fila("BAG-A", "NOIR", 31,
                objetivo("PARIS", 20), objetivo("CHINA", 20));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas())
                .repartir("AMI", List.of(alReves));

        assertEquals(20, cantidadDe(reparto, "CHINA", "BAG-A"));
        assertEquals(11, cantidadDe(reparto, "PARIS", "BAG-A"));
    }

    @Test
    void siLlegaDeSobraSeSirveElObjetivoYElRestoSeQueda() {
        FilaAjustada fila = fila("BAG-A", "NOIR", 50, objetivo("CHINA", 20), objetivo("PARIS", 20));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas()).repartir("AMI", List.of(fila));

        assertEquals(20, cantidadDe(reparto, "CHINA", "BAG-A"));
        assertEquals(20, cantidadDe(reparto, "PARIS", "BAG-A"));
        assertTrue(reparto.getAvisos().stream().anyMatch(a -> a.contains("10")),
                "el sobrante se dice, para que nadie lo dé por enviado");
    }

    @Test
    void conPrioridadEmpatadaElRepartoEsProporcionalAlObjetivo() {
        // CHINA y JAPAN empatan; llegan 15 para 20 + 10.
        FilaAjustada fila = fila("BAG-A", "NOIR", 15, objetivo("CHINA", 20), objetivo("JAPAN", 10));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas()).repartir("AMI", List.of(fila));

        assertEquals(10, cantidadDe(reparto, "CHINA", "BAG-A"));
        assertEquals(5, cantidadDe(reparto, "JAPAN", "BAG-A"));
    }

    @Test
    void elRestoEnteroVaALaDestinacionDeMayorObjetivo() {
        // 5 unidades para 20 + 10: proporcional daría 3,33 y 1,66.
        FilaAjustada fila = fila("BAG-A", "NOIR", 5, objetivo("CHINA", 20), objetivo("JAPAN", 10));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas()).repartir("AMI", List.of(fila));

        assertEquals(5, cantidadDe(reparto, "CHINA", "BAG-A") + cantidadDe(reparto, "JAPAN", "BAG-A"));
        assertEquals(4, cantidadDe(reparto, "CHINA", "BAG-A"));
        assertEquals(1, cantidadDe(reparto, "JAPAN", "BAG-A"));
    }

    @Test
    void sinNadaQueRepartirNoSeGeneraNingunArticulo() {
        FilaAjustada fila = fila("BAG-A", "NOIR", 0, objetivo("CHINA", 20));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas()).repartir("AMI", List.of(fila));

        assertTrue(reparto.getArticulos().isEmpty());
        assertTrue(reparto.getAvisos().stream().anyMatch(a -> a.contains("BAG-A")));
    }

    @Test
    void unaDestinacionConObjetivoCeroNoGeneraArticulo() {
        FilaAjustada fila = fila("BAG-A", "NOIR", 10, objetivo("CHINA", 10), objetivo("PARIS", 0));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas()).repartir("AMI", List.of(fila));

        assertTrue(reparto.getArticulos().stream().noneMatch(a -> a.destino().equals("PARIS")));
        assertEquals(10, cantidadDe(reparto, "CHINA", "BAG-A"));
    }

    @Test
    void unaDestinacionSinPrioridadBloquea() {
        FilaAjustada fila = fila("BAG-A", "NOIR", 10, objetivo("MARTE", 10));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas()).repartir("AMI", List.of(fila));

        assertTrue(reparto.getBloqueos().stream().anyMatch(b -> b.contains("MARTE")));
        assertTrue(reparto.getArticulos().isEmpty());
    }

    @Test
    void unClienteSinReglasNoNecesitaPrioridades() {
        // Los clientes de destino único no tienen bloque en el yml y no hay
        // nada que arbitrar: exigirles prioridad los bloquearía sin motivo.
        FilaAjustada fila = fila("BAG-A", "NOIR", 10, objetivo("ALMACEN", 10));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas())
                .repartir("PALOMA WOOL", List.of(fila));

        assertEquals(10, cantidadDe(reparto, "ALMACEN", "BAG-A"));
        assertTrue(reparto.getBloqueos().isEmpty());
    }

    @Test
    void unaFilaSinUnidadesPorCajaBloquea() {
        FilaAjustada fila = new FilaAjustada("BAG-A", "NOIR", "U", 10, "60x40x40", null,
                List.of(objetivo("CHINA", 10)));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas()).repartir("AMI", List.of(fila));

        assertTrue(reparto.getBloqueos().stream().anyMatch(b -> b.contains("BAG-A")));
        assertTrue(reparto.getArticulos().isEmpty());
    }

    @Test
    void unaMedidaDeCajaIlegibleBloquea() {
        FilaAjustada fila = new FilaAjustada("BAG-A", "NOIR", "U", 10, "60x40", 10,
                List.of(objetivo("CHINA", 10)));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas()).repartir("AMI", List.of(fila));

        assertTrue(reparto.getBloqueos().stream().anyMatch(b -> b.contains("60x40")));
    }

    @Test
    void cadaArticuloSeRepartePorSuCuenta() {
        // Que a una referencia le falte género no le quita nada a la otra.
        FilaAjustada corta = fila("BAG-A", "NOIR", 5, objetivo("CHINA", 20), objetivo("PARIS", 20));
        FilaAjustada sobrada = fila("BAG-B", "NOIR", 40, objetivo("CHINA", 20), objetivo("PARIS", 20));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas())
                .repartir("AMI", List.of(corta, sobrada));

        assertEquals(5, cantidadDe(reparto, "CHINA", "BAG-A"));
        assertEquals(0, cantidadDe(reparto, "PARIS", "BAG-A"));
        assertEquals(20, cantidadDe(reparto, "CHINA", "BAG-B"));
        assertEquals(20, cantidadDe(reparto, "PARIS", "BAG-B"));
    }

    @Test
    void elArticuloDestinadoSeLlevaSuCartonYSusUnidadesPorCaja() {
        FilaAjustada fila = fila("BAG-A", "NOIR", 10, objetivo("CHINA", 10));

        ArticuloDestinado articulo = new RepartoDestinaciones(reglas())
                .repartir("AMI", List.of(fila)).getArticulos().get(0);

        assertEquals("60x40x40", articulo.medidaCaja());
        assertEquals(10, articulo.unidadesPorCaja());
        assertEquals("07001", articulo.pedido());
    }
}
