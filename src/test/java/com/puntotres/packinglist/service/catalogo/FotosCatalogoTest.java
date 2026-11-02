package com.puntotres.packinglist.service.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.persistence.ResumenFoto;
import com.puntotres.packinglist.service.corte.FotoModelo;
import com.puntotres.packinglist.service.corte.FotosTemporada;
import com.puntotres.packinglist.testutil.FotosDePrueba;

/**
 * Las fotos de los modelos guardadas en el catálogo: cómo entran, cuál es la
 * principal y cómo vuelven a salir hacia el corte como si fueran la carpeta
 * de fotos de la temporada.
 */
@SpringBootTest
class FotosCatalogoTest {

    @Autowired
    private FotosCatalogo fotos;

    @Autowired
    private CatalogoModelos catalogo;

    @Test
    void laPrimeraFotoNaceComoPrincipalYNoSeDuplicaPorNombre() {
        catalogo.asegurarModelo("AMI", "ULL940", "");

        ResumenFoto primera = fotos.guardar("AMI", "ULL940", "a.jpg", FotosDePrueba.jpeg(20, 20, Color.RED));
        ResumenFoto repetida = fotos.guardar("AMI", "ULL940", "a.jpg", FotosDePrueba.jpeg(20, 20, Color.BLUE));

        assertEquals(primera.id(), repetida.id(), "misma foto: el mismo nombre en el mismo modelo");
        assertEquals(1, fotos.de("AMI", "ULL940").size());
        assertTrue(primera.principal());
    }

    @Test
    void laFotoDelModeloEsLaPrincipalYSinFotosNoHayNinguna() {
        catalogo.asegurarModelo("AMI", "ULL948", "");
        assertTrue(fotos.principalDe("AMI", "ULL948").isEmpty(), "un modelo sin fotos no tiene foto");

        fotos.guardar("AMI", "ULL948", "a.jpg", FotosDePrueba.jpeg(20, 20, Color.RED));
        ResumenFoto b = fotos.guardar("AMI", "ULL948", "b.jpg", FotosDePrueba.jpeg(20, 20, Color.BLUE));
        fotos.marcarPrincipal("AMI", "ULL948", b.id());

        assertEquals(b.id(), fotos.principalDe("AMI", "ULL948").orElseThrow(),
                "la principal, aunque no sea la primera");
        assertTrue(fotos.principalDe("AMI", "").isEmpty());
    }

    @Test
    void marcarPrincipalQuitaLaAnterior() {
        catalogo.asegurarModelo("AMI", "ULL941", "");
        ResumenFoto a = fotos.guardar("AMI", "ULL941", "a.jpg", FotosDePrueba.jpeg(20, 20, Color.RED));
        ResumenFoto b = fotos.guardar("AMI", "ULL941", "b.jpg", FotosDePrueba.jpeg(20, 20, Color.BLUE));

        fotos.marcarPrincipal("AMI", "ULL941", b.id());

        List<ResumenFoto> todas = fotos.de("AMI", "ULL941");
        assertEquals(1, todas.stream().filter(ResumenFoto::principal).count());
        assertTrue(todas.stream().filter(ResumenFoto::principal).findFirst().orElseThrow().id().equals(b.id()));
        assertFalse(fotos.conBytes(a.id()).orElseThrow().isPrincipal());
    }

    @Test
    void borrarLaPrincipalPasaElTestigo() {
        catalogo.asegurarModelo("AMI", "ULL942", "");
        ResumenFoto a = fotos.guardar("AMI", "ULL942", "a.jpg", FotosDePrueba.jpeg(20, 20, Color.RED));
        fotos.guardar("AMI", "ULL942", "b.jpg", FotosDePrueba.jpeg(20, 20, Color.BLUE));

        fotos.borrar(a.id());

        List<ResumenFoto> quedan = fotos.de("AMI", "ULL942");
        assertEquals(1, quedan.size());
        assertTrue(quedan.get(0).principal());
    }

    @Test
    void comoFotosTemporadaEscribeLosFicherosYLaPrincipalSeLlamaPrincipal() throws IOException {
        catalogo.asegurarModelo("AMI", "ULL943", "");
        fotos.guardar("AMI", "ULL943", "z-detalle.jpg", FotosDePrueba.jpeg(20, 20, Color.RED));
        ResumenFoto frente = fotos.guardar("AMI", "ULL943", "frente.jpg", FotosDePrueba.jpeg(20, 20, Color.BLUE));
        fotos.marcarPrincipal("AMI", "ULL943", frente.id());
        Path directorio = Files.createTempDirectory("fotos-catalogo-test-");

        FotosTemporada temporada = fotos.comoFotosTemporada("AMI", List.of("ULL943", "ULL999"), directorio);

        List<FotoModelo> delModelo = temporada.de("ULL943");
        assertEquals(2, delModelo.size());
        assertTrue(delModelo.stream().allMatch(foto -> Files.exists(foto.original())));
        FotoModelo porDefecto = delModelo.get(temporada.indicePorDefecto("ULL943"));
        assertEquals("principal.jpg", porDefecto.nombreOriginal(),
                "la marcada en el catálogo es la que el corte elige por defecto");
        assertTrue(temporada.de("ULL999").isEmpty());
        assertEquals(2, temporada.total());
    }
}
