package com.puntotres.packinglist.modelos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.modelos.api.dto.Evento;
import com.puntotres.packinglist.modelos.api.dto.ModeloFicha;
import com.puntotres.packinglist.modelos.api.dto.ModeloResumen;
import com.puntotres.packinglist.modelos.persistence.CambioEtapa;
import com.puntotres.packinglist.modelos.persistence.CambioEtapaRepository;
import com.puntotres.packinglist.modelos.persistence.ModeloCodigoHistorico;
import com.puntotres.packinglist.modelos.persistence.ModeloCodigoHistoricoRepository;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.persistence.UsuarioRepository;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.Etapa;
import com.puntotres.packinglist.persistence.FicheroExcel;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloRepository;
import com.puntotres.packinglist.persistence.TipoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;

/**
 * Alta, etapa, ficha y lista de los modelos. La base de datos es la misma
 * para toda la suite: los códigos de este test empiezan por {@code T9-}, los
 * prototipos por {@code PROTO T9}, las temporadas por {@code T9-} y los
 * logins por {@code t9-}.
 */
@SpringBootTest
class ModelosTest {

    @Autowired
    private Modelos modelos;

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private ArchivoTemporadas temporadas;

    @Autowired
    private CatalogoModelos catalogo;

    @Autowired
    private CambioEtapaRepository cambiosEtapa;

    @Autowired
    private ModeloCodigoHistoricoRepository codigosHistoricos;

    @Autowired
    private ModeloRepository repositorioModelos;

    @Autowired
    private FotosCatalogo fotos;

    @Autowired
    private ClientesProperties clientes;

    @Autowired
    private UsuarioRepository repositorioUsuarios;

    @Test
    void unPrototipoRepetidoEnLaMismaTemporadaDa400YEnOtraVale() {
        Long primera = temporada("T9-PROTO-1");
        Long segunda = temporada("T9-PROTO-2");

        Modelo proto = modelos.darDeAlta(new Modelos.AltaManual("ami", primera, null, " proto t9 ", null, null,
                null), despacho());

        assertEquals("AMI", proto.getCliente());
        assertNull(proto.getCodigo());
        assertEquals("PROTO T9", proto.getNombrePrototipo());
        assertEquals(Etapa.PROTOTIPO, proto.getEtapa());
        assertEquals(TipoArticulo.BOLSO, proto.getTipo());
        assertEquals(primera, proto.getTemporadaAltaId());

        IllegalArgumentException repetido = assertThrows(IllegalArgumentException.class,
                () -> modelos.darDeAlta(new Modelos.AltaManual("AMI", primera, null, "PROTO T9", null, null, null),
                        despacho()));
        assertEquals("Ya hay un PROTO T9 en esa temporada", repetido.getMessage());

        Modelo enOtra = modelos.darDeAlta(new Modelos.AltaManual("AMI", segunda, null, "PROTO T9", null, null,
                null), despacho());
        assertNotEquals(proto.getId(), enOtra.getId());

        ModeloFicha ficha = modelos.ficha(proto.getId());
        assertEquals(List.of("T9-PROTO-1"), ficha.temporadas());
        assertEquals(1, ficha.codigos().size());
        assertEquals("PROTO T9", ficha.codigos().get(0).codigo());
        assertEquals("ALTA", ficha.codigos().get(0).motivo());
        assertTrue(ficha.lineaTiempo().stream().anyMatch(evento -> "CODIGO".equals(evento.tipo())
                && "Prototipo PROTO T9".equals(evento.texto())), ficha.lineaTiempo().toString());
        assertTrue(ficha.lineaTiempo().stream().anyMatch(evento -> "ETAPA".equals(evento.tipo())
                && "Pasa a Prototipo".equals(evento.texto())
                && "T9 Despacho".equals(evento.quien())), ficha.lineaTiempo().toString());
        assertTrue(ficha.documentos().isEmpty());
    }

    @Test
    void unPrototipoSinTemporadaDa400() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> modelos.darDeAlta(new Modelos.AltaManual("AMI", null, null, "PROTO T9 SUELTO", null, null,
                        null), despacho()));
        assertEquals("Un prototipo necesita su temporada", error.getMessage());
    }

    @Test
    void altaConUnCodigoYaImportadoDa400() {
        catalogo.asegurarModelo("AMI", "T9-ULL770", "");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> modelos.darDeAlta(new Modelos.AltaManual("AMI", null, "t9-ull770", null, null, null, null),
                        despacho()));
        assertEquals("Ya existe T9-ULL770: asigna la referencia al prototipo o fusiona", error.getMessage());
    }

    @Test
    void sinClienteValidoOSinCodigoNiPrototipoDa400() {
        IllegalArgumentException sinCliente = assertThrows(IllegalArgumentException.class,
                () -> modelos.darDeAlta(new Modelos.AltaManual("NO-EXISTE-T9", null, "T9-X", null, null, null,
                        null), despacho()));
        assertTrue(sinCliente.getMessage().startsWith("No hay ningún cliente"), sinCliente.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> modelos.darDeAlta(new Modelos.AltaManual("AMI", null, " ", "", null, null, null), despacho()));
    }

    @Test
    void conCodigoNaceConElTipoDeSuReferenciaYApuntaSuCodigo() {
        Modelo cinturon = modelos.darDeAlta(new Modelos.AltaManual("AMI", null, "ubl-t9", null, "Cinturón",
                null, Etapa.SMS), despacho());

        assertEquals("UBL-T9", cinturon.getCodigo());
        assertEquals(TipoArticulo.CINTURON, cinturon.getTipo());
        assertEquals(Etapa.SMS, cinturon.getEtapa());
        ModeloFicha ficha = modelos.ficha(cinturon.getId());
        assertTrue(ficha.lineaTiempo().stream().anyMatch(evento -> "Referencia UBL-T9".equals(evento.texto())),
                ficha.lineaTiempo().toString());
        assertTrue(ficha.lineaTiempo().stream().anyMatch(evento -> "Pasa a Muestras".equals(evento.texto())),
                ficha.lineaTiempo().toString());
    }

    @Test
    void cambiarEtapaLaApuntaYSaleEnLaLineaDeTiempoConQuienLaCambio() {
        Modelo modelo = modelos.darDeAlta(new Modelos.AltaManual("AMI", null, "T9-ETAPA", null, null, null, null),
                despacho());

        modelos.cambiarEtapa(modelo.getId(), Etapa.PRODUCCION, "aprobado", despacho());

        List<CambioEtapa> apuntes = cambiosEtapa.findByModeloIdOrderByFechaDesc(modelo.getId());
        assertEquals(2, apuntes.size());
        assertEquals(Etapa.PROTOTIPO, apuntes.get(0).getDe());
        assertEquals(Etapa.PRODUCCION, apuntes.get(0).getA());
        assertEquals("aprobado", apuntes.get(0).getMotivo());
        assertNull(apuntes.get(1).getDe());

        ModeloFicha ficha = modelos.ficha(modelo.getId());
        assertEquals(Etapa.PRODUCCION, ficha.modelo().etapa());
        Evento ultimo = ficha.lineaTiempo().get(0);
        assertEquals("ETAPA", ultimo.tipo());
        assertEquals("Pasa a Producción · aprobado", ultimo.texto());
        assertEquals("T9 Despacho", ultimo.quien());
        for (int i = 1; i < ficha.lineaTiempo().size(); i++) {
            assertTrue(!ficha.lineaTiempo().get(i).fecha().isAfter(ficha.lineaTiempo().get(i - 1).fecha()),
                    "la línea de tiempo va de lo más nuevo a lo más viejo");
        }

        IllegalArgumentException misma = assertThrows(IllegalArgumentException.class,
                () -> modelos.cambiarEtapa(modelo.getId(), Etapa.PRODUCCION, null, despacho()));
        assertEquals("El modelo ya está en Producción", misma.getMessage());
    }

    @Test
    void unaEtapaSinUsuarioSaleConGuion() {
        Modelo modelo = modelos.darDeAlta(new Modelos.AltaManual("AMI", null, "T9-SINQUIEN", null, null, null,
                null), despacho());
        modelos.cambiarEtapa(modelo.getId(), Etapa.INACTIVO, " ", null);

        Evento ultimo = modelos.ficha(modelo.getId()).lineaTiempo().get(0);
        assertEquals("Pasa a Inactivo", ultimo.texto());
        assertEquals("—", ultimo.quien());
    }

    @Test
    void laFichaDeUnModeloQueNoExisteEsNoEncontrado() {
        assertThrows(NoSuchElementException.class, () -> modelos.ficha(987_654_321L));
        assertThrows(NoSuchElementException.class,
                () -> modelos.cambiarEtapa(987_654_321L, Etapa.SMS, null, despacho()));
    }

    @Test
    void listarFiltraPorEtapaYPorTextoSinMirarMayusculas() {
        Modelo muestra = modelos.darDeAlta(new Modelos.AltaManual("AMI", null, "T9-LISTA-A", null,
                "Bolso Lista Nueve", null, Etapa.SMS), despacho());
        Modelo produccion = modelos.darDeAlta(new Modelos.AltaManual("AMI", null, "T9-LISTA-B", null, null, null,
                Etapa.PRODUCCION), despacho());

        List<Long> muestras = ids(modelos.listar(new Modelos.Filtro("AMI", Etapa.SMS, null, "t9-lista", null)));
        assertTrue(muestras.contains(muestra.getId()));
        assertTrue(!muestras.contains(produccion.getId()));

        List<Long> porNombre = ids(modelos.listar(new Modelos.Filtro("ami", null, null, "lista nueve", null)));
        assertEquals(List.of(muestra.getId()), porNombre);

        List<ModeloResumen> todos = modelos.listar(new Modelos.Filtro(null, null, null, "T9-LISTA", null));
        assertEquals(List.of("T9-LISTA-A", "T9-LISTA-B"), todos.stream().map(ModeloResumen::referencia).toList());
        assertEquals("Bolso Lista Nueve", todos.get(0).nombre());
        assertEquals(Etapa.SMS, todos.get(0).etapa());
        assertNull(todos.get(0).fotoPrincipalId());
        assertNotNull(todos.get(0).fechaEtapa());
    }

    @Test
    void listarFiltraPorTemporadaYPorEtiquetaSinImprimir() {
        Long temporada = temporada("T9-TEMP");
        Modelo impreso = modelos.darDeAlta(new Modelos.AltaManual("AMI", temporada, "T9-TEMP-A", null, null,
                null, null), despacho());
        Modelo sinImprimir = modelos.darDeAlta(new Modelos.AltaManual("AMI", temporada, null, "PROTO T9 TEMP",
                null, null, null), despacho());
        modelos.marcarEtiquetaImpresa(List.of(impreso.getId()));

        List<Long> deLaTemporada = ids(modelos.listar(new Modelos.Filtro(null, null, temporada, null, null)));
        assertEquals(List.of(sinImprimir.getId(), impreso.getId()), deLaTemporada,
                "por referencia visible: PROTO T9 TEMP antes que T9-TEMP-A");

        List<ModeloResumen> pendientes = modelos.listar(new Modelos.Filtro("AMI", null, temporada, null, true));
        assertEquals(List.of(sinImprimir.getId()), ids(pendientes));
        assertNull(pendientes.get(0).etiquetaImpresaEn());

        ModeloResumen marcado = modelos.ficha(impreso.getId()).modelo();
        assertTrue(marcado.etiquetaImpresaEn() != null
                && !marcado.etiquetaImpresaEn().isAfter(LocalDateTime.now()));
    }

    @Test
    void editarConNulosOEnBlancoNoToca() {
        Modelo modelo = modelos.darDeAlta(new Modelos.AltaManual("AMI", null, "T9-EDITAR", null, "Nombre Viejo",
                null, null), despacho());

        modelos.editar(modelo.getId(), null, TipoArticulo.CINTURON);
        modelos.editar(modelo.getId(), "  ", null);
        ModeloResumen editado = modelos.ficha(modelo.getId()).modelo();
        assertEquals("Nombre Viejo", editado.nombre());
        assertEquals(TipoArticulo.CINTURON, editado.tipo());

        modelos.editar(modelo.getId(), " Nombre Nuevo ", null);
        assertEquals("Nombre Nuevo", modelos.ficha(modelo.getId()).modelo().nombre());
    }

    @Test
    void resolverIdDaElMismoOElSupervivienteOVacio() {
        Modelo modelo = modelos.darDeAlta(new Modelos.AltaManual("AMI", null, "T9-RESOLVER", null, null, null,
                null), despacho());
        codigosHistoricos.save(new ModeloCodigoHistorico(modelo.getId(), "#987654320", LocalDateTime.now(),
                "FUSION"));

        assertEquals(Optional.of(modelo.getId()), modelos.resolverId(modelo.getId()));
        assertEquals(Optional.of(modelo.getId()), modelos.resolverId(987_654_320L));
        assertEquals(Optional.empty(), modelos.resolverId(987_654_329L));
    }

    @Test
    void losEventosDeOtrasFuentesSeMezclanPorFechaEnLaLineaDeTiempo() {
        Modelo modelo = modelos.darDeAlta(new Modelos.AltaManual("AMI", null, "T9-FUENTES", null, null, null,
                null), despacho());
        Evento futuro = new Evento(LocalDateTime.now().plusDays(1), "MODIFICACION", "Cambio de piel", "Taller",
                7L, null);
        Evento antiguo = new Evento(LocalDateTime.now().minusYears(1), "MODIFICACION", "Forro", "Taller", 8L, 9L);
        Modelos.FuenteEventos fuente = modeloId -> modeloId.equals(modelo.getId()) ? List.of(antiguo, futuro)
                : List.of();
        Modelos conFuente = new Modelos(repositorioModelos, catalogo, fotos, temporadas, clientes, cambiosEtapa,
                codigosHistoricos, repositorioUsuarios, List.of(fuente));

        List<Evento> linea = conFuente.ficha(modelo.getId()).lineaTiempo();
        assertEquals(4, linea.size());
        assertEquals(futuro, linea.get(0));
        assertEquals(antiguo, linea.get(3));
    }

    private Long temporada(String nombre) {
        return temporadas.guardar(null, "AMI", nombre, (FicheroExcel) null, null).getId();
    }

    private UsuarioActual despacho() {
        Usuario usuario = usuarios.todos().stream()
                .filter(existente -> "t9-despacho".equalsIgnoreCase(existente.getLogin()))
                .findFirst()
                .orElseGet(() -> usuarios.crear(new Usuarios.Alta("T9 Despacho", "t9-despacho", "t9", Rol.DESPACHO)));
        return new UsuarioActual(usuario.getId(), usuario.getNombre(), usuario.getRol(), null);
    }

    private static List<Long> ids(List<ModeloResumen> resumenes) {
        return resumenes.stream().map(ModeloResumen::id).toList();
    }
}
