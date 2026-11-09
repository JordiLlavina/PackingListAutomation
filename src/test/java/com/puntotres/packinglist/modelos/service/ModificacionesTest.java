package com.puntotres.packinglist.modelos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

import com.puntotres.packinglist.modelos.ComoUsuario;
import com.puntotres.packinglist.modelos.api.dto.AdjuntoVista;
import com.puntotres.packinglist.modelos.api.dto.DestinoEstado;
import com.puntotres.packinglist.modelos.api.dto.Evento;
import com.puntotres.packinglist.modelos.api.dto.MensajeVista;
import com.puntotres.packinglist.modelos.api.dto.ModificacionDetalle;
import com.puntotres.packinglist.modelos.api.dto.ModificacionResumen;
import com.puntotres.packinglist.modelos.api.dto.ResumenBandeja;
import com.puntotres.packinglist.modelos.persistence.DestinoAplicacion;
import com.puntotres.packinglist.modelos.persistence.Direccion;
import com.puntotres.packinglist.modelos.persistence.EstadoModificacion;
import com.puntotres.packinglist.modelos.persistence.EstadoTranscripcion;
import com.puntotres.packinglist.modelos.persistence.Mensaje;
import com.puntotres.packinglist.modelos.persistence.Modificacion;
import com.puntotres.packinglist.modelos.persistence.ModificacionRepository;
import com.puntotres.packinglist.modelos.persistence.Prioridad;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.TipoAdjunto;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloRepository;
import com.puntotres.packinglist.persistence.TipoArticulo;

/**
 * El ciclo de una modificación en los dos sentidos: avisos del taller,
 * órdenes del despacho, destinos, respuestas, bandeja y búsqueda. La base de
 * datos es la misma para toda la suite: los modelos de este test se llaman
 * {@code T11-…} y los recuentos de la bandeja se miran como diferencias.
 */
@SpringBootTest
class ModificacionesTest {

    @Autowired
    private Modificaciones modificaciones;

    @Autowired
    private Adjuntos adjuntos;

    @Autowired
    private Modelos modelos;

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private ModeloRepository repositorioModelos;

    @Autowired
    private ModificacionRepository repositorio;

    @Test
    void unAvisoSinFotoNiAudioSeRechaza() {
        Long modeloId = modelo("SINFOTO");

        IllegalArgumentException sinNada = assertThrows(IllegalArgumentException.class,
                () -> modificaciones.crearAvisoDeTaller(new Modificaciones.AvisoTaller(modeloId, "PIEL", List.of()),
                        taller()));
        assertEquals("Hace falta una foto o una nota de voz", sinNada.getMessage());

        AdjuntoNuevo documento = new AdjuntoNuevo(TipoAdjunto.DOCUMENTO, "ficha.pdf", "application/pdf",
                new byte[] {1, 2, 3}, null);
        IllegalArgumentException soloDocumento = assertThrows(IllegalArgumentException.class,
                () -> modificaciones.crearAvisoDeTaller(new Modificaciones.AvisoTaller(modeloId, "PIEL",
                        List.of(documento)), taller()));
        assertEquals("Hace falta una foto o una nota de voz", soloDocumento.getMessage());

        IllegalArgumentException tipoQueNoExiste = assertThrows(IllegalArgumentException.class,
                () -> modificaciones.crearAvisoDeTaller(new Modificaciones.AvisoTaller(modeloId, "NOPE",
                        List.of(foto())), taller()));
        assertEquals("No existe el tipo de cambio NOPE", tipoQueNoExiste.getMessage());

        assertTrue(repositorio.findAll().stream().noneMatch(m -> m.getModeloId().equals(modeloId)),
                "un aviso rechazado no deja nada guardado");
    }

    @Test
    void unAvisoConAudioNaceNuevoSinPrioridadYConLosDosDestinos() {
        Long modeloId = modelo("AUDIO");

        Modificacion aviso = modificaciones.crearAvisoDeTaller(new Modificaciones.AvisoTaller(modeloId, "PIEL",
                List.of(audio(30))), taller());

        ModificacionDetalle detalle = modificaciones.detalle(aviso.getId());
        ModificacionResumen resumen = detalle.resumen();
        assertEquals(EstadoModificacion.NUEVA, resumen.estado());
        assertNull(resumen.prioridad());
        assertEquals(Direccion.TALLER_A_DESPACHO, resumen.direccion());
        assertEquals("PIEL", resumen.tipoCambio());
        assertEquals("Piel / material", resumen.tipoCambioTexto());
        assertEquals("T11-AUDIO", resumen.modeloReferencia());
        assertEquals("Bolso T11", resumen.modeloNombre());
        assertEquals("AMI", resumen.cliente());
        assertEquals("Prueba Taller", resumen.creadaPor());
        assertNotNull(resumen.creadaEn());
        assertNull(resumen.vistaEn());
        assertNull(resumen.respuestaPendienteDe());
        assertFalse(resumen.vencida());
        assertEquals(0, resumen.fotos());
        assertEquals(1, resumen.audios());
        assertEquals(30, resumen.duracionAudioSeg(), "cuánto dura la nota de voz del primer mensaje");
        assertEquals(0, resumen.documentos());
        assertEquals(List.of(DestinoAplicacion.ICSUITE, DestinoAplicacion.EXCEL_ESCANDALLOS),
                resumen.destinos().stream().map(DestinoEstado::destino).toList());
        assertEquals(List.of("ICSUITE", "Excel de escandallos"),
                resumen.destinos().stream().map(DestinoEstado::texto).toList());
        assertTrue(resumen.destinos().stream().allMatch(destino -> destino.aplica() && destino.hechoEn() == null));

        assertEquals(1, detalle.mensajes().size());
        MensajeVista primero = detalle.mensajes().get(0);
        assertEquals("", primero.texto());
        assertEquals("Prueba Taller", primero.autor());
        assertEquals(Rol.TALLER, primero.rolAutor());
        assertEquals(1, primero.adjuntos().size());
        AdjuntoVista nota = primero.adjuntos().get(0);
        assertEquals(TipoAdjunto.AUDIO, nota.tipo());
        assertEquals(30, nota.duracionSeg());
        // Nace PENDIENTE y la transcripción corre aparte, en su hilo (TranscripcionService): en los tests no hay
        // Azure, así que según llegue ese hilo ya puede estar en NO_APLICA. Lo que no puede es quedarse sin estado.
        assertTrue(Set.of(EstadoTranscripcion.PENDIENTE, EstadoTranscripcion.NO_APLICA)
                .contains(nota.estadoTranscripcion()), String.valueOf(nota.estadoTranscripcion()));

        Evento evento = modelos.ficha(modeloId).lineaTiempo().stream()
                .filter(e -> "MODIFICACION".equals(e.tipo()))
                .findFirst()
                .orElseThrow();
        assertEquals("Aviso: Piel / material", evento.texto());
        assertEquals("Prueba Taller (taller)", evento.quien());
        assertEquals(aviso.getId(), evento.modificacionId());
        assertEquals(nota.id(), evento.audioAdjuntoId());
    }

    @Test
    void marcarLosDosDestinosLoPasaAHechaSolo() {
        Modificacion aviso = avisoConFoto("DESTINOS");

        modificaciones.marcarVista(aviso.getId(), taller());
        assertEquals(EstadoModificacion.NUEVA, estado(aviso), "el taller no es quien recibe sus avisos");
        modificaciones.marcarVista(aviso.getId(), despacho());
        assertEquals(EstadoModificacion.VISTA, estado(aviso));
        assertNotNull(modificaciones.detalle(aviso.getId()).resumen().vistaEn());

        assertThrows(AccessDeniedException.class, () -> modificaciones.marcarDestino(aviso.getId(),
                DestinoAplicacion.ICSUITE, true, true, taller()));

        modificaciones.marcarDestino(aviso.getId(), DestinoAplicacion.ICSUITE, true, true, despacho());
        ModificacionResumen enCurso = modificaciones.detalle(aviso.getId()).resumen();
        assertEquals(EstadoModificacion.EN_CURSO, enCurso.estado());
        DestinoEstado icsuite = enCurso.destinos().get(0);
        assertNotNull(icsuite.hechoEn());
        assertEquals("Prueba Despacho", icsuite.hechoPor());
        assertNull(enCurso.hechaEn());

        modificaciones.marcarDestino(aviso.getId(), DestinoAplicacion.EXCEL_ESCANDALLOS, true, true, despacho());
        ModificacionResumen hecha = modificaciones.detalle(aviso.getId()).resumen();
        assertEquals(EstadoModificacion.HECHA, hecha.estado());
        assertEquals("Prueba Despacho", hecha.hechaPor());
        assertNotNull(hecha.hechaEn());

        // Desmarcar un destino de una hecha la devuelve a en curso.
        modificaciones.marcarDestino(aviso.getId(), DestinoAplicacion.EXCEL_ESCANDALLOS, true, false, despacho());
        ModificacionResumen reabierta = modificaciones.detalle(aviso.getId()).resumen();
        assertEquals(EstadoModificacion.EN_CURSO, reabierta.estado());
        assertNull(reabierta.hechaPor());
        assertNull(reabierta.hechaEn());
        assertNull(reabierta.destinos().get(1).hechoEn());
    }

    @Test
    void unDestinoQueNoAplicaNoHaceFalta() {
        Modificacion aviso = avisoConFoto("NOAPLICA");

        modificaciones.marcarDestino(aviso.getId(), DestinoAplicacion.ICSUITE, false, false, despacho());
        assertEquals(EstadoModificacion.NUEVA, estado(aviso), "decir dónde no aplica no es hacer nada");
        assertFalse(modificaciones.detalle(aviso.getId()).resumen().destinos().get(0).aplica());

        modificaciones.marcarDestino(aviso.getId(), DestinoAplicacion.EXCEL_ESCANDALLOS, true, true, despacho());
        assertEquals(EstadoModificacion.HECHA, estado(aviso));

        Modificacion otro = avisoConFoto("NINGUNO");
        modificaciones.marcarDestino(otro.getId(), DestinoAplicacion.ICSUITE, false, false, despacho());
        modificaciones.marcarDestino(otro.getId(), DestinoAplicacion.EXCEL_ESCANDALLOS, false, false, despacho());
        assertEquals(EstadoModificacion.HECHA, estado(otro), "si no aplica en ningún sitio no queda nada que hacer");
    }

    @Test
    void unaOrdenPasaAVistaAlAbrirlaElTallerYAHechaConSuUsuario() {
        Long modeloId = modelo("ORDEN");

        IllegalArgumentException sinTitulo = assertThrows(IllegalArgumentException.class,
                () -> modificaciones.crearOrdenParaTaller(new Modificaciones.OrdenDespacho(modeloId, null, " ",
                        "texto", Prioridad.ALTA, List.of()), despacho()));
        assertEquals("Falta el título", sinTitulo.getMessage());
        IllegalArgumentException sinPrioridad = assertThrows(IllegalArgumentException.class,
                () -> modificaciones.crearOrdenParaTaller(new Modificaciones.OrdenDespacho(modeloId, null,
                        "Cambiar asa", "texto", null, List.of()), despacho()));
        assertEquals("Falta la prioridad", sinPrioridad.getMessage());

        Modificacion orden = modificaciones.crearOrdenParaTaller(new Modificaciones.OrdenDespacho(modeloId,
                "HERRAJES", "Cambiar asa", "Asa de 2 cm", Prioridad.ALTA, List.of()), despacho());
        ModificacionDetalle detalle = modificaciones.detalle(orden.getId());
        assertEquals(EstadoModificacion.NUEVA, detalle.resumen().estado());
        assertEquals(Direccion.DESPACHO_A_TALLER, detalle.resumen().direccion());
        assertEquals(Prioridad.ALTA, detalle.resumen().prioridad());
        assertEquals("Cambiar asa", detalle.resumen().titulo());
        assertTrue(detalle.resumen().destinos().isEmpty(), "una orden no tiene destinos");
        assertNull(detalle.resumen().duracionAudioSeg(), "sin nota de voz no hay duración");
        assertEquals("Asa de 2 cm", detalle.mensajes().get(0).texto());
        assertThrows(IllegalArgumentException.class, () -> modificaciones.marcarDestino(orden.getId(),
                DestinoAplicacion.ICSUITE, true, true, despacho()));

        modificaciones.marcarVista(orden.getId(), despacho());
        assertEquals(EstadoModificacion.NUEVA, estado(orden), "quien la manda no la ve por abrirla");
        modificaciones.marcarVista(orden.getId(), taller());
        assertEquals(EstadoModificacion.VISTA, estado(orden));

        modificaciones.marcarHecha(orden.getId(), taller());
        ModificacionResumen hecha = modificaciones.detalle(orden.getId()).resumen();
        assertEquals(EstadoModificacion.HECHA, hecha.estado());
        assertEquals("Prueba Taller", hecha.hechaPor());
        assertNotNull(hecha.hechaEn());

        Evento evento = modelos.ficha(modeloId).lineaTiempo().stream()
                .filter(e -> "MODIFICACION".equals(e.tipo()))
                .findFirst()
                .orElseThrow();
        assertEquals("Orden: Cambiar asa", evento.texto());
        assertEquals("Prueba Despacho (despacho)", evento.quien());
        assertNull(evento.audioAdjuntoId());
    }

    @Test
    void unaRespuestaEnciendeNecesitaRespuestaDelOtroLado() {
        Modificacion aviso = avisoConFoto("RESPUESTA");
        assertNull(modificaciones.ladoQueDebeResponder(recargar(aviso)));
        int antes = modificaciones.resumen(taller()).conRespuesta();

        IllegalArgumentException vacia = assertThrows(IllegalArgumentException.class,
                () -> modificaciones.responder(aviso.getId(), "  ", List.of(), despacho()));
        assertEquals("Escribe algo o adjunta una nota de voz o una foto", vacia.getMessage());

        Mensaje respuesta = modificaciones.responder(aviso.getId(), "¿Qué piel?", List.of(), despacho());
        assertNotNull(respuesta.getId());
        Modificacion recargada = recargar(aviso);
        assertTrue(recargada.isNecesitaRespuesta());
        assertEquals(Rol.TALLER, modificaciones.ladoQueDebeResponder(recargada));
        assertEquals(Rol.TALLER, modificaciones.detalle(aviso.getId()).resumen().respuestaPendienteDe());
        assertEquals(antes + 1, modificaciones.resumen(taller()).conRespuesta());

        modificaciones.responder(aviso.getId(), null, List.of(audio(12)), taller());
        assertEquals(Rol.DESPACHO, modificaciones.ladoQueDebeResponder(recargar(aviso)));
        assertEquals(antes, modificaciones.resumen(taller()).conRespuesta());
        List<MensajeVista> mensajes = modificaciones.detalle(aviso.getId()).mensajes();
        assertEquals(3, mensajes.size());
        assertEquals("¿Qué piel?", mensajes.get(1).texto());
        assertEquals(Rol.DESPACHO, mensajes.get(1).rolAutor());
        assertEquals(TipoAdjunto.AUDIO, mensajes.get(2).adjuntos().get(0).tipo());
    }

    @Test
    void unaDescartadaNoSeMarcaHechaNiCambiaDePrioridadPeroAdmiteMensajes() {
        Modificacion aviso = avisoConFoto("DESCARTE");

        assertThrows(AccessDeniedException.class, () -> modificaciones.descartar(aviso.getId(), "no", taller()));
        IllegalArgumentException sinMotivo = assertThrows(IllegalArgumentException.class,
                () -> modificaciones.descartar(aviso.getId(), " ", despacho()));
        assertEquals("Hace falta el motivo", sinMotivo.getMessage());

        modificaciones.descartar(aviso.getId(), "Duplicado del de ayer", despacho());
        ModificacionDetalle descartada = modificaciones.detalle(aviso.getId());
        assertEquals(EstadoModificacion.DESCARTADA, descartada.resumen().estado());
        assertEquals("Duplicado del de ayer", descartada.motivoDescarte());

        IllegalArgumentException prioridad = assertThrows(IllegalArgumentException.class,
                () -> modificaciones.ponerPrioridad(aviso.getId(), Prioridad.ALTA, despacho()));
        assertEquals("La modificación está descartada", prioridad.getMessage());
        IllegalArgumentException hecha = assertThrows(IllegalArgumentException.class,
                () -> modificaciones.marcarHecha(aviso.getId(), despacho()));
        assertEquals("La modificación está descartada", hecha.getMessage());
        IllegalArgumentException destino = assertThrows(IllegalArgumentException.class,
                () -> modificaciones.marcarDestino(aviso.getId(), DestinoAplicacion.ICSUITE, true, true,
                        despacho()));
        assertEquals("La modificación está descartada", destino.getMessage());

        modificaciones.responder(aviso.getId(), "Era el mismo cambio que el de ayer", List.of(), despacho());
        ModificacionDetalle conRespuesta = modificaciones.detalle(aviso.getId());
        assertEquals(EstadoModificacion.DESCARTADA, conRespuesta.resumen().estado());
        assertNull(conRespuesta.resumen().prioridad());
        assertEquals(2, conRespuesta.mensajes().size());

        // Y una hecha tampoco cambia de prioridad.
        Modificacion otra = avisoConFoto("HECHA");
        modificaciones.marcarHecha(otra.getId(), despacho());
        assertTrue(modificaciones.detalle(otra.getId()).resumen().destinos().stream()
                .allMatch(d -> d.hechoEn() != null), "el atajo marca hechos todos los destinos que aplican");
        IllegalArgumentException yaHecha = assertThrows(IllegalArgumentException.class,
                () -> modificaciones.ponerPrioridad(otra.getId(), Prioridad.BAJA, despacho()));
        assertEquals("La modificación ya está hecha", yaHecha.getMessage());
        assertThrows(AccessDeniedException.class, () -> modificaciones.marcarHecha(avisoConFoto("HECHA-T").getId(),
                taller()));
    }

    @Test
    void unaCerradaNoSeVuelveADescartar() {
        Modificacion descartada = avisoConFoto("REDESCARTE");
        modificaciones.descartar(descartada.getId(), "Duplicado", despacho());
        IllegalArgumentException otraVez = assertThrows(IllegalArgumentException.class,
                () -> modificaciones.descartar(descartada.getId(), "Otro motivo", despacho()));
        assertEquals("La modificación ya está descartada", otraVez.getMessage());
        assertEquals("Duplicado", modificaciones.detalle(descartada.getId()).motivoDescarte());

        Modificacion hecha = avisoConFoto("REDESCARTE-H");
        modificaciones.marcarHecha(hecha.getId(), despacho());
        IllegalArgumentException yaHecha = assertThrows(IllegalArgumentException.class,
                () -> modificaciones.descartar(hecha.getId(), "Tarde", despacho()));
        assertEquals("La modificación ya está hecha", yaHecha.getMessage());
        assertEquals(EstadoModificacion.HECHA, estado(hecha));
    }

    @Test
    void unaCerradaNoEsperaRespuestaAunqueLaPidiera() {
        int tallerAntes = modificaciones.resumen(taller()).conRespuesta();
        Modificacion aviso = avisoConFoto("CERRADA-R");
        assertEquals(Rol.TALLER, modificaciones.detalle(aviso.getId()).resumen().ultimoMensajeDe(),
                "el cuerpo del aviso es el primer mensaje, del taller");
        modificaciones.responder(aviso.getId(), "¿Qué piel?", List.of(), despacho());
        assertEquals(Rol.TALLER, modificaciones.detalle(aviso.getId()).resumen().respuestaPendienteDe());
        assertEquals(Rol.DESPACHO, modificaciones.detalle(aviso.getId()).resumen().ultimoMensajeDe());
        assertEquals(tallerAntes + 1, modificaciones.resumen(taller()).conRespuesta());

        modificaciones.descartar(aviso.getId(), "Ya no hace falta", despacho());
        assertTrue(recargar(aviso).isNecesitaRespuesta(), "descartar no apaga la marca");
        assertNull(modificaciones.detalle(aviso.getId()).resumen().respuestaPendienteDe());
        assertEquals(Rol.DESPACHO, modificaciones.detalle(aviso.getId()).resumen().ultimoMensajeDe(),
                "en una cerrada se sigue sabiendo quién habló el último: el taller enseña la respuesta");
        assertEquals(tallerAntes, modificaciones.resumen(taller()).conRespuesta());

        int despachoAntes = modificaciones.resumen(despacho()).conRespuesta();
        Modificacion orden = modificaciones.crearOrdenParaTaller(new Modificaciones.OrdenDespacho(
                modelo("CERRADA-O"), null, "Cambiar asa", null, Prioridad.NORMAL, List.of()), despacho());
        modificaciones.responder(orden.getId(), "¿Qué asa?", List.of(), taller());
        assertEquals(Rol.DESPACHO, modificaciones.detalle(orden.getId()).resumen().respuestaPendienteDe());
        assertEquals(despachoAntes + 1, modificaciones.resumen(despacho()).conRespuesta());

        modificaciones.marcarHecha(orden.getId(), taller());
        assertNull(modificaciones.detalle(orden.getId()).resumen().respuestaPendienteDe());
        assertTrue(modificaciones.listar(new Modificaciones.Filtro(null, null, null, recargar(orden).getModeloId(),
                null, null), despacho()).stream().allMatch(r -> r.respuestaPendienteDe() == null));
        assertEquals(despachoAntes, modificaciones.resumen(despacho()).conRespuesta());
    }

    @Test
    void elResumenDeDespachoCuentaNuevasYSinPrioridad() {
        ResumenBandeja antes = modificaciones.resumen(despacho());
        ResumenBandeja tallerAntes = modificaciones.resumen(taller());

        Modificacion primero = avisoConFoto("BANDEJA-1");
        avisoConFoto("BANDEJA-2");
        modificaciones.ponerPrioridad(primero.getId(), Prioridad.NORMAL, despacho());
        Modificacion orden = modificaciones.crearOrdenParaTaller(new Modificaciones.OrdenDespacho(
                modelo("BANDEJA-O"), null, "Revisar costura", null, Prioridad.BAJA, List.of()), despacho());

        ResumenBandeja despues = modificaciones.resumen(despacho());
        assertEquals(antes.nuevas() + 2, despues.nuevas());
        assertEquals(antes.sinPrioridad() + 1, despues.sinPrioridad());
        assertEquals(antes.pendientes() + 2, despues.pendientes());
        assertEquals(0, despues.vencidas());

        ResumenBandeja tallerDespues = modificaciones.resumen(taller());
        assertEquals(tallerAntes.nuevas() + 1, tallerDespues.nuevas());
        assertEquals(tallerAntes.pendientes() + 1, tallerDespues.pendientes());

        modificaciones.marcarVista(orden.getId(), taller());
        assertEquals(tallerAntes.nuevas(), modificaciones.resumen(taller()).nuevas());
        assertEquals(tallerAntes.pendientes() + 1, modificaciones.resumen(taller()).pendientes());

        modificaciones.responder(orden.getId(), "¿Qué costura?", List.of(), taller());
        assertEquals(antes.conRespuesta() + 1, modificaciones.resumen(despacho()).conRespuesta());
    }

    @Test
    void laBusquedaPorTextoEncuentraLaTranscripcion() {
        Long modeloId = modelo("BUSCA");
        Modificacion conVoz = modificaciones.crearAvisoDeTaller(new Modificaciones.AvisoTaller(modeloId, "FORRO",
                List.of(audio(20))), taller());
        Modificacion conResumen = modificaciones.crearAvisoDeTaller(new Modificaciones.AvisoTaller(modeloId,
                "FORRO", List.of(audio(20))), taller());
        Modificacion otra = avisoConFoto("BUSCA-OTRO");
        Long notaId = modificaciones.detalle(conVoz.getId()).mensajes().get(0).adjuntos().get(0).id();
        Long notaResumenId = modificaciones.detalle(conResumen.getId()).mensajes().get(0).adjuntos().get(0).id();

        adjuntos.marcarTranscripcion(notaId, EstadoTranscripcion.HECHA, "Poner el forro de ante t11zorro", null);
        adjuntos.marcarResumen(notaResumenId, "Forro de ante T11LINCE");

        assertEquals(List.of(conVoz.getId()), ids(modificaciones.listar(filtroTexto("T11ZORRO"), despacho())));
        assertEquals(List.of(conResumen.getId()), ids(modificaciones.listar(filtroTexto("t11lince"), despacho())));

        modificaciones.responder(otra.getId(), "Mira el T11GATO de la esquina", List.of(), despacho());
        assertEquals(List.of(otra.getId()), ids(modificaciones.listar(filtroTexto("t11gato"), despacho())));

        Modificacion orden = modificaciones.crearOrdenParaTaller(new Modificaciones.OrdenDespacho(modeloId, null,
                "Cambiar T11TOPO", null, Prioridad.NORMAL, List.of()), despacho());
        assertEquals(List.of(orden.getId()), ids(modificaciones.listar(new Modificaciones.Filtro(null, null, null,
                null, null, "t11topo"), taller())));
        assertTrue(modificaciones.listar(new Modificaciones.Filtro(Direccion.TALLER_A_DESPACHO, null, null, null,
                null, "t11topo"), taller()).isEmpty(), "la dirección también filtra");
    }

    @Test
    void laListaVaNuevasPrimeroLuegoSinPrioridadYPorPrioridadYCerradasAlFinal() {
        Long modeloId = modelo("ORDENLISTA");
        Modificacion vistaBaja = aviso(modeloId);
        Modificacion vistaAlta = aviso(modeloId);
        Modificacion vistaSinPrioridad = aviso(modeloId);
        Modificacion hecha = aviso(modeloId);
        Modificacion nueva = aviso(modeloId);
        modificaciones.ponerPrioridad(vistaBaja.getId(), Prioridad.BAJA, despacho());
        modificaciones.ponerPrioridad(vistaAlta.getId(), Prioridad.ALTA, despacho());
        for (Modificacion m : List.of(vistaBaja, vistaAlta, vistaSinPrioridad, hecha)) {
            modificaciones.marcarVista(m.getId(), despacho());
        }
        modificaciones.marcarHecha(hecha.getId(), despacho());

        List<Long> orden = ids(modificaciones.listar(new Modificaciones.Filtro(null, null, null, modeloId, null,
                null), despacho()));
        assertEquals(List.of(nueva.getId(), vistaSinPrioridad.getId(), vistaAlta.getId(), vistaBaja.getId(),
                hecha.getId()), orden);

        List<Long> soloAbiertas = ids(modificaciones.listar(new Modificaciones.Filtro(Direccion.TALLER_A_DESPACHO,
                Set.of(EstadoModificacion.VISTA), null, modeloId, null, null), despacho()));
        assertEquals(List.of(vistaSinPrioridad.getId(), vistaAlta.getId(), vistaBaja.getId()), soloAbiertas);

        List<Long> alta = ids(modificaciones.listar(new Modificaciones.Filtro(null, null, Prioridad.ALTA, modeloId,
                null, null), despacho()));
        assertEquals(List.of(vistaAlta.getId()), alta);
        assertTrue(modificaciones.listar(new Modificaciones.Filtro(null, null, null, modeloId, "APC", null),
                despacho()).isEmpty(), "el modelo es de AMI");
    }

    private Modificacion avisoConFoto(String codigo) {
        return aviso(modelo(codigo));
    }

    private Modificacion aviso(Long modeloId) {
        return modificaciones.crearAvisoDeTaller(new Modificaciones.AvisoTaller(modeloId, "PIEL", List.of(foto())),
                taller());
    }

    private EstadoModificacion estado(Modificacion modificacion) {
        return recargar(modificacion).getEstado();
    }

    private Modificacion recargar(Modificacion modificacion) {
        return repositorio.findById(modificacion.getId()).orElseThrow();
    }

    private Long modelo(String codigo) {
        return repositorioModelos.save(new Modelo("AMI", "T11-" + codigo, "Bolso T11", TipoArticulo.BOLSO))
                .getId();
    }

    private UsuarioActual taller() {
        return actual(ComoUsuario.usuarioDePrueba(usuarios, Rol.TALLER));
    }

    private UsuarioActual despacho() {
        return actual(ComoUsuario.usuarioDePrueba(usuarios, Rol.DESPACHO));
    }

    private static UsuarioActual actual(Usuario usuario) {
        return new UsuarioActual(usuario.getId(), usuario.getNombre(), usuario.getRol(), null);
    }

    private static Modificaciones.Filtro filtroTexto(String texto) {
        return new Modificaciones.Filtro(Direccion.TALLER_A_DESPACHO, null, null, null, null, texto);
    }

    private static List<Long> ids(List<ModificacionResumen> resumenes) {
        return resumenes.stream().map(ModificacionResumen::id).toList();
    }

    static AdjuntoNuevo foto() {
        return new AdjuntoNuevo(TipoAdjunto.FOTO, "foto.png", "image/png", png(10, 10), null);
    }

    static AdjuntoNuevo audio(int segundos) {
        return new AdjuntoNuevo(TipoAdjunto.AUDIO, "nota.webm", "audio/webm", new byte[] {26, 69, -33, -93},
                segundos);
    }

    static byte[] png(int ancho, int alto) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB), "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
