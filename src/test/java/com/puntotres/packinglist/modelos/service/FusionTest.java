package com.puntotres.packinglist.modelos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.modelos.ComoUsuario;
import com.puntotres.packinglist.modelos.api.dto.CodigoHistorico;
import com.puntotres.packinglist.modelos.api.dto.DocumentoResumen;
import com.puntotres.packinglist.modelos.api.dto.Evento;
import com.puntotres.packinglist.modelos.api.dto.Fusion;
import com.puntotres.packinglist.modelos.api.dto.ModeloFicha;
import com.puntotres.packinglist.modelos.persistence.CambioEtapa;
import com.puntotres.packinglist.modelos.persistence.CambioEtapaRepository;
import com.puntotres.packinglist.modelos.persistence.AdjuntoRepository;
import com.puntotres.packinglist.modelos.persistence.DocumentoModeloRepository;
import com.puntotres.packinglist.modelos.persistence.Mensaje;
import com.puntotres.packinglist.modelos.persistence.MensajeRepository;
import com.puntotres.packinglist.modelos.persistence.ModeloCodigoHistoricoRepository;
import com.puntotres.packinglist.modelos.persistence.Modificacion;
import com.puntotres.packinglist.modelos.persistence.ModificacionRepository;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.Articulo;
import com.puntotres.packinglist.persistence.ArticuloRepository;
import com.puntotres.packinglist.persistence.Etapa;
import com.puntotres.packinglist.persistence.FicheroExcel;
import com.puntotres.packinglist.persistence.FotoCatalogoRepository;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloRepository;
import com.puntotres.packinglist.persistence.ModeloTemporadaRepository;
import com.puntotres.packinglist.persistence.ResumenFoto;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;

/**
 * Asignar la referencia a un prototipo y, si ya existe un modelo con ella
 * (el que ha entrado por el pedido), juntar los dos. La base de datos es la
 * misma para toda la suite: los códigos de este test empiezan por
 * {@code T19-} (o {@code T22-} los de los documentos y los arreglos de la
 * fusión), los prototipos por {@code PROTO T19} (o {@code PROTO T22}) y las
 * temporadas igual.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FusionTest {

    private static final byte[] JPEG = {1, 2, 3};

    @Autowired
    private Modelos modelos;

    @Autowired
    private Documentos documentos;

    @Autowired
    private DocumentoModeloRepository documentosRepositorio;

    @Autowired
    private Modificaciones modificaciones;

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private ArchivoTemporadas temporadas;

    @Autowired
    private CatalogoModelos catalogo;

    @Autowired
    private FotosCatalogo fotos;

    @Autowired
    private ModeloRepository repositorioModelos;

    @Autowired
    private ArticuloRepository articulos;

    @Autowired
    private FotoCatalogoRepository fotosRepositorio;

    @Autowired
    private ModeloTemporadaRepository vinculos;

    @Autowired
    private ModificacionRepository modificacionesRepositorio;

    @Autowired
    private CambioEtapaRepository cambiosEtapa;

    @Autowired
    private ModeloCodigoHistoricoRepository codigosHistoricos;

    @Autowired
    private MensajeRepository mensajes;

    @Autowired
    private AdjuntoRepository adjuntosRepositorio;

    @Autowired
    private MockMvc mvc;

    @Test
    void asignarUnCodigoLibreLoEscribeYConservaElPrototipo() {
        Long temporada = temporada("T19-LIBRE");
        Modelo proto = prototipo("PROTO T19 LIBRE", temporada);

        Fusion previa = modelos.previsualizarReferencia(proto.getId(), " t19-libre ");
        assertNull(previa.absorbido());

        Modelo asignado = modelos.asignarReferencia(proto.getId(), " t19-libre ", false, despacho());

        assertEquals(proto.getId(), asignado.getId());
        Modelo guardado = repositorioModelos.findById(proto.getId()).orElseThrow();
        assertEquals("T19-LIBRE", guardado.getCodigo());
        assertEquals("PROTO T19 LIBRE", guardado.getNombrePrototipo());
        assertEquals(Etapa.PROTOTIPO, guardado.getEtapa());

        ModeloFicha ficha = modelos.ficha(proto.getId());
        CodigoHistorico referencia = ficha.codigos().stream()
                .filter(codigo -> "T19-LIBRE".equals(codigo.codigo())).findFirst().orElseThrow();
        assertEquals("REFERENCIA", referencia.motivo());
        assertNull(referencia.hasta());
        CodigoHistorico delPrototipo = ficha.codigos().stream()
                .filter(codigo -> "PROTO T19 LIBRE".equals(codigo.codigo())).findFirst().orElseThrow();
        assertNotNull(delPrototipo.hasta(), "el nombre de prototipo deja de ser el vigente");
        assertTrue(ficha.fusionadosDesde().isEmpty());

        IllegalArgumentException repetida = assertThrows(IllegalArgumentException.class,
                () -> modelos.asignarReferencia(proto.getId(), "T19-LIBRE", false, despacho()));
        assertEquals("El modelo ya tiene esa referencia", repetida.getMessage());
        IllegalArgumentException vacia = assertThrows(IllegalArgumentException.class,
                () -> modelos.asignarReferencia(proto.getId(), "  ", false, despacho()));
        assertEquals("Falta la referencia", vacia.getMessage());
    }

    @Test
    void asignarUnCodigoQueYaExisteSinConfirmarDevuelveLaPrevisualizacion() {
        Long temporada = temporada("T19-PREVIA");
        Modelo proto = prototipo("PROTO T19 PREVIA", temporada);
        Modelo importado = importado("T19-PREVIA.AL0001");
        fotos.guardar(importado.getId(), "a.jpg", JPEG);

        FusionPendiente pendiente = assertThrows(FusionPendiente.class,
                () -> modelos.asignarReferencia(proto.getId(), "t19-previa", false, despacho()));

        assertEquals("Ya existe T19-PREVIA: se juntará con el prototipo", pendiente.getMessage());
        assertEquals(new Fusion(proto.getId(), importado.getId(), 1, 1, 0, 0), pendiente.fusion());
        assertEquals(pendiente.fusion(), modelos.previsualizarReferencia(proto.getId(), "T19-PREVIA"));
        assertTrue(repositorioModelos.existsById(importado.getId()), "sin confirmar no se toca nada");
        assertNull(repositorioModelos.findById(proto.getId()).orElseThrow().getCodigo());
    }

    @Test
    void fusionarDejaUnSoloModeloConTodoYElQrViejoResuelve() throws Exception {
        Long temporada = temporada("T19-FUSION");
        Long delPedido = temporada("T19-FUSION-PEDIDO");
        Modelo proto = prototipo("PROTO T19 FUSION", temporada);
        fotos.guardar(proto.getId(), "a.jpg", JPEG);
        fotos.guardar(proto.getId(), "b.jpg", JPEG);
        modificaciones.crearOrdenParaTaller(new Modificaciones.OrdenDespacho(proto.getId(), null, "T19 cambio",
                "texto", List.of()), despacho());

        Modelo importado = importado("T19-FUSION.AL0001");
        catalogo.guardarCampoModelo("AMI", "T19-FUSION", CatalogoModelos.CampoModelo.NOMBRE, "Bolso T19");
        fotos.guardar(importado.getId(), "a.jpg", JPEG);
        catalogo.vincularTemporada(importado.getId(), delPedido);
        Long viejo = importado.getId();

        Modelo superviviente = modelos.asignarReferencia(proto.getId(), "T19-FUSION", true, despacho());

        assertEquals(proto.getId(), superviviente.getId(), "sobrevive el de menor id, el prototipo");
        assertFalse(repositorioModelos.existsById(viejo));
        Modelo guardado = repositorioModelos.findById(proto.getId()).orElseThrow();
        assertEquals("T19-FUSION", guardado.getCodigo());
        assertEquals("PROTO T19 FUSION", guardado.getNombrePrototipo());
        assertEquals("BOLSO T19", guardado.getNombre().toUpperCase());
        assertEquals(Etapa.PRODUCCION, guardado.getEtapa(), "la etapa es la del que tenía código");
        List<CambioEtapa> etapas = cambiosEtapa.findByModeloIdOrderByFechaDescIdDesc(proto.getId());
        assertEquals("Fusión con #" + viejo, etapas.get(0).getMotivo());
        assertEquals(Etapa.PROTOTIPO, etapas.get(0).getDe());
        assertEquals(Etapa.PRODUCCION, etapas.get(0).getA());

        List<ResumenFoto> suyas = fotos.de(proto.getId());
        assertEquals(3, suyas.size());
        assertEquals(Set.of("a.jpg", "b.jpg", "a (2).jpg"),
                suyas.stream().map(ResumenFoto::nombreFichero).collect(Collectors.toSet()));
        assertEquals(1, suyas.stream().filter(ResumenFoto::principal).count());
        assertEquals(List.of("T19-FUSION.AL0001"), catalogo.articulosDelModelo(proto.getId()).stream()
                .map(Articulo::getReferencia).toList());
        assertEquals(1, modificacionesRepositorio.findByModeloIdOrderByCreadaEnDescIdDesc(proto.getId()).size());
        assertEquals(Set.of(temporada, delPedido), Set.copyOf(catalogo.temporadasDeModelo(proto.getId())));

        assertTrue(articulos.findByModeloIdOrderByReferencia(viejo).isEmpty());
        assertEquals(0, fotosRepositorio.countByModeloId(viejo));
        assertTrue(modificacionesRepositorio.findByModeloIdOrderByCreadaEnDescIdDesc(viejo).isEmpty());
        assertTrue(cambiosEtapa.findByModeloIdOrderByFechaDescIdDesc(viejo).isEmpty());
        assertTrue(codigosHistoricos.findByModeloIdOrderByDesdeDescIdDesc(viejo).isEmpty());
        assertTrue(vinculos.findByModeloId(viejo).isEmpty());

        ModeloFicha ficha = modelos.ficha(proto.getId());
        assertTrue(ficha.codigos().stream().noneMatch(codigo -> codigo.codigo().startsWith("#")),
                ficha.codigos().toString());
        assertTrue(ficha.codigos().stream().anyMatch(codigo -> "T19-FUSION".equals(codigo.codigo())));
        assertTrue(ficha.lineaTiempo().stream().map(Evento::texto).noneMatch(texto -> texto.contains("#" + viejo)
                && texto.startsWith("Referencia")), ficha.lineaTiempo().toString());
        assertEquals(List.of(viejo), ficha.fusionadosDesde());

        mvc.perform(get("/m/" + viejo))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/app/modelos/" + proto.getId()));
    }

    @Test
    void fusionarConsigoMismoOEntreClientesSeRechaza() {
        Long temporada = temporada("T19-RECHAZO");
        Modelo proto = prototipo("PROTO T19 RECHAZO", temporada);
        Modelo deApc = modelos.darDeAlta(new Modelos.AltaManual("APC", null, "T19-RECHAZO-APC", null, null, null,
                null), despacho());

        IllegalArgumentException consigo = assertThrows(IllegalArgumentException.class,
                () -> modelos.fusionar(proto.getId(), proto.getId(), despacho()));
        assertEquals("No se puede fusionar un modelo consigo mismo", consigo.getMessage());

        IllegalArgumentException entreClientes = assertThrows(IllegalArgumentException.class,
                () -> modelos.fusionar(proto.getId(), deApc.getId(), despacho()));
        assertEquals("Los dos modelos tienen que ser del mismo cliente", entreClientes.getMessage());

        assertTrue(repositorioModelos.existsById(proto.getId()));
        assertTrue(repositorioModelos.existsById(deApc.getId()));
        assertNull(repositorioModelos.findById(proto.getId()).orElseThrow().getCodigo());
        assertTrue(modelos.ficha(deApc.getId()).fusionadosDesde().isEmpty());
    }

    @Test
    void unaCadenaDeFusionesResuelveAlUltimo() throws Exception {
        Long temporada = temporada("T19-CADENA");
        // Por orden de id, C < B < A: A se junta con B (sobrevive B) y luego B con C (sobrevive C).
        Modelo c = prototipo("PROTO T19 CADENA C", temporada);
        Modelo b = prototipo("PROTO T19 CADENA B", temporada);
        Modelo a = prototipo("PROTO T19 CADENA A", temporada);

        assertEquals(b.getId(), modelos.fusionar(a.getId(), b.getId(), despacho()).getId());
        assertEquals(c.getId(), modelos.fusionar(b.getId(), c.getId(), despacho()).getId());

        assertEquals(c.getId(), modelos.resolverId(a.getId()).orElseThrow());
        assertEquals(c.getId(), modelos.resolverId(b.getId()).orElseThrow());
        assertEquals(Set.of(a.getId(), b.getId()), Set.copyOf(modelos.ficha(c.getId()).fusionadosDesde()));
        mvc.perform(get("/m/" + a.getId()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/app/modelos/" + c.getId()));
    }

    @Test
    void fusionarSeLlevaLosDocumentosAlSuperviviente() {
        Long temporada = temporada("T22-DOCS");
        Modelo proto = prototipo("PROTO T22 DOCS", temporada);
        documentos.guardar(proto.getId(), "boceto.pdf", "application/pdf", JPEG, despacho());
        Modelo importado = importado("T22-DOCS.AL0001");
        documentos.guardar(importado.getId(), "ficha.pdf", "application/pdf", JPEG, despacho());
        documentos.guardar(importado.getId(), "medidas.xlsx", "application/octet-stream", JPEG, despacho());
        Long viejo = importado.getId();

        assertEquals(new Fusion(proto.getId(), viejo, 1, 0, 0, 2),
                modelos.previsualizarReferencia(proto.getId(), "T22-DOCS"));

        modelos.asignarReferencia(proto.getId(), "T22-DOCS", true, despacho());

        assertEquals(0, documentosRepositorio.countByModeloId(viejo));
        assertEquals(Set.of("boceto.pdf", "ficha.pdf", "medidas.xlsx"),
                documentos.de(proto.getId()).stream().map(DocumentoResumen::nombre).collect(Collectors.toSet()));
        assertEquals(3, modelos.ficha(proto.getId()).documentos().size());
    }

    @Test
    void siSobreviveElImportadoSeQuedaConElNombreDePrototipoYSoloUnNombreVigente() {
        Long temporada = temporada("T22-PROTO");
        // El importado primero, para que tenga el id menor y sea él quien sobreviva.
        Modelo importado = importado("T22-PROTO.AL0001");
        Modelo proto = prototipo("PROTO T22 PROTO", temporada);

        Modelo superviviente = modelos.asignarReferencia(proto.getId(), "T22-PROTO", true, despacho());

        assertEquals(importado.getId(), superviviente.getId());
        Modelo guardado = repositorioModelos.findById(importado.getId()).orElseThrow();
        assertEquals("T22-PROTO", guardado.getCodigo());
        assertEquals("PROTO T22 PROTO", guardado.getNombrePrototipo());
        assertEquals(temporada, guardado.getTemporadaAltaId());

        List<CodigoHistorico> codigos = modelos.ficha(importado.getId()).codigos();
        // El importado entró por el pedido, sin apuntes: el único nombre que tenía en el histórico era el del
        // prototipo, y ya no es el vigente.
        assertTrue(codigos.stream().noneMatch(codigo -> codigo.hasta() == null), codigos.toString());
        assertNotNull(codigos.stream().filter(codigo -> "PROTO T22 PROTO".equals(codigo.codigo())).findFirst()
                .orElseThrow().hasta(), "el nombre de prototipo que se ha movido deja de ser el vigente");
        assertTrue(modelos.ficha(importado.getId()).lineaTiempo().stream().map(Evento::texto)
                .anyMatch("Prototipo PROTO T22 PROTO"::equals));
    }

    @Test
    void dosConReferenciaNoSeFusionan() {
        Modelo uno = modelos.darDeAlta(new Modelos.AltaManual("AMI", null, "T22-UNO", null, null, null, null),
                despacho());
        Modelo otro = modelos.darDeAlta(new Modelos.AltaManual("AMI", null, "T22-OTRO", null, null, null, null),
                despacho());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> modelos.fusionar(otro.getId(), uno.getId(), despacho()));

        assertEquals("Los dos modelos tienen referencia: elige cuál es el prototipo", error.getMessage());
        assertTrue(repositorioModelos.existsById(uno.getId()));
        assertTrue(repositorioModelos.existsById(otro.getId()));
    }

    @Test
    void siSobreviveElPrototipoSeLlevaElHistorialDelTallerDelImportado() {
        Long temporada = temporada("T26-HISTORIAL-PROTO");
        Modelo proto = prototipo("PROTO T26 HISTORIAL", temporada);
        Modelo importado = importado("T26-HIST-PROTO.AL0001");
        Historial historial = historialEn(importado);

        Modelo superviviente = modelos.asignarReferencia(proto.getId(), "T26-HIST-PROTO", true, despacho());

        assertEquals(proto.getId(), superviviente.getId(), "sobrevive el de menor id, el prototipo");
        comprobarQueSeHaMovido(historial, proto.getId());
    }

    @Test
    void siSobreviveElImportadoConservaSuHistorialDelTaller() {
        Long temporada = temporada("T26-HISTORIAL-IMP");
        // El importado primero, para que tenga el id menor y sea él quien sobreviva.
        Modelo importado = importado("T26-HIST-IMP.AL0001");
        Modelo proto = prototipo("PROTO T26 HISTORIAL IMP", temporada);
        Historial historial = historialEn(proto);

        Modelo superviviente = modelos.asignarReferencia(proto.getId(), "T26-HIST-IMP", true, despacho());

        assertEquals(importado.getId(), superviviente.getId());
        comprobarQueSeHaMovido(historial, importado.getId());
    }

    /** Lo que el absorbido tenía de la app del taller, para buscarlo después por su id. */
    private record Historial(Long modificacionId, Long mensajeId, Long adjuntoId, Long cambioEtapaId) {
    }

    /** Un aviso del taller con su nota de voz y un cambio de etapa: lo que la V5 no deja que caiga con el modelo. */
    private Historial historialEn(Modelo modelo) {
        Modificacion aviso = modificaciones.crearAvisoDeTaller(new Modificaciones.AvisoTaller(modelo.getId(),
                "PIEL", List.of(ModificacionesTest.audio(30))), taller());
        Mensaje mensaje = mensajes.findByModificacionIdOrderByCreadoEnAscIdAsc(aviso.getId()).get(0);
        Long adjuntoId = adjuntosRepositorio.resumenes(List.of(mensaje.getId())).get(0).id();
        modelos.cambiarEtapa(modelo.getId(), Etapa.SMS, "T26 a muestras", despacho());
        Long cambioId = cambiosEtapa.findByModeloIdOrderByFechaDescIdDesc(modelo.getId()).stream()
                .filter(cambio -> "T26 a muestras".equals(cambio.getMotivo())).findFirst().orElseThrow().getId();
        return new Historial(aviso.getId(), mensaje.getId(), adjuntoId, cambioId);
    }

    private void comprobarQueSeHaMovido(Historial historial, Long supervivienteId) {
        assertEquals(supervivienteId,
                modificacionesRepositorio.findById(historial.modificacionId()).orElseThrow().getModeloId());
        assertEquals(historial.modificacionId(),
                mensajes.findById(historial.mensajeId()).orElseThrow().getModificacionId());
        assertTrue(adjuntosRepositorio.existsById(historial.adjuntoId()), "la nota de voz sigue ahí");
        assertTrue(cambiosEtapa.findByModeloIdOrderByFechaDescIdDesc(supervivienteId).stream()
                .anyMatch(cambio -> cambio.getId().equals(historial.cambioEtapaId())));
    }

    @Test
    void unaReferenciaQueEmpiezaPorAlmohadillaNoSeDaDeAlta() {
        Long temporada = temporada("T22-ALMOHADILLA");

        IllegalArgumentException codigo = assertThrows(IllegalArgumentException.class,
                () -> modelos.darDeAlta(new Modelos.AltaManual("AMI", null, " #12", null, null, null, null),
                        despacho()));
        assertEquals("La referencia no puede empezar por #", codigo.getMessage());

        IllegalArgumentException prototipo = assertThrows(IllegalArgumentException.class,
                () -> prototipo("#PROTO T22", temporada));
        assertEquals("La referencia no puede empezar por #", prototipo.getMessage());
    }

    private Modelo prototipo(String nombre, Long temporada) {
        return modelos.darDeAlta(new Modelos.AltaManual("AMI", temporada, null, nombre, null, null, null),
                despacho());
    }

    /** Un modelo como los que da de alta el pedido: con código, en producción y con su artículo. */
    private Modelo importado(String referencia) {
        Long modeloId = catalogo.asegurarArticulo("AMI", referencia).getModeloId();
        return repositorioModelos.findById(modeloId).orElseThrow();
    }

    private Long temporada(String nombre) {
        return temporadas.guardar(null, "AMI", nombre, (FicheroExcel) null, null).getId();
    }

    private UsuarioActual taller() {
        Usuario usuario = ComoUsuario.usuarioDePrueba(usuarios, Rol.TALLER);
        return new UsuarioActual(usuario.getId(), usuario.getNombre(), usuario.getRol(), null);
    }

    private UsuarioActual despacho() {
        Usuario usuario = ComoUsuario.usuarioDePrueba(usuarios, Rol.DESPACHO);
        return new UsuarioActual(usuario.getId(), usuario.getNombre(), usuario.getRol(), null);
    }
}
