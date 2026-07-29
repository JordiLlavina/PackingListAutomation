package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Nombre de fichero de los packing lists AMI. El formato lo fija el cliente
 * (ver la lista de ficheros reales de docs/): fecha de envío, marca PUN,
 * product order, referencia.color, temporada y destinación abreviada.
 */
class AmiNombreFicheroTest {

    @Test
    void componeElFormatoAcordadoConElCliente() {
        assertEquals("2026.05.21_PUN_07705_ULL027.AL0103.001_H26_CHINA.xlsx",
                AmiNombreFichero.componer("21/05/2026", "07705", "ULL027.AL0103", "001", "H26", "CHINA"));
    }

    @Test
    void abreviaLasDestinacionesConocidas() {
        assertEquals("2026.04.14_PUN_07668_ULL737.AL0137.A236_H26_FR.xlsx",
                AmiNombreFichero.componer("14/04/2026", "07668", "ULL737.AL0137", "A236", "H26", "FRANCE"));
        // PARIS es la misma destinación escrita de otra forma en las imágenes.
        assertEquals("2026.04.14_PUN_07668_ULL737.AL0137.A236_H26_FR.xlsx",
                AmiNombreFichero.componer("14/04/2026", "07668", "ULL737.AL0137", "A236", "H26", "paris"));
        assertEquals("2026.04.14_PUN_07701_USL737.AL0137.A236_H26_JAPAN.xlsx",
                AmiNombreFichero.componer("14/04/2026", "07701", "USL737.AL0137", "A236", "H26", "Japan"));
    }

    @Test
    void unaDestinacionDesconocidaNoBloquea() {
        assertEquals("2026.04.14_PUN_07668_ULL737.AL0137.A236_H26_HONGKONG.xlsx",
                AmiNombreFichero.componer("14/04/2026", "07668", "ULL737.AL0137", "A236", "H26", "Hong Kong"));
    }

    @Test
    void saneaLosCaracteresQueWindowsNoAdmiteEnUnNombreDeFichero() {
        assertEquals("2026.07.24_PUN_07685_USL737.ACO137.ROJO_PASION_69_H26_FR.xlsx",
                AmiNombreFichero.componer("24/07/2026", "07685", "USL737.ACO137",
                        "ROJO PASION 69", "H26", "PARIS"));
        assertEquals("2026.07.24_PUN_OF-1_BOLSO_TOTE.NAT_03_H26_NEWYORK_BOSTON.xlsx",
                AmiNombreFichero.componer("24/07/2026", "OF-1", "BOLSO: TOTE",
                        "NAT 03", "H26", "New York/Boston"));
    }

    @Test
    void losCamposQueFaltanSeOmitenEnVezDeDejarSeparadoresSueltos() {
        assertEquals("2026.05.21_PUN_ULL027.AL0103.001_CHINA.xlsx",
                AmiNombreFichero.componer("21/05/2026", null, "ULL027.AL0103", "001", "  ", "CHINA"));
        assertEquals("PUN_ULL027.AL0103_H26_CHINA.xlsx",
                AmiNombreFichero.componer(null, "", "ULL027.AL0103", null, "H26", "CHINA"));
    }

    @Test
    void unaFechaQueNoEsDdMmYyyySeEscribeTalCualSinBloquear() {
        assertEquals("24-07-2026_PUN_07705_ULL027.AL0103.001_H26_CHINA.xlsx",
                AmiNombreFichero.componer("24-07-2026", "07705", "ULL027.AL0103", "001", "H26", "CHINA"));
    }
}
