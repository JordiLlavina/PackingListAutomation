package com.puntotres.packinglist.service.escandallos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Los escandallos son por modelo <em>y</em> color, así que dos ficheros
 * distintos traen el mismo MODEL con mucha frecuencia (los dos ejemplos
 * reales, sin ir más lejos). Excel no admite dos hojas con el mismo nombre.
 */
class NombresHojaTest {

    private final NombresHoja nombres = new NombresHoja();

    private static Escandallo escandallo(String modelo, String color, String origen) {
        return new Escandallo(modelo, "SAC CANDY RABAT LARGE UNISEX", color, List.of(), origen);
    }

    @Test
    void elNombreEsElModeloMasElColorSinSusCodigosNumericos() {
        assertEquals("ULL770.AL245 NOIR",
                nombres.para(escandallo("ULL770.AL245", "000    0014 NOIR", "ULL770 NOIR.xlsx")));
    }

    @Test
    void elColorDeVariasPalabrasSeConservaEntero() {
        assertEquals("ULL770.AL245 SABLE SAND",
                nombres.para(escandallo("ULL770.AL245", "001    255 SABLE SAND",
                        "ULL770 SAND.xlsx")));
    }

    @Test
    void dosEscandallosDelMismoModeloYColorNoChocan() {
        Escandallo uno = escandallo("ULL770.AL245", "000 0014 NOIR", "ULL770 NOIR.xlsx");
        Escandallo otro = escandallo("ULL770.AL245", "000 0014 NOIR", "ULL770 NOIR (copia).xlsx");

        assertEquals("ULL770.AL245 NOIR", nombres.para(uno));
        assertEquals("ULL770.AL245 NOIR (2)", nombres.para(otro));
    }

    @Test
    void elChoqueDeNombreSeAvisa() {
        Escandallo repetido = escandallo("ULL770.AL245", "000 0014 NOIR", "dos.xlsx");
        nombres.para(escandallo("ULL770.AL245", "000 0014 NOIR", "uno.xlsx"));

        nombres.para(repetido);

        assertTrue(nombres.avisos().stream().anyMatch(aviso -> aviso.contains("dos.xlsx")
                && aviso.contains("ULL770.AL245 NOIR (2)")), nombres.avisos().toString());
    }

    @Test
    void unNombreLargoSeRecortaALos31CaracteresDeExcel() {
        String nombre = nombres.para(escandallo("ULL770.AL245678",
                "000 0014 NEGRO CON REFLEJOS DORADOS", "largo.xlsx"));

        assertEquals(31, nombre.length());
        assertEquals("ULL770.AL245678 NEGRO CON REFLE", nombre);
    }

    @Test
    void elSufijoDeChoqueCabeAunqueElNombreYaLlegaseAlLimite() {
        Escandallo largo = escandallo("ULL770.AL245678",
                "000 0014 NEGRO CON REFLEJOS DORADOS", "largo.xlsx");

        nombres.para(largo);
        String segundo = nombres.para(largo);

        assertTrue(segundo.length() <= 31, segundo);
        assertTrue(segundo.endsWith("(2)"), segundo);
    }

    @Test
    void losCaracteresQueExcelProhibeEnUnaHojaDesaparecen() {
        String nombre = nombres.para(escandallo("ULL/770:AL245", "000 0014 NOIR*", "raro.xlsx"));

        assertEquals("ULL 770 AL245 NOIR", nombre);
    }

    @Test
    void sinModeloSeUsaElNombreDelFicheroDeOrigen() {
        assertEquals("ULL770 NOIR", nombres.para(escandallo(null, "000 0014 NOIR",
                "ULL770 NOIR.xlsx")));
    }

    @Test
    void sinColorSeUsaSoloElModelo() {
        assertEquals("ULL770.AL245", nombres.para(escandallo("ULL770.AL245", null, "sin.xlsx")));
    }

    @Test
    void unColorSoloNumericoSeConservaTalCual() {
        assertEquals("ULL770.AL245 000 0014",
                nombres.para(escandallo("ULL770.AL245", "000    0014", "num.xlsx")));
    }
}
