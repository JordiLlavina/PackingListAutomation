package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * Desde la V5 las modificaciones y los documentos de un modelo ya no caen con
 * él: la base rechaza borrar un modelo que todavía los tiene. Es la red de
 * seguridad de abajo, por si una ruta vieja o un código futuro que no conozca
 * el taller intenta borrar el modelo sin preguntar a {@code HistorialDelTaller}.
 */
class MigracionHistorialSinCascadaTest {

    private static String urlNueva() {
        return "jdbc:h2:mem:migracion-v5-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
    }

    @Test
    void unModeloConModificacionNoSeDejaBorrarYSinElla_si() throws Exception {
        String url = urlNueva();
        Flyway.configure().dataSource(url, "sa", "").target("5").load().migrate();
        try (Connection conexion = DriverManager.getConnection(url, "sa", "")) {
            long modeloId = insertarModelo(conexion, "V5A");
            ejecutar(conexion, """
                    insert into modificacion (modelo_id, direccion, estado, creada_en)
                    values (?, ?, ?, ?)""", modeloId, "TALLER_A_DESPACHO", "NUEVA", ahora());

            assertThrows(SQLException.class,
                    () -> ejecutar(conexion, "delete from modelo where id = ?", modeloId));
            assertEquals(1, unLong(conexion, "select count(*) from modelo where id = " + modeloId));
            assertEquals(1, unLong(conexion, "select count(*) from modificacion where modelo_id = " + modeloId));

            ejecutar(conexion, "delete from modificacion where modelo_id = ?", modeloId);
            ejecutar(conexion, "delete from modelo where id = ?", modeloId);
            assertEquals(0, unLong(conexion, "select count(*) from modelo where id = " + modeloId));
        }
    }

    @Test
    void unModeloConDocumentoNoSeDejaBorrarYSinEl_si() throws Exception {
        String url = urlNueva();
        Flyway.configure().dataSource(url, "sa", "").target("5").load().migrate();
        try (Connection conexion = DriverManager.getConnection(url, "sa", "")) {
            long modeloId = insertarModelo(conexion, "V5B");
            ejecutar(conexion, """
                    insert into documento_modelo (modelo_id, nombre, mime, tamano, bytes, fecha)
                    values (?, ?, ?, ?, ?, ?)""", modeloId, "ficha.pdf", "application/pdf", 3L,
                    new byte[] {1, 2, 3}, ahora());

            assertThrows(SQLException.class,
                    () -> ejecutar(conexion, "delete from modelo where id = ?", modeloId));

            ejecutar(conexion, "delete from documento_modelo where modelo_id = ?", modeloId);
            ejecutar(conexion, "delete from modelo where id = ?", modeloId);
            assertEquals(0, unLong(conexion, "select count(*) from modelo where id = " + modeloId));
        }
    }

    private static long insertarModelo(Connection conexion, String codigo) throws SQLException {
        ejecutar(conexion, """
                insert into modelo (cliente, codigo, nombre, tipo, etapa, fecha_etapa, fecha_actualizacion)
                values (?, ?, ?, ?, ?, ?, ?)""", "MIG5", codigo, "CANDY", "BOLSO", "PRODUCCION", ahora(), ahora());
        return unLong(conexion, "select id from modelo where cliente = 'MIG5' and codigo = '" + codigo + "'");
    }

    private static Timestamp ahora() {
        return Timestamp.valueOf(LocalDateTime.now());
    }

    private static long unLong(Connection conexion, String sql) throws SQLException {
        try (ResultSet filas = conexion.createStatement().executeQuery(sql)) {
            assertTrue(filas.next());
            return filas.getLong(1);
        }
    }

    private static void ejecutar(Connection conexion, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                sentencia.setObject(i + 1, parametros[i]);
            }
            sentencia.executeUpdate();
        }
    }
}
