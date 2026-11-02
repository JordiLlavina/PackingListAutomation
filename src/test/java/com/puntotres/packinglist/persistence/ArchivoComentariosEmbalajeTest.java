package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Los comentarios tipo de la hoja de embalaje contra la base real. Los nombres
 * llevan un prefijo propio y cada test borra los suyos: la base en memoria es
 * la misma para toda la suite.
 */
@SpringBootTest
class ArchivoComentariosEmbalajeTest {

    @Autowired
    private ArchivoComentariosEmbalaje archivo;

    @Test
    void altaEdicionYBorrado() {
        ComentarioEmbalaje alta = archivo.guardar(null, "  Prueba alta  ", "No apilar");
        try {
            assertEquals("Prueba alta", alta.getNombre(), "el nombre se guarda recortado");
            assertEquals("No apilar", archivo.uno(alta.getId()).orElseThrow().getTexto());
            assertTrue(archivo.todos().stream().anyMatch(c -> c.getId().equals(alta.getId())));

            archivo.guardar(alta.getId(), "Prueba alta editada", "Apilar de dos en dos");
            ComentarioEmbalaje editado = archivo.uno(alta.getId()).orElseThrow();
            assertEquals("Prueba alta editada", editado.getNombre());
            assertEquals("Apilar de dos en dos", editado.getTexto());
        } finally {
            archivo.borrar(alta.getId());
        }
        assertTrue(archivo.uno(alta.getId()).isEmpty());
    }

    @Test
    void unNombreRepetidoSeRechazaSinMirarMayusculasNiEspacios() {
        ComentarioEmbalaje primero = archivo.guardar(null, "Cajas pesadas prueba", "Texto uno");
        try {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> archivo.guardar(null, " cajas PESADAS prueba ", "Texto dos"));
            assertEquals("Ya hay un comentario llamado «cajas PESADAS prueba»", error.getMessage());

            // La propia fila conserva su nombre al editarla.
            archivo.guardar(primero.getId(), "Cajas pesadas prueba", "Texto uno corregido");
            assertEquals("Texto uno corregido", archivo.uno(primero.getId()).orElseThrow().getTexto());
        } finally {
            archivo.borrar(primero.getId());
        }
    }
}
