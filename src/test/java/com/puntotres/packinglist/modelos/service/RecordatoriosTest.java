package com.puntotres.packinglist.modelos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.modelos.ComoUsuario;
import com.puntotres.packinglist.modelos.api.dto.ModificacionResumen;
import com.puntotres.packinglist.modelos.persistence.Mensaje;
import com.puntotres.packinglist.modelos.persistence.Modificacion;
import com.puntotres.packinglist.modelos.persistence.ModificacionRepository;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.modelos.service.PushDePrueba.EnviadorFalso;
import com.puntotres.packinglist.modelos.service.PushDePrueba.RelojDePrueba;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloRepository;
import com.puntotres.packinglist.persistence.TipoArticulo;

/**
 * Los recordatorios anti-olvido: qué se recuerda, a quién, cada cuánto, que
 * no se repite dentro del plazo y que lo cerrado no insiste.
 *
 * Las horas se dan a mano ({@code revisar(ahora)}) y las modificaciones se
 * mueven al pasado con SQL: cada test vive en su propio año (1990, 2001,
 * 2002…), muy lejos de lo que crean los demás tests de la suite, que es de
 * hoy, así que lo de los demás nunca está vencido a esas horas. Los envíos se
 * miran solo los de la modificación de cada test. Los modelos se llaman
 * {@code T18-…}. No es {@code @Transactional} por lo mismo que {@link PushTest}.
 */
@SpringBootTest
@Import(PushDePrueba.class)
class RecordatoriosTest {

    private static final long ESPERA_MAXIMA_MS = 5_000;

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    @Autowired
    private Recordatorios recordatorios;

    @Autowired
    private Modificaciones modificaciones;

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private ModeloRepository repositorioModelos;

    @Autowired
    private ModificacionRepository repositorioModificaciones;

    @Autowired
    private Push push;

    @Autowired
    private EnviadorFalso enviador;

    @Autowired
    private RelojDePrueba reloj;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();

    private Usuario taller;
    private Usuario despacho;

    @BeforeEach
    void preparar() {
        taller = ComoUsuario.usuarioDePrueba(usuarios, Rol.TALLER);
        despacho = ComoUsuario.usuarioDePrueba(usuarios, Rol.DESPACHO);
        push.suscribir(actual(taller), "https://push.example/T18-taller", "p256dh-taller", "auth-taller");
        push.suscribir(actual(despacho), "https://push.example/T18-despacho", "p256dh-despacho", "auth-despacho");
        esperarALaCola();
        enviador.envios.clear();
    }

    @AfterEach
    void soltarElReloj() {
        reloj.soltar();
    }

    @Test
    void unaOrdenUrgenteRecibeRecordatorioCadaDiaYUnaNormalNinguno() throws IOException {
        Modificacion orden = orden("URGENTE", true);
        Modificacion normal = orden("NORMAL", false);
        LocalDateTime creada = LocalDateTime.of(2001, 3, 1, 8, 0);
        alPasado(orden, creada);
        alPasado(normal, creada);

        recordatorios.revisar(creada.plusHours(23));
        assertTrue(recibidos(orden, taller).isEmpty(), "antes de las 24 h no se recuerda nada");

        assertTrue(recordatorios.revisar(creada.plusHours(25)) >= 1);
        List<JsonNode> primero = recibidos(orden, taller);
        assertEquals(1, primero.size(), "a las 25 h, un recordatorio al taller");
        JsonNode carga = primero.get(0);
        String referencia = referencia(orden);
        assertEquals("Urgente: Cambiar el forro", carga.get("titulo").asText());
        assertEquals("Nº " + resumenDe(orden).posicionCola() + " de la cola · " + referencia,
                carga.get("cuerpo").asText());
        assertEquals("/app/ordenes/" + orden.getId(), carga.get("url").asText());
        assertEquals("recordatorio-" + orden.getId(), carga.get("tag").asText());
        assertTrue(recibidos(orden, despacho).isEmpty(), "la orden se le recuerda al taller, no al despacho");

        recordatorios.revisar(creada.plusHours(26));
        assertEquals(1, recibidos(orden, taller).size(), "a las 26 h no se repite: el último es de hace 1 h");

        recordatorios.revisar(creada.plusHours(49));
        List<JsonNode> segundo = recibidos(orden, taller);
        assertEquals(2, segundo.size(), "a las 49 h, el segundo");
        assertEquals(2, jdbc.queryForObject("select count(*) from aviso_enviado where modificacion_id = ?"
                + " and tipo = 'RECORDATORIO' and usuario_id = ?", Integer.class, orden.getId(), taller.getId()),
                "cada envío se apunta, una fila por destinatario");

        recordatorios.revisar(creada.plusDays(30));
        assertTrue(recibidos(normal, taller).isEmpty() && recibidos(normal, despacho).isEmpty(),
                "una orden que no es urgente no caduca ni tiene recordatorio propio: la recuerda el resumen diario");
    }

    @Test
    void unAvisoSinAplicarAvisaAlDespachoCadaSieteDias() throws IOException {
        Modificacion aviso = aviso("SIN-APLICAR");
        LocalDateTime creada = LocalDateTime.of(2002, 3, 1, 8, 0);
        alPasado(aviso, creada);

        recordatorios.revisar(creada.plusDays(6));
        assertTrue(recibidos(aviso, despacho).isEmpty(), "ni a las 24 h: los avisos ya no llevan prioridad");

        recordatorios.revisar(creada.plusDays(7).plusHours(1));
        List<JsonNode> porAplicar = recibidos(aviso, despacho);
        assertEquals(1, porAplicar.size());
        assertEquals("Por aplicar: Piel / material", porAplicar.get(0).get("titulo").asText());
        assertEquals("ICSUITE / Excel de escandallos pendientes · " + referencia(aviso),
                porAplicar.get(0).get("cuerpo").asText());
        assertEquals("/app/bandeja/" + aviso.getId(), porAplicar.get(0).get("url").asText());
        assertTrue(recibidos(aviso, taller).isEmpty());

        recordatorios.revisar(creada.plusDays(10));
        assertEquals(1, recibidos(aviso, despacho).size(), "dentro de los 7 días no se repite");
        recordatorios.revisar(creada.plusDays(14).plusHours(2));
        assertEquals(2, recibidos(aviso, despacho).size(), "y pasados, sí");
    }

    @Test
    void elResumenDeLaColaSaleUnaVezAlDiaDesdeSuHora() throws IOException {
        orden("RESUMEN", false);
        // Un día propio de este test (un lunes): el resumen se apunta por día, sea cual sea la orden.
        LocalDateTime dia = LocalDateTime.of(1985, 6, 3, 0, 0);
        assertEquals(java.time.DayOfWeek.MONDAY, dia.getDayOfWeek());

        assertFalse(recordatorios.resumenDeLaCola(dia.plusHours(7), 8), "antes de su hora, no");
        assertTrue(recordatorios.resumenDeLaCola(dia.plusHours(8).plusMinutes(30), 8));
        List<ModificacionResumen> cola = modificaciones.cola();
        List<JsonNode> resumenes = resumenesDeLaCola();
        assertEquals(1, resumenes.size());
        JsonNode carga = resumenes.get(0);
        assertEquals("Tienes " + cola.size() + (cola.size() == 1 ? " orden por hacer" : " órdenes por hacer"),
                carga.get("titulo").asText());
        ModificacionResumen primera = cola.get(0);
        assertEquals("La siguiente: " + primera.modeloReferencia() + " · " + primera.titulo(),
                carga.get("cuerpo").asText());
        assertEquals("/app/ordenes", carga.get("url").asText());
        assertEquals(primera.id(), carga.get("modificacionId").asLong());

        assertFalse(recordatorios.resumenDeLaCola(dia.plusHours(15), 8), "una sola vez al día");
        assertTrue(recordatorios.resumenDeLaCola(dia.plusDays(1).plusHours(9), 8), "y al día siguiente, otra");
        assertEquals(2, resumenesDeLaCola().size());

        assertFalse(recordatorios.resumenDeLaCola(dia.plusDays(5).plusHours(9), 8), "el sábado no sale");
        assertFalse(recordatorios.resumenDeLaCola(dia.plusDays(6).plusHours(9), 8), "ni el domingo");
        assertTrue(recordatorios.resumenDeLaCola(dia.plusDays(7).plusHours(9), 8), "el lunes vuelve");
        assertEquals(3, resumenesDeLaCola().size());
    }

    @Test
    void unaHechaNoRecibeNada() throws IOException {
        Modificacion orden = orden("HECHA", true);
        modificaciones.responder(orden.getId(), "¿Qué forro?", List.of(), actual(taller));
        modificaciones.marcarHecha(orden.getId(), actual(taller));
        Modificacion aviso = aviso("HECHO");
        modificaciones.marcarHecha(aviso.getId(), actual(despacho));
        Modificacion descartado = aviso("DESCARTADO");
        modificaciones.descartar(descartado.getId(), "Ya estaba así", actual(despacho));
        LocalDateTime creada = LocalDateTime.of(2003, 3, 1, 8, 0);
        for (Modificacion m : List.of(orden, aviso, descartado)) {
            alPasado(m, creada);
        }
        esperarALaCola();
        enviador.envios.clear();

        recordatorios.revisar(creada.plusDays(30));

        for (Modificacion m : List.of(orden, aviso, descartado)) {
            assertTrue(recibidos(m, taller).isEmpty() && recibidos(m, despacho).isEmpty(),
                    "lo cerrado no insiste: " + m.getId());
        }
    }

    @Test
    void unaRespuestaPendienteAvisaAlLadoQueDebeResponder() throws IOException {
        Modificacion orden = orden("RESPUESTA", false);
        Mensaje pregunta = modificaciones.responder(orden.getId(), "¿Qué forro?", List.of(), actual(taller));
        LocalDateTime creada = LocalDateTime.of(2004, 3, 1, 8, 0);
        alPasado(orden, creada);
        alPasado(pregunta, creada.plusHours(1));
        esperarALaCola();
        enviador.envios.clear();

        recordatorios.revisar(creada.plusHours(24));
        assertTrue(respuestas(orden, despacho).isEmpty(), "hasta las 24 h del último mensaje no se insiste");

        recordatorios.revisar(creada.plusHours(26));
        List<JsonNode> alDespacho = respuestas(orden, despacho);
        assertEquals(1, alDespacho.size(), "la pelota está en el despacho");
        assertEquals("Respuesta pendiente: Cambiar el forro", alDespacho.get(0).get("titulo").asText());
        assertEquals(taller.getNombre() + " espera respuesta · " + referencia(orden),
                alDespacho.get(0).get("cuerpo").asText());
        assertEquals("/app/bandeja/" + orden.getId(), alDespacho.get(0).get("url").asText());
        assertTrue(respuestas(orden, taller).isEmpty());

        recordatorios.revisar(creada.plusHours(30));
        assertEquals(1, respuestas(orden, despacho).size(), "no se repite dentro de las 24 h");

        Mensaje contestacion = modificaciones.responder(orden.getId(), "El de siempre", List.of(), actual(despacho));
        alPasado(contestacion, creada.plusHours(31));
        esperarALaCola();
        recordatorios.revisar(creada.plusHours(56));
        List<JsonNode> alTaller = respuestas(orden, taller);
        assertEquals(1, alTaller.size(), "ahora espera el taller");
        assertEquals(despacho.getNombre() + " espera respuesta · " + referencia(orden),
                alTaller.get(0).get("cuerpo").asText());
        assertEquals("/app/ordenes/" + orden.getId(), alTaller.get(0).get("url").asText());
        assertEquals(1, respuestas(orden, despacho).size());
    }

    /** Los resúmenes de la cola que le han llegado al taller, en orden. */
    private List<JsonNode> resumenesDeLaCola() throws IOException {
        List<JsonNode> lista = new ArrayList<>();
        for (EnviadorFalso.Envio envio : enviador.envios) {
            JsonNode carga = json.readTree(envio.payload());
            if (taller.getId().equals(envio.usuarioId()) && carga.get("titulo").asText().startsWith("Tienes ")) {
                lista.add(carga);
            }
        }
        return lista;
    }

    private List<JsonNode> respuestas(Modificacion m, Usuario usuario) throws IOException {
        List<JsonNode> lista = new ArrayList<>();
        for (JsonNode carga : recibidos(m, usuario)) {
            if (carga.get("titulo").asText().startsWith("Respuesta pendiente")) {
                lista.add(carga);
            }
        }
        return lista;
    }

    /** Lo que le ha llegado a ese usuario de esa modificación, en orden. */
    private List<JsonNode> recibidos(Modificacion m, Usuario usuario) throws IOException {
        List<JsonNode> lista = new ArrayList<>();
        for (EnviadorFalso.Envio envio : enviador.envios) {
            JsonNode carga = json.readTree(envio.payload());
            if (usuario.getId().equals(envio.usuarioId()) && carga.get("modificacionId").asLong() == m.getId()) {
                lista.add(carga);
            }
        }
        return lista;
    }

    private ModificacionResumen resumenDe(Modificacion m) {
        return modificaciones.detalle(m.getId()).resumen();
    }

    private Modificacion orden(String que, boolean urgente) {
        Modificacion orden = modificaciones.crearOrdenParaTaller(new Modificaciones.OrdenDespacho(modelo(que), null,
                "Cambiar el forro", "", urgente, null, List.of()), actual(despacho));
        esperarALaCola();
        enviador.envios.clear();
        return orden;
    }

    private Modificacion aviso(String que) {
        Modificacion aviso = modificaciones.crearAvisoDeTaller(new Modificaciones.AvisoTaller(modelo(que), "PIEL",
                List.of(ModificacionesTest.foto())), actual(taller));
        esperarALaCola();
        enviador.envios.clear();
        return aviso;
    }

    /** La modificación y sus mensajes pasan a haberse creado entonces. */
    private void alPasado(Modificacion m, LocalDateTime cuando) {
        jdbc.update("update modificacion set creada_en = ? where id = ?", cuando, m.getId());
        jdbc.update("update mensaje set creado_en = ? where modificacion_id = ?", cuando, m.getId());
    }

    private void alPasado(Mensaje mensaje, LocalDateTime cuando) {
        jdbc.update("update mensaje set creado_en = ? where id = ?", cuando, mensaje.getId());
    }

    private String referencia(Modificacion m) {
        return repositorioModelos.findById(m.getModeloId()).orElseThrow().nombreVisible();
    }

    private Long modelo(String codigo) {
        return repositorioModelos.save(new Modelo("AMI", "T18-" + codigo + "-" + CONTADOR.incrementAndGet(),
                "Bolso T18", TipoArticulo.BOLSO)).getId();
    }

    private static UsuarioActual actual(Usuario usuario) {
        return new UsuarioActual(usuario.getId(), usuario.getNombre(), usuario.getRol(), null);
    }

    private void esperarALaCola() {
        long limite = System.currentTimeMillis() + ESPERA_MAXIMA_MS;
        while (push.pendientes() > 0) {
            if (System.currentTimeMillis() > limite) {
                fail("Los push no han acabado en " + ESPERA_MAXIMA_MS / 1000 + " s");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrumpido esperando a los push");
            }
        }
    }
}
