package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * La clave con la que el catálogo de modelos guarda y vuelve a leer los
 * colores de piel de un color del bolso. Tiene que ser la MISMA al guardar y
 * al leer: con una distinta, lo tecleado se guarda y no vuelve nunca.
 */
class ColorCorteTest {

    @Test
    void laClaveDelCatalogoEsElCodigoOSinCodigoLaDescripcionEntera() {
        assertEquals("LZZ", new ColorCorte("LZZ", "NOIR", 3).claveCatalogo());
        assertEquals("AMARILLO CLARO/ GRIS GUIJARRO",
                new ColorCorte("", "AMARILLO CLARO/ GRIS GUIJARRO", 3).claveCatalogo(),
                "el bicolor de AMI no tiene código: se identifica por su descripción");
        assertEquals("", new ColorCorte("", "", 0).claveCatalogo());
    }
}
