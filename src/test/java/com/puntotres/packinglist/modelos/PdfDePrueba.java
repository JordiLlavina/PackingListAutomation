package com.puntotres.packinglist.modelos;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Un PDF como una ficha técnica de cliente, hecho en el test con PDFBox (en el
 * repo no entra ningún PDF real): en la primera página dos fotos de 400 × 400
 * (la primera pegada dos veces, que tiene que contar una) y un logo de
 * 100 × 100; en la segunda, solo líneas, como un dibujo técnico vectorial sin
 * ninguna imagen que extraer.
 */
public final class PdfDePrueba {

    /** Las candidatas que salen de {@link #fichaTecnica()}: las dos fotos y las dos páginas. */
    public static final int CANDIDATAS = 4;

    private PdfDePrueba() {
    }

    public static byte[] fichaTecnica() throws IOException {
        try (PDDocument documento = new PDDocument()) {
            PDPage conFotos = new PDPage(PDRectangle.A4);
            PDPage vectorial = new PDPage(PDRectangle.A4);
            documento.addPage(conFotos);
            documento.addPage(vectorial);

            PDImageXObject roja = LosslessFactory.createFromImage(documento, imagen(400, 400, Color.RED));
            PDImageXObject azul = LosslessFactory.createFromImage(documento, imagen(400, 400, Color.BLUE));
            PDImageXObject logo = LosslessFactory.createFromImage(documento, imagen(100, 100, Color.GREEN));
            try (PDPageContentStream pagina = new PDPageContentStream(documento, conFotos)) {
                pagina.drawImage(roja, 50, 450, 200, 200);
                pagina.drawImage(roja, 300, 450, 100, 100);
                pagina.drawImage(azul, 50, 150, 200, 200);
                pagina.drawImage(logo, 450, 750, 50, 50);
            }
            try (PDPageContentStream pagina = new PDPageContentStream(documento, vectorial)) {
                pagina.setLineWidth(2);
                pagina.moveTo(100, 100);
                pagina.lineTo(400, 700);
                pagina.lineTo(500, 100);
                pagina.closeAndStroke();
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    private static BufferedImage imagen(int ancho, int alto, Color color) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, ancho, alto);
        g.setColor(Color.WHITE);
        g.fillOval(ancho / 4, alto / 4, ancho / 2, alto / 2);
        g.dispose();
        return imagen;
    }
}
