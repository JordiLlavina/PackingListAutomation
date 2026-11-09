package com.puntotres.packinglist.modelos.service.transcripcion;

import static com.puntotres.packinglist.modelos.ComoUsuario.comoUsuario;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.modelos.ComoUsuario;
import com.puntotres.packinglist.modelos.persistence.Adjunto;
import com.puntotres.packinglist.modelos.persistence.EstadoTranscripcion;
import com.puntotres.packinglist.modelos.persistence.Modificacion;
import com.puntotres.packinglist.modelos.persistence.ModificacionRepository;
import com.puntotres.packinglist.modelos.persistence.Prioridad;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.TipoAdjunto;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.modelos.service.AdjuntoNuevo;
import com.puntotres.packinglist.modelos.service.Adjuntos;
import com.puntotres.packinglist.modelos.service.Modificaciones;
import com.puntotres.packinglist.modelos.service.Usuarios;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloRepository;
import com.puntotres.packinglist.persistence.TipoArticulo;

/**
 * La transcripción y el resumen de las notas de voz, de punta a punta: se crea
 * un aviso con una nota de voz como lo haría el taller y se espera a que el
 * hilo de la transcripción acabe. Azure y Claude son falsos (los de abajo),
 * así que no sale nada a la red.
 *
 * El test no es {@code @Transactional} a propósito: la transcripción arranca
 * cuando se confirma la transacción que guarda la nota de voz, y dentro de una
 * transacción de test no se confirmaría nunca. Los modelos se llaman
 * {@code T16-…}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TranscripcionServiceTest {

    /** Lo más que se espera al hilo de la transcripción. */
    private static final long ESPERA_MAXIMA_MS = 10_000;

    @Autowired
    private TranscripcionService transcripcion;

    @Autowired
    private Modificaciones modificaciones;

    @Autowired
    private Adjuntos adjuntos;

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private ModeloRepository repositorioModelos;

    @Autowired
    private ModificacionRepository repositorio;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TranscriptorFalso transcriptor;

    @Autowired
    private ResumidorFalso resumidor;

    @TestConfiguration
    static class Falsos {

        @Bean
        @Primary
        TranscriptorFalso transcriptorFalso() {
            return new TranscriptorFalso();
        }

        @Bean
        @Primary
        ResumidorFalso resumidorFalso() {
            return new ResumidorFalso();
        }
    }

    /** Un Azure de mentira: disponible o no, y lo que conteste (o lance). */
    static class TranscriptorFalso implements Transcriptor {

        volatile boolean disponible = true;
        volatile String texto = "";
        volatile String fallo;
        volatile CountDownLatch espera;

        @Override
        public boolean disponible() {
            return disponible;
        }

        @Override
        public String transcribir(byte[] audio, String mime) throws IOException {
            CountDownLatch latch = espera;
            if (latch != null) {
                try {
                    latch.await(ESPERA_MAXIMA_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (fallo != null) {
                throw new IOException(fallo);
            }
            return texto;
        }
    }

    /** Un Claude de mentira que apunta con qué se le ha llamado. */
    static class ResumidorFalso implements ResumidorModificacion {

        volatile boolean disponible = true;
        volatile ResumenIa resumen;
        volatile String fallo;
        private final List<List<String>> llamadas = new ArrayList<>();

        @Override
        public boolean disponible() {
            return disponible;
        }

        @Override
        public synchronized ResumenIa resumir(String transcripcion, String tipoCambio, String modelo)
                throws IOException {
            llamadas.add(List.of(transcripcion, tipoCambio, modelo));
            if (fallo != null) {
                throw new IOException(fallo);
            }
            return resumen;
        }

        synchronized List<List<String>> llamadas() {
            return List.copyOf(llamadas);
        }

        synchronized void olvidarLlamadas() {
            llamadas.clear();
        }
    }

    @BeforeEach
    void limpiarLosFalsos() {
        esperarALaCola();
        transcriptor.disponible = true;
        transcriptor.texto = "";
        transcriptor.fallo = null;
        transcriptor.espera = null;
        resumidor.disponible = true;
        resumidor.resumen = null;
        resumidor.fallo = null;
        resumidor.olvidarLlamadas();
    }

    @Test
    void conElTranscriptorDesactivadoElAdjuntoQuedaNoAplicaYLaModificacionSeCreaIgual() {
        transcriptor.disponible = false;

        Modificacion aviso = avisoConVoz("DESACTIVADO");
        esperarALaCola();

        Adjunto nota = nota(aviso);
        assertEquals(EstadoTranscripcion.NO_APLICA, nota.getEstadoTranscripcion());
        assertNull(nota.getTranscripcion());
        assertNull(nota.getError(), "no transcribir porque no hay servicio no es un fallo");
        assertTrue(repositorio.findById(aviso.getId()).isPresent());
        assertTrue(resumidor.llamadas().isEmpty());
    }

    @Test
    void unTranscriptorQueLanzaDejaFallidaConSuErrorYElResumenNoCorre() {
        transcriptor.fallo = "Azure ha contestado 401: clave no válida";

        Modificacion aviso = avisoConVoz("FALLA");
        esperarALaCola();

        Adjunto nota = nota(aviso);
        assertEquals(EstadoTranscripcion.FALLIDA, nota.getEstadoTranscripcion());
        assertEquals("Azure ha contestado 401: clave no válida", nota.getError());
        assertNull(nota.getTranscripcion());
        assertTrue(resumidor.llamadas().isEmpty());
        assertNull(repositorio.findById(aviso.getId()).orElseThrow().getTitulo());
    }

    @Test
    void unaTranscripcionBuenaPoneElTituloMarcadoComoDeLaIa() {
        transcriptor.texto = "  He reforzado la costura del asa porque se abría  ";
        resumidor.resumen = new ResumenIa("Costura del asa reforzada", "Se dobla la costura del asa.", "COSTURA",
                List.of("hilo encerado"), List.of("No dice si es el asa larga"));

        Modificacion aviso = avisoConVoz("BUENA");
        esperarALaCola();

        Adjunto nota = nota(aviso);
        assertEquals(EstadoTranscripcion.HECHA, nota.getEstadoTranscripcion());
        assertEquals("He reforzado la costura del asa porque se abría", nota.getTranscripcion());
        assertNull(nota.getError());
        assertNotNull(nota.getResumenIa());
        assertTrue(nota.getResumenIa().startsWith("Se dobla la costura del asa."), nota.getResumenIa());
        assertTrue(nota.getResumenIa().contains("Tipo de cambio sugerido: Costura / construcción"),
                nota.getResumenIa());
        assertTrue(nota.getResumenIa().contains("Materiales: hilo encerado"), nota.getResumenIa());
        assertTrue(nota.getResumenIa().contains("Dudas: No dice si es el asa larga"), nota.getResumenIa());

        Modificacion recargada = repositorio.findById(aviso.getId()).orElseThrow();
        assertEquals("Costura del asa reforzada", recargada.getTitulo());
        assertTrue(recargada.isTituloDeIa());

        assertEquals(List.of(List.of("He reforzado la costura del asa porque se abría", "Piel / material",
                "T16-BUENA · Bolso T16")), resumidor.llamadas());
    }

    @Test
    void elTituloDeLaIaNoPisaUnTituloQueYaHay() {
        transcriptor.texto = "Cambia el forro";
        resumidor.resumen = new ResumenIa("Forro nuevo", "Cambiar el forro.", "FORRO", List.of(), List.of());

        Modificacion orden = modificaciones.crearOrdenParaTaller(new Modificaciones.OrdenDespacho(
                modelo("ORDEN"), "FORRO", "Forro de ante", null, Prioridad.NORMAL, List.of(audio())), despacho());
        esperarALaCola();

        Modificacion recargada = repositorio.findById(orden.getId()).orElseThrow();
        assertEquals("Forro de ante", recargada.getTitulo());
        assertFalse(recargada.isTituloDeIa());
        Adjunto nota = nota(orden);
        assertEquals(EstadoTranscripcion.HECHA, nota.getEstadoTranscripcion());
        assertTrue(nota.getResumenIa().startsWith("Cambiar el forro."), nota.getResumenIa());
    }

    @Test
    void unResumenQueFallaDejaLaTranscripcionHechaConSuError() {
        transcriptor.texto = "Herrajes dorados";
        resumidor.fallo = "Se ha agotado el tiempo";

        Modificacion aviso = avisoConVoz("RESUMENFALLA");
        esperarALaCola();

        Adjunto nota = nota(aviso);
        assertEquals(EstadoTranscripcion.HECHA, nota.getEstadoTranscripcion(), "la transcripción sigue valiendo");
        assertEquals("Herrajes dorados", nota.getTranscripcion());
        assertEquals("Sin resumen de la IA: Se ha agotado el tiempo", nota.getError());
        assertNull(nota.getResumenIa());
        assertNull(repositorio.findById(aviso.getId()).orElseThrow().getTitulo());
    }

    @Test
    void unSilencioQuedaHechoSinTextoYNoSeResume() {
        transcriptor.texto = "   ";

        Modificacion aviso = avisoConVoz("SILENCIO");
        esperarALaCola();

        Adjunto nota = nota(aviso);
        assertEquals(EstadoTranscripcion.HECHA, nota.getEstadoTranscripcion());
        assertEquals("", nota.getTranscripcion());
        assertTrue(resumidor.llamadas().isEmpty());
    }

    @Test
    void retranscribirVuelveAEncolar() throws Exception {
        transcriptor.fallo = "Sin conexión";
        Modificacion aviso = avisoConVoz("REINTENTO");
        esperarALaCola();
        Adjunto nota = nota(aviso);
        assertEquals(EstadoTranscripcion.FALLIDA, nota.getEstadoTranscripcion());

        transcriptor.fallo = null;
        transcriptor.texto = "Ahora sí";
        resumidor.disponible = false;
        CountDownLatch soltar = new CountDownLatch(1);
        transcriptor.espera = soltar;
        mvc.perform(post("/api/adjuntos/" + nota.getId() + "/retranscribir")
                        .with(comoUsuario(usuario(Rol.TALLER))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(nota.getId()))
                .andExpect(jsonPath("$.estadoTranscripcion").value("PENDIENTE"));

        Adjunto pendiente = adjuntos.conBytes(nota.getId()).orElseThrow();
        assertEquals(EstadoTranscripcion.PENDIENTE, pendiente.getEstadoTranscripcion());
        assertNull(pendiente.getError(), "el error viejo se borra al reintentar");

        soltar.countDown();
        esperarALaCola();
        Adjunto hecha = adjuntos.conBytes(nota.getId()).orElseThrow();
        assertEquals(EstadoTranscripcion.HECHA, hecha.getEstadoTranscripcion());
        assertEquals("Ahora sí", hecha.getTranscripcion());
        assertNull(hecha.getError());
    }

    @Test
    void soloSeRetranscribeUnaNotaDeVoz() throws Exception {
        Modificacion aviso = modificaciones.crearAvisoDeTaller(new Modificaciones.AvisoTaller(modelo("FOTO"), "PIEL",
                List.of(new AdjuntoNuevo(TipoAdjunto.FOTO, "foto.png", "image/png", png(), null))), taller());
        Long fotoId = modificaciones.detalle(aviso.getId()).mensajes().get(0).adjuntos().get(0).id();

        mvc.perform(post("/api/adjuntos/" + fotoId + "/retranscribir")
                        .with(comoUsuario(usuario(Rol.DESPACHO))).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Solo se transcribe una nota de voz"));
        mvc.perform(post("/api/adjuntos/999999999/retranscribir")
                        .with(comoUsuario(usuario(Rol.DESPACHO))).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void despachoCorrigeElTituloYDejaDeSerDeLaIa() throws Exception {
        transcriptor.texto = "Asa más larga";
        resumidor.resumen = new ResumenIa("Asa alargada", "El asa pasa a 60 cm.", "MEDIDAS", List.of(), List.of());
        Modificacion aviso = avisoConVoz("TITULO");
        esperarALaCola();
        assertTrue(repositorio.findById(aviso.getId()).orElseThrow().isTituloDeIa());

        mvc.perform(patch("/api/modificaciones/" + aviso.getId() + "/titulo")
                        .with(comoUsuario(usuario(Rol.TALLER))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"Otra cosa\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/modificaciones/" + aviso.getId() + "/titulo")
                        .with(comoUsuario(usuario(Rol.DESPACHO))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Falta el título"));
        mvc.perform(patch("/api/modificaciones/" + aviso.getId() + "/titulo")
                        .with(comoUsuario(usuario(Rol.DESPACHO))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\" Asa de 60 cm \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Asa de 60 cm"))
                .andExpect(jsonPath("$.tituloDeIa").value(false));

        Modificacion recargada = repositorio.findById(aviso.getId()).orElseThrow();
        assertEquals("Asa de 60 cm", recargada.getTitulo());
        assertFalse(recargada.isTituloDeIa());
    }

    private void esperarALaCola() {
        long limite = System.currentTimeMillis() + ESPERA_MAXIMA_MS;
        while (transcripcion.pendientes() > 0) {
            if (System.currentTimeMillis() > limite) {
                fail("La transcripción no ha acabado en " + ESPERA_MAXIMA_MS / 1000 + " s");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrumpido esperando a la transcripción");
            }
        }
    }

    private Modificacion avisoConVoz(String codigo) {
        return modificaciones.crearAvisoDeTaller(new Modificaciones.AvisoTaller(modelo(codigo), "PIEL",
                List.of(audio())), taller());
    }

    private Adjunto nota(Modificacion modificacion) {
        Long id = modificaciones.detalle(modificacion.getId()).mensajes().get(0).adjuntos().stream()
                .filter(a -> a.tipo() == TipoAdjunto.AUDIO).findFirst().orElseThrow().id();
        return adjuntos.conBytes(id).orElseThrow();
    }

    private Long modelo(String codigo) {
        return repositorioModelos.save(new Modelo("AMI", "T16-" + codigo, "Bolso T16", TipoArticulo.BOLSO)).getId();
    }

    private Usuario usuario(Rol rol) {
        return ComoUsuario.usuarioDePrueba(usuarios, rol);
    }

    private UsuarioActual taller() {
        return actual(usuario(Rol.TALLER));
    }

    private UsuarioActual despacho() {
        return actual(usuario(Rol.DESPACHO));
    }

    private static UsuarioActual actual(Usuario usuario) {
        return new UsuarioActual(usuario.getId(), usuario.getNombre(), usuario.getRol(), null);
    }

    private static AdjuntoNuevo audio() {
        return new AdjuntoNuevo(TipoAdjunto.AUDIO, "nota.webm", "audio/webm", new byte[] {26, 69, -33, -93}, 12);
    }

    private static byte[] png() {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "png", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
