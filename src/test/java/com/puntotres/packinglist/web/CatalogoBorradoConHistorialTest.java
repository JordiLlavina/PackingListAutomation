package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.modelos.ComoUsuario;
import com.puntotres.packinglist.modelos.persistence.Modificacion;
import com.puntotres.packinglist.modelos.persistence.ModificacionRepository;
import com.puntotres.packinglist.modelos.persistence.Prioridad;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.modelos.service.Modificaciones;
import com.puntotres.packinglist.modelos.service.Usuarios;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.ModeloRepository;
import com.puntotres.packinglist.persistence.TemporadaGuardada;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;

/**
 * Los botones de borrar de la pantalla vieja {@code /catalogo} no saben nada
 * de la app del taller: un modelo con modificaciones o documentos no se borra
 * desde ahí (solo se desvincula de la temporada) y su historial se conserva.
 * Cada test usa referencias propias (ULL97x).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogoBorradoConHistorialTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CatalogoModelos catalogo;

    @Autowired
    private ArchivoTemporadas archivo;

    @Autowired
    private Modificaciones modificaciones;

    @Autowired
    private ModificacionRepository repositorioModificaciones;

    @Autowired
    private ModeloRepository repositorioModelos;

    @Autowired
    private Usuarios usuarios;

    @Test
    void unModeloConUnaModificacionNoSeBorraYConservaSuHistorial() throws Exception {
        catalogo.asegurarArticulo("AMI", "ULL971.AL0001");
        Long modeloId = catalogo.modelo("AMI", "ULL971").orElseThrow().getId();
        Modificacion orden = orden(modeloId);

        mvc.perform(post("/catalogo/modelo/borrar").param("cliente", "AMI").param("codigo", "ULL971"))
                .andExpect(redirectedUrl("/catalogo?cliente=AMI"))
                .andExpect(flash().attribute("mensajeError", containsString("no se puede borrar")));

        assertTrue(catalogo.modelo("AMI", "ULL971").isPresent());
        assertEquals(modeloId, repositorioModificaciones.findById(orden.getId()).orElseThrow().getModeloId());
    }

    @Test
    void alQuitarLosModelosDeUnaTemporadaElDeHistorialSoloSeDesvincula() throws Exception {
        TemporadaGuardada temporada = archivo.guardar(null, "AMI", "CAT-HISTORIAL", "p.xlsx", new byte[] {1});
        catalogo.asegurarArticulo("AMI", "ULL972.AL0001");
        catalogo.asegurarArticulo("AMI", "ULL973.AL0001");
        Long sinHistorial = catalogo.modelo("AMI", "ULL972").orElseThrow().getId();
        Long conHistorial = catalogo.modelo("AMI", "ULL973").orElseThrow().getId();
        catalogo.vincularTemporada(sinHistorial, temporada.getId());
        catalogo.vincularTemporada(conHistorial, temporada.getId());
        Modificacion orden = orden(conHistorial);

        mvc.perform(post("/catalogo/temporada/quitar-modelos")
                        .param("cliente", "AMI").param("temporada", String.valueOf(temporada.getId())))
                .andExpect(flash().attribute("mensaje", allOf(containsString("1 borrado"),
                        containsString("1 desvinculado"), containsString("1 con historial del taller"))));

        assertTrue(catalogo.modelo("AMI", "ULL972").isEmpty());
        assertTrue(catalogo.modelo("AMI", "ULL973").isPresent());
        assertFalse(catalogo.temporadasDeModelo(conHistorial).contains(temporada.getId()));
        assertEquals(conHistorial, repositorioModificaciones.findById(orden.getId()).orElseThrow().getModeloId());
    }

    @Test
    void laBaseNoDejaBorrarUnModeloConHistorialAunqueAlguienLoIntenteSinPreguntar() {
        catalogo.asegurarArticulo("AMI", "ULL974.AL0001");
        Long modeloId = catalogo.modelo("AMI", "ULL974").orElseThrow().getId();
        orden(modeloId);

        assertThrows(DataIntegrityViolationException.class, () -> {
            repositorioModelos.deleteById(modeloId);
            repositorioModelos.flush();
        });
        assertTrue(repositorioModelos.findById(modeloId).isPresent());
    }

    private Modificacion orden(Long modeloId) {
        Usuario despacho = ComoUsuario.usuarioDePrueba(usuarios, Rol.DESPACHO);
        return modificaciones.crearOrdenParaTaller(new Modificaciones.OrdenDespacho(modeloId, null,
                        "Cambiar asa", "Asa de 2 cm", Prioridad.NORMAL, List.of()),
                new UsuarioActual(despacho.getId(), despacho.getNombre(), despacho.getRol(), null));
    }
}
