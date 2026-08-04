package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * La imagen compuesta que va en el hueco del EAN13 de la etiqueta de caja de
 * AMI: los cuatro textos de la etiqueta de artículo con el código de barras
 * debajo, todo en un solo PNG.
 */
class ImagenEtiquetaArticuloTest {

    /** El EAN13 y los cuatro textos del mock de la plantilla del cliente. */
    private static final EtiquetaArticulo ETIQUETA = new EtiquetaArticulo(
            "ULL163.AL0052", "Size: U", "221 DARK COFFEE", "Cde: 07703", "3666598354771");

    /** La del hueco de la plantilla: 1674091 / 762000. */
    private static final double PROPORCION = 2.1969;

    private static BufferedImage leer(byte[] png) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    @Test
    void laImagenSaleConLaProporcionDelHueco() throws Exception {
        BufferedImage imagen = leer(ImagenEtiquetaArticulo.png(ETIQUETA, PROPORCION));
        assertEquals(PROPORCION, (double) imagen.getWidth() / imagen.getHeight(), 0.02);
    }

    @Test
    void elLienzoSeDimensionaAPartirDelCodigo() throws Exception {
        // Es lo que garantiza que el código quepa a resolución nativa: si el
        // lienzo se fijara aparte, habría que escalar el código para meterlo.
        BufferedImage codigo = leer(CodigoBarrasEan13.png(
                ETIQUETA.ean13(), PROPORCION * 0.96 / 0.64).orElseThrow());
        BufferedImage imagen = leer(ImagenEtiquetaArticulo.png(ETIQUETA, PROPORCION));
        assertEquals(Math.round(codigo.getWidth() / 0.96), imagen.getWidth());
    }

    @Test
    void elCodigoDeBarrasNoSeInterpola() throws Exception {
        // Un reescalado con interpolación mete grises entre barra y hueco. La
        // banda central del código, pegada 1:1, es blanco y negro puros.
        BufferedImage imagen = leer(ImagenEtiquetaArticulo.png(ETIQUETA, PROPORCION));
        int y = imagen.getHeight() * 3 / 5;   // dentro de las barras
        int grises = 0;
        for (int x = 0; x < imagen.getWidth(); x++) {
            int rgb = imagen.getRGB(x, y) & 0xFFFFFF;
            if (rgb != 0x000000 && rgb != 0xFFFFFF) {
                grises++;
            }
        }
        assertEquals(0, grises, "hay píxeles grises en la banda del código de barras");
    }

    @Test
    void unaFilaDeBarrasTieneBarrasDeVerdad() throws Exception {
        // Que la banda sea blanco y negro puros no basta: podría ser toda
        // blanca. Tiene que haber negro.
        BufferedImage imagen = leer(ImagenEtiquetaArticulo.png(ETIQUETA, PROPORCION));
        int y = imagen.getHeight() * 3 / 5;
        int negros = 0;
        for (int x = 0; x < imagen.getWidth(); x++) {
            if ((imagen.getRGB(x, y) & 0xFFFFFF) == 0x000000) {
                negros++;
            }
        }
        assertTrue(negros > 20, "la banda del código no tiene barras: " + negros);
    }

    @Test
    void sinEan13SaleLaImagenIgualConSusTextos() throws Exception {
        EtiquetaArticulo sinCodigo = new EtiquetaArticulo(
                "ULL163.AL0052", "Size: U", "221 DARK COFFEE", "Cde: 07703", null);
        byte[] png = ImagenEtiquetaArticulo.png(sinCodigo, PROPORCION);
        assertNotNull(png);
        BufferedImage imagen = leer(png);
        assertEquals(PROPORCION, (double) imagen.getWidth() / imagen.getHeight(), 0.02);
    }

    @Test
    void losTextosNullNoRompenLaImagen() throws Exception {
        EtiquetaArticulo vacia = new EtiquetaArticulo(null, null, null, null, null);
        assertNotNull(leer(ImagenEtiquetaArticulo.png(vacia, PROPORCION)));
    }

    @Test
    void elLienzoNoCambiaDeTamanoConTextosLargos() throws Exception {
        // No se puede afirmar el cuerpo elegido desde fuera (eso lo cubre el
        // bucle de encogido en otro sitio); lo único comprobable aquí es que
        // el lienzo siempre sale con el tamaño pedido, aunque el texto no quepa.
        EtiquetaArticulo larga = new EtiquetaArticulo(
                "ULL163.AL0052 / ULL745.AL0103 / UBL029.AL0216", "Size: U",
                "221 DARK COFFEE / 001 BLACK", "Cde: 07703", "3666598354771");
        BufferedImage normal = leer(ImagenEtiquetaArticulo.png(ETIQUETA, PROPORCION));
        BufferedImage grande = leer(ImagenEtiquetaArticulo.png(larga, PROPORCION));
        assertEquals(normal.getWidth(), grande.getWidth());
        assertEquals(normal.getHeight(), grande.getHeight());
    }

    @Test
    void dejaUnaMuestraEnTargetParaMirarla() throws Exception {
        // Existe para abrirla y compararla con el mock del cliente: lo que
        // afirma es lo único afirmable de un artefacto cuyo juez es el ojo.
        Path muestra = Path.of("target/imagen-etiqueta-articulo.png");
        Files.write(muestra, ImagenEtiquetaArticulo.png(ETIQUETA, PROPORCION));
        assertNotNull(ImageIO.read(muestra.toFile()), "la muestra no es un PNG legible");
    }
}
