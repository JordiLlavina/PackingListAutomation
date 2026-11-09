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
import com.puntotres.packinglist.modelos.persistence.Prioridad;
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
    void unaOrdenAltaRecibeRecordatorioALas24hYNoDos() throws IOException {
        Modificacion orden = orden("ALTA", Prioridad.ALTA);
        LocalDateTime creada = LocalDateTime.of(2001, 3, 1, 8, 0);
        alPasado(orden, creada);

        recordatorios.revisar(creada.plusHours(23));
        assertTrue(recibidos(orden, taller).isEmpty(), "antes de las 24 h no se recuerda nada");

        assertTrue(recordatorios.revisar(creada.plusHours(25)) >= 1);
        List<JsonNode> primero = recibidos(orden, taller);
        assertEquals(1, primero.size(), "a las 25 h, un recordatorio al taller");
        JsonNode carga = primero.get(0);
        String referencia = referencia(orden);
        assertEquals("Pendiente: Cambiar el forro", carga.get("titulo").asText());
        assertEquals("Desde hace 1 día · " + referencia, carga.get("cuerpo").asText());
        assertEquals("/app/ordenes/" + orden.getId(), carga.get("url").asText());
        assertEquals("recordatorio-" + orden.getId(), carga.get("tag").asText());
        assertTrue(recibidos(orden, despacho).isEmpty(), "la orden se le recuerda al taller, no al despacho");

        recordatorios.revisar(creada.plusHours(26));
        assertEquals(1, recibidos(orden, taller).size(), "a las 26 h no se repite: el último es de hace 1 h");

        recordatorios.revisar(creada.plusHours(49));
        List<JsonNode> segundo = recibidos(orden, taller);
        assertEquals(2, segundo.size(), "a las 49 h, el segundo");
        assertEquals("Desde hace 2 días · " + referencia, segundo.get(1).get("cuerpo").asText());
        assertEquals(2, jdbc.queryForObject("select count(*) from aviso_enviado where modificacion_id = ?"
                + " and tipo = 'RECORDATORIO' and usuario_id = ?", Integer.class, orden.getId(), taller.getId()),
                "cada envío se apunta, una fila por destinatario");
    }

    @Test
    void unAvisoSinPrioridadAvisaADespachoALas24h() throws IOException {
        Modificacion aviso = aviso("SIN-PRIORIDAD");
        LocalDateTime creada = LocalDateTime.of(2002, 3, 1, 8, 0);
        alPasado(aviso, creada);
        Modificacion recargado = repositorioModificaciones.findById(aviso.getId()).orElseThrow();

        recordatorios.revisar(creada.plusHours(23));
        assertTrue(recibidos(aviso, despacho).isEmpty());
        assertFalse(recordatorios.vencida(recargado, creada.plusHours(23)));

        recordatorios.revisar(creada.plusHours(25));
        List<JsonNode> recibido = recibidos(aviso, despacho);
        assertEquals(1, recibido.size(), "a las 25 h, al despacho");
        JsonNode carga = recibido.get(0);
        assertEquals("Aviso sin prioridad: Piel / material", carga.get("titulo").asText());
        assertEquals(referencia(aviso) + " · de " + taller.getNombre(), carga.get("cuerpo").asText());
        assertEquals("/app/bandeja/" + aviso.getId(), carga.get("url").asText());
        assertTrue(recibidos(aviso, taller).isEmpty());
        assertTrue(recordatorios.vencida(recargado, creada.plusHours(25)));

        recordatorios.revisar(creada.plusHours(30));
        assertEquals(1, recibidos(aviso, despacho).size(), "dentro de las 24 h no se repite");
        recordatorios.revisar(creada.plusHours(49));
        assertEquals(2, recibidos(aviso, despacho).size(), "y pasadas, sí");

        // Con prioridad puesta deja de ser «sin prioridad» y pasa a «por aplicar», cada 7 días.
        modificaciones.ponerPrioridad(aviso.getId(), Prioridad.NORMAL, actual(despacho));
        enviador.envios.clear();
        recordatorios.revisar(creada.plusDays(6));
        assertTrue(recibidos(aviso, despacho).isEmpty());
        recordatorios.revisar(creada.plusDays(7).plusHours(1));
        List<JsonNode> porAplicar = recibidos(aviso, despacho);
        assertEquals(1, porAplicar.size());
        assertEquals("Por aplicar: Piel / material", porAplicar.get(0).get("titulo").asText());
        assertEquals("ICSUITE / Excel de escandallos pendientes · " + referencia(aviso),
                porAplicar.get(0).get("cuerpo").asText());
    }

    @Test
    void unaHechaNoRecibeNada() throws IOException {
        Modificacion orden = orden("HECHA", Prioridad.ALTA);
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
            assertFalse(recordatorios.vencida(repositorioModificaciones.findById(m.getId()).orElseThrow(),
                    creada.plusDays(30)));
        }
    }

    @Test
    void vencidaCuentaEnElResumen() {
        Modificacion orden = orden("VENCIDA", Prioridad.ALTA);
        // El año más viejo de esta clase: a estas horas nada más de la base está vencido.
        LocalDateTime creada = LocalDateTime.of(1990, 3, 1, 8, 0);
        alPasado(orden, creada);

        reloj.fijar(creada.plusHours(23));
        assertEquals(0, modificaciones.resumen(actual(taller)).vencidas());
        assertFalse(resumenDe(orden).vencida());

        reloj.fijar(creada.plusHours(25));
        assertEquals(1, modificaciones.resumen(actual(taller)).vencidas(), "la orden del taller ya está vencida");
        assertTrue(resumenDe(orden).vencida(), "y la ficha lo dice");
        assertEquals(0, modificaciones.resumen(actual(despacho)).vencidas(),
                "al despacho le cuentan sus avisos, no las órdenes del taller");
    }

    @Test
    void unaRespuestaPendienteAvisaAlLadoQueDebeResponder() throws IOException {
        Modificacion orden = orden("RESPUESTA", Prioridad.BAJA);
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

    private Modificacion orden(String que, Prioridad prioridad) {
        Modificacion orden = modificaciones.crearOrdenParaTaller(new Modificaciones.OrdenDespacho(modelo(que), null,
                "Cambiar el forro", "", prioridad, List.of()), actual(despacho));
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
