package com.puntotres.packinglist.modelos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.puntotres.packinglist.modelos.ComoUsuario;
import com.puntotres.packinglist.modelos.persistence.Adjunto;
import com.puntotres.packinglist.modelos.persistence.EstadoTranscripcion;
import com.puntotres.packinglist.modelos.persistence.Modificacion;
import com.puntotres.packinglist.modelos.persistence.ResumenAdjunto;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.TipoAdjunto;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloRepository;
import com.puntotres.packinglist.persistence.TipoArticulo;

/**
 * Lo que se admite como adjunto y cómo se guarda. Los mensajes a los que se
 * cuelgan salen de avisos de los modelos {@code T11-ADJ-…}.
 */
@SpringBootTest
@RecordApplicationEvents
class AdjuntosTest {

    @Autowired
    private Adjuntos adjuntos;

    @Autowired
    private Modificaciones modificaciones;

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private ModeloRepository repositorioModelos;

    @Autowired
    private ApplicationEvents eventos;

    @Test
    void unAudioDeMasDeTresMinutosSeRechaza() {
        Long mensajeId = mensaje("AUDIO");

        IllegalArgumentException largo = assertThrows(IllegalArgumentException.class,
                () -> adjuntos.guardar(mensajeId, ModificacionesTest.audio(Adjuntos.MAX_AUDIO_SEG + 1)));
        assertEquals("La nota de voz no puede pasar de 3 minutos", largo.getMessage());

        Adjunto justo = adjuntos.guardar(mensajeId, ModificacionesTest.audio(Adjuntos.MAX_AUDIO_SEG));
        assertEquals(EstadoTranscripcion.PENDIENTE, justo.getEstadoTranscripcion());
        assertEquals("audio/webm", justo.getMime());
        assertEquals(4, justo.getTamano());
        assertTrue(eventos.stream(AdjuntoAudioGuardado.class)
                .anyMatch(evento -> evento.adjuntoId().equals(justo.getId())),
                "una nota de voz guardada se anuncia para transcribirla");

        List<ResumenAdjunto> resumenes = adjuntos.deMensajes(List.of(mensajeId));
        assertTrue(resumenes.stream().anyMatch(r -> r.id().equals(justo.getId()) && r.duracionSeg() == 180
                && r.tipo() == TipoAdjunto.AUDIO));
        assertTrue(adjuntos.deMensajes(List.of()).isEmpty());
    }

    @Test
    void unaFotoSeReduceA1600() throws IOException {
        Long mensajeId = mensaje("FOTO");
        long anunciosAntes = eventos.stream(AdjuntoAudioGuardado.class).count();

        Adjunto foto = adjuntos.guardar(mensajeId, new AdjuntoNuevo(TipoAdjunto.FOTO, "grande.png", "image/png",
                ModificacionesTest.png(3200, 1000), null));

        assertEquals("grande.jpg", foto.getNombre());
        assertEquals("image/jpeg", foto.getMime());
        assertEquals(EstadoTranscripcion.NO_APLICA, foto.getEstadoTranscripcion());
        assertNull(foto.getDuracionSeg());
        byte[] guardados = adjuntos.conBytes(foto.getId()).orElseThrow().getBytes();
        assertEquals(guardados.length, foto.getTamano());
        BufferedImage leida = ImageIO.read(new ByteArrayInputStream(guardados));
        assertNotNull(leida);
        assertEquals(1600, leida.getWidth());
        assertEquals(500, leida.getHeight());
        assertEquals(anunciosAntes, eventos.stream(AdjuntoAudioGuardado.class).count(), "una foto no se transcribe");
    }

    @Test
    void unFicheroQueNoEsImagenSeRechazaNombrandolo() {
        Long mensajeId = mensaje("NOIMAGEN");

        IllegalArgumentException noImagen = assertThrows(IllegalArgumentException.class,
                () -> adjuntos.guardar(mensajeId, new AdjuntoNuevo(TipoAdjunto.FOTO, "notas.txt", "text/plain",
                        "no soy una foto".getBytes(StandardCharsets.UTF_8), null)));
        assertEquals("El fichero notas.txt no es una imagen", noImagen.getMessage());

        IllegalArgumentException vacio = assertThrows(IllegalArgumentException.class,
                () -> adjuntos.guardar(mensajeId, new AdjuntoNuevo(TipoAdjunto.DOCUMENTO, "vacio.pdf",
                        "application/pdf", new byte[0], null)));
        assertEquals("El fichero vacio.pdf está vacío", vacio.getMessage());

        IllegalArgumentException grande = assertThrows(IllegalArgumentException.class,
                () -> adjuntos.guardar(mensajeId, new AdjuntoNuevo(TipoAdjunto.DOCUMENTO, "enorme.pdf",
                        "application/pdf", new byte[(int) Adjuntos.MAX_BYTES + 1], null)));
        assertEquals("El fichero enorme.pdf pesa más de 15 MB", grande.getMessage());

        IllegalArgumentException sinNombre = assertThrows(IllegalArgumentException.class,
                () -> adjuntos.guardar(mensajeId, new AdjuntoNuevo(TipoAdjunto.DOCUMENTO, " ", "application/pdf",
                        new byte[] {1}, null)));
        assertEquals("El adjunto necesita un nombre", sinNombre.getMessage());

        IllegalArgumentException avisoConNoImagen = assertThrows(IllegalArgumentException.class,
                () -> modificaciones.crearAvisoDeTaller(new Modificaciones.AvisoTaller(modelo("NOIMAGEN-AVISO"),
                        "PIEL", List.of(new AdjuntoNuevo(TipoAdjunto.FOTO, "foto.heic", "image/heic",
                                new byte[] {0, 0, 0, 24}, null))), taller()));
        assertEquals("El fichero foto.heic no es una imagen", avisoConNoImagen.getMessage());
    }

    @Test
    void marcarTranscripcionYResumenLosDejaEnElAdjunto() {
        Long mensajeId = mensaje("TRANSCRIBE");
        Adjunto nota = adjuntos.guardar(mensajeId, ModificacionesTest.audio(10));

        adjuntos.marcarTranscripcion(nota.getId(), EstadoTranscripcion.FALLIDA, null, "Sin conexión");
        Adjunto fallida = adjuntos.conBytes(nota.getId()).orElseThrow();
        assertEquals(EstadoTranscripcion.FALLIDA, fallida.getEstadoTranscripcion());
        assertEquals("Sin conexión", fallida.getError());

        adjuntos.marcarTranscripcion(nota.getId(), EstadoTranscripcion.HECHA, "Cambiar el forro", null);
        adjuntos.marcarResumen(nota.getId(), "Forro nuevo");
        Adjunto hecha = adjuntos.conBytes(nota.getId()).orElseThrow();
        assertEquals(EstadoTranscripcion.HECHA, hecha.getEstadoTranscripcion());
        assertEquals("Cambiar el forro", hecha.getTranscripcion());
        assertEquals("Forro nuevo", hecha.getResumenIa());
        assertNull(hecha.getError());
    }

    /** El primer mensaje de un aviso nuevo de un modelo {@code T11-ADJ-…}. */
    private Long mensaje(String codigo) {
        Modificacion aviso = modificaciones.crearAvisoDeTaller(new Modificaciones.AvisoTaller(modelo(codigo), "PIEL",
                List.of(ModificacionesTest.foto())), taller());
        return modificaciones.detalle(aviso.getId()).mensajes().get(0).id();
    }

    private Long modelo(String codigo) {
        return repositorioModelos.save(new Modelo("AMI", "T11-ADJ-" + codigo, null, TipoArticulo.BOLSO)).getId();
    }

    private UsuarioActual taller() {
        Usuario usuario = ComoUsuario.usuarioDePrueba(usuarios, Rol.TALLER);
        return new UsuarioActual(usuario.getId(), usuario.getNombre(), usuario.getRol(), null);
    }
}
