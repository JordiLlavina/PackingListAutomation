package com.puntotres.packinglist.modelos.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.modelos.ModelosProperties;
import com.puntotres.packinglist.modelos.persistence.Modificacion;
import com.puntotres.packinglist.modelos.persistence.ModificacionRepository;
import com.puntotres.packinglist.modelos.persistence.Prioridad;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.SuscripcionPush;
import com.puntotres.packinglist.modelos.persistence.SuscripcionPushRepository;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloRepository;
import com.puntotres.packinglist.persistence.TipoArticulo;

/**
 * Los avisos al móvil: a quién le llegan, qué dicen, qué pasa con una
 * suscripción que el navegador ya ha tirado y que nada de esto rompe una
 * petición. El envío de verdad lo hace un {@link EnviadorPush} falso que
 * apunta a quién habría mandado qué.
 *
 * El test no es {@code @Transactional} a propósito: el push sale cuando se
 * confirma la transacción que crea la modificación, y dentro de una
 * transacción de test no se confirmaría nunca. Los usuarios y los modelos se
 * llaman {@code T17-…}; como la base de datos puede tener suscripciones de
 * otros tests, los envíos se miran solo los de los usuarios de cada test.
 */
@SpringBootTest
class PushTest {

    /** Lo más que se espera al hilo de los push. */
    private static final long ESPERA_MAXIMA_MS = 5_000;

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    @Autowired
    private Push push;

    @Autowired
    private Modificaciones modificaciones;

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private ModeloRepository repositorioModelos;

    @Autowired
    private ModificacionRepository repositorioModificaciones;

    @Autowired
    private SuscripcionPushRepository suscripciones;

    @Autowired
    private EnviadorFalso enviador;

    private final ObjectMapper json = new ObjectMapper();

    @TestConfiguration
    static class Falsos {

        @Bean
        @Primary
        EnviadorFalso enviadorFalso() {
            return new EnviadorFalso();
        }
    }

    /** Un servidor de push de mentira: apunta cada envío y da por caducados los endpoints que se le digan. */
    static class EnviadorFalso implements EnviadorPush {

        record Envio(Long usuarioId, String endpoint, String payload) {
        }

        final List<Envio> envios = new CopyOnWriteArrayList<>();
        final Set<String> caducados = ConcurrentHashMap.newKeySet();

        @Override
        public void enviar(SuscripcionPush s, String payloadJson) throws IOException {
            if (caducados.contains(s.getEndpoint())) {
                throw new SuscripcionCaducada("410 Gone");
            }
            envios.add(new Envio(s.getUsuarioId(), s.getEndpoint(), payloadJson));
        }
    }

    @BeforeEach
    void limpiar() {
        esperarALaCola();
        enviador.envios.clear();
        enviador.caducados.clear();
    }

    @Test
    void crearUnaOrdenAvisaAlTallerYUnAvisoADespacho() throws Exception {
        Usuario taller = usuario(Rol.TALLER);
        Usuario despacho = usuario(Rol.DESPACHO);
        Usuario admin = usuario(Rol.ADMIN);
        Usuario tallerDesactivado = usuario(Rol.TALLER);
        usuarios.cambiar(tallerDesactivado.getId(), null, null, false, null);
        suscribir(taller, "taller");
        suscribir(despacho, "despacho");
        suscribir(admin, "admin");
        suscribir(tallerDesactivado, "desactivado");
        Long modeloId = modelo("ORDEN");

        Modificacion orden = modificaciones.crearOrdenParaTaller(new Modificaciones.OrdenDespacho(modeloId, null,
                "Cambiar el forro", "", Prioridad.ALTA, List.of()), actual(despacho));
        esperarALaCola();

        List<EnviadorFalso.Envio> deLaOrden = enviosDe(orden.getId(), taller, despacho, admin, tallerDesactivado);
        assertEquals(List.of(taller.getId()), deLaOrden.stream().map(EnviadorFalso.Envio::usuarioId).toList(),
                "la orden solo le llega al taller activo");
        JsonNode carga = json.readTree(deLaOrden.get(0).payload());
        assertEquals("Orden de despacho: Cambiar el forro", carga.get("titulo").asText());
        String referencia = repositorioModelos.findById(modeloId).orElseThrow().getCodigo();
        assertEquals(referencia + " · Bolso T17", carga.get("cuerpo").asText());
        assertEquals("/app/ordenes/" + orden.getId(), carga.get("url").asText());
        assertEquals(orden.getId().longValue(), carga.get("modificacionId").asLong());
        assertEquals("modificacion-" + orden.getId(), carga.get("tag").asText());

        Modificacion aviso = modificaciones.crearAvisoDeTaller(new Modificaciones.AvisoTaller(modeloId, "PIEL",
                List.of(ModificacionesTest.foto())), actual(taller));
        esperarALaCola();

        List<EnviadorFalso.Envio> delAviso = enviosDe(aviso.getId(), taller, despacho, admin, tallerDesactivado);
        assertEquals(Set.of(despacho.getId(), admin.getId()),
                Set.copyOf(delAviso.stream().map(EnviadorFalso.Envio::usuarioId).toList()),
                "el aviso le llega al despacho, y el administrador cuenta como despacho");
        assertEquals(2, delAviso.size());
        JsonNode delDespacho = json.readTree(delAviso.get(0).payload());
        assertEquals("Aviso del taller: Piel / material", delDespacho.get("titulo").asText());
        assertEquals(referencia + " · de " + taller.getNombre(), delDespacho.get("cuerpo").asText());
        assertEquals("/app/bandeja/" + aviso.getId(), delDespacho.get("url").asText());
    }

    @Test
    void unaSuscripcionCaducadaSeBorraYLaModificacionYaEstaCreada() throws IOException {
        Usuario taller = usuario(Rol.TALLER);
        Usuario otroTaller = usuario(Rol.TALLER);
        Usuario despacho = usuario(Rol.DESPACHO);
        String caducado = suscribir(taller, "caducado");
        String bueno = suscribir(otroTaller, "bueno");
        enviador.caducados.add(caducado);

        Modificacion orden = assertDoesNotThrow(() -> modificaciones.crearOrdenParaTaller(
                new Modificaciones.OrdenDespacho(modelo("CADUCADA"), null, "Coser el asa", "", Prioridad.NORMAL,
                        List.of()), actual(despacho)));
        esperarALaCola();

        assertTrue(repositorioModificaciones.findById(orden.getId()).isPresent(),
                "la orden se queda creada aunque falle un push");
        assertFalse(existe(caducado), "la suscripción que el navegador ya ha tirado se borra");
        assertTrue(existe(bueno), "la otra sigue");
        assertEquals(List.of(otroTaller.getId()), enviosDe(orden.getId(), taller, otroTaller).stream()
                .map(EnviadorFalso.Envio::usuarioId).toList());
    }

    @Test
    void sinClavesVapidNoSeEnviaNadaYNoFalla() throws IOException {
        // En los tests no hay claves VAPID (application-test.yml las deja vacías).
        assertFalse(push.configurado());
        assertEquals("", push.clavePublica());

        ModelosProperties sinClaves = new ModelosProperties(null, null, List.of(), List.of(), null,
                new ModelosProperties.Push("", "", "mailto:prueba@puntotres.com"), null);
        EnviadorPush apagado = new PushConfig().enviadorPush(sinClaves);
        assertInstanceOf(EnviadorPushDesactivado.class, apagado);
        assertDoesNotThrow(() -> apagado.enviar(new SuscripcionPush(1L, null, "https://push.example/x", "p", "a"),
                "{}"));

        ModelosProperties conClavesMalas = new ModelosProperties(null, null, List.of(), List.of(), null,
                new ModelosProperties.Push("no-es-una-clave", "tampoco", "mailto:prueba@puntotres.com"), null);
        assertInstanceOf(EnviadorPushDesactivado.class, new PushConfig().enviadorPush(conClavesMalas),
                "unas claves que no se dejan leer apagan el push, no la aplicación");
    }

    @Test
    void suscribirDosVecesElMismoEndpointNoDuplica() {
        Usuario taller = usuario(Rol.TALLER);
        Usuario despacho = usuario(Rol.DESPACHO);
        String endpoint = "https://push.example/T17-" + CONTADOR.incrementAndGet();

        push.suscribir(actual(taller), endpoint, "clave-1", "auth-1");
        push.suscribir(actual(taller), endpoint, "clave-2", "auth-2");
        assertEquals(1, conEndpoint(endpoint).size());
        assertEquals("clave-2", conEndpoint(endpoint).get(0).getP256dh());

        // El mismo navegador con otra persona dentro: la suscripción pasa a ser suya.
        push.suscribir(actual(despacho), endpoint, "clave-3", "auth-3");
        List<SuscripcionPush> ahora = conEndpoint(endpoint);
        assertEquals(1, ahora.size());
        assertEquals(despacho.getId(), ahora.get(0).getUsuarioId());

        push.borrar(actual(taller), endpoint);
        assertEquals(1, conEndpoint(endpoint).size(), "solo la borra su dueño");
        push.borrar(actual(despacho), endpoint);
        assertTrue(conEndpoint(endpoint).isEmpty());
    }

    @Test
    void unaRespuestaAUnAvisoCerradoTambienAvisa() throws Exception {
        Usuario taller = usuario(Rol.TALLER);
        Usuario despacho = usuario(Rol.DESPACHO);
        suscribir(taller, "taller");
        suscribir(despacho, "despacho");
        Modificacion aviso = modificaciones.crearAvisoDeTaller(new Modificaciones.AvisoTaller(modelo("CERRADO"),
                "FORRO", List.of(ModificacionesTest.foto())), actual(taller));
        modificaciones.descartar(aviso.getId(), "Ya estaba así", actual(despacho));
        esperarALaCola();
        enviador.envios.clear();

        String largo = "Lo descarto porque el forro ya era ese, el de la temporada pasada, y no hay que cambiar"
                + " nada en el escandallo";
        modificaciones.responder(aviso.getId(), largo, List.of(), actual(despacho));
        esperarALaCola();

        List<EnviadorFalso.Envio> respuesta = enviosDe(aviso.getId(), taller, despacho);
        assertEquals(List.of(taller.getId()), respuesta.stream().map(EnviadorFalso.Envio::usuarioId).toList(),
                "la respuesta del despacho le llega al taller aunque el aviso esté descartado");
        JsonNode carga = json.readTree(respuesta.get(0).payload());
        assertEquals("Respuesta de " + despacho.getNombre(), carga.get("titulo").asText());
        assertEquals(largo.substring(0, 80) + "…", carga.get("cuerpo").asText(), "los 80 primeros caracteres");
        assertEquals("/app/avisos", carga.get("url").asText(), "el taller no tiene pantalla de un aviso suelto");

        enviador.envios.clear();
        modificaciones.responder(aviso.getId(), "", List.of(ModificacionesTest.audio(5)), actual(taller));
        esperarALaCola();
        List<EnviadorFalso.Envio> delTaller = enviosDe(aviso.getId(), taller, despacho);
        assertEquals(List.of(despacho.getId()), delTaller.stream().map(EnviadorFalso.Envio::usuarioId).toList());
        JsonNode deVoz = json.readTree(delTaller.get(0).payload());
        assertEquals("Nota de voz", deVoz.get("cuerpo").asText());
        assertEquals("/app/bandeja/" + aviso.getId(), deVoz.get("url").asText());
    }

    private List<EnviadorFalso.Envio> enviosDe(Long modificacionId, Usuario... suyos) throws IOException {
        Set<Long> ids = Arrays.stream(suyos).map(Usuario::getId).collect(Collectors.toSet());
        List<EnviadorFalso.Envio> lista = new ArrayList<>();
        for (EnviadorFalso.Envio envio : enviador.envios) {
            if (ids.contains(envio.usuarioId())
                    && json.readTree(envio.payload()).get("modificacionId").asLong() == modificacionId) {
                lista.add(envio);
            }
        }
        return lista;
    }

    private String suscribir(Usuario usuario, String que) {
        String endpoint = "https://push.example/T17-" + que + "-" + CONTADOR.incrementAndGet();
        push.suscribir(actual(usuario), endpoint, "p256dh-" + que, "auth-" + que);
        return endpoint;
    }

    private boolean existe(String endpoint) {
        return !conEndpoint(endpoint).isEmpty();
    }

    private List<SuscripcionPush> conEndpoint(String endpoint) {
        return suscripciones.findAll().stream().filter(s -> endpoint.equals(s.getEndpoint())).toList();
    }

    private Usuario usuario(Rol rol) {
        int n = CONTADOR.incrementAndGet();
        return usuarios.crear(new Usuarios.Alta("T17 " + rol + " " + n, "t17-" + rol.name().toLowerCase() + "-" + n,
                "prueba", rol));
    }

    private Long modelo(String codigo) {
        return repositorioModelos.save(new Modelo("AMI", "T17-" + codigo + "-" + CONTADOR.incrementAndGet(),
                "Bolso T17", TipoArticulo.BOLSO)).getId();
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
