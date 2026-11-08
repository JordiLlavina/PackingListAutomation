package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * La V4 da al modelo un id interno (antes su clave era cliente + código) y
 * recoloca sus hijos (artículos, fotos, temporadas) sin perder nada de lo
 * guardado. Se prueba contra una base H2 propia llevada primero hasta la V3,
 * con un modelo, un artículo, una foto y un vínculo a temporada.
 */
class MigracionModelosV4Test {

    private static String urlNueva() {
        return "jdbc:h2:mem:migracion-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
    }

    @Test
    void losHijosApuntanAlIdDeSuModeloYNadaSePierde() throws Exception {
        String url = urlNueva();
        Flyway.configure().dataSource(url, "sa", "").target("3").load().migrate();
        long temporadaId;
        try (Connection conexion = DriverManager.getConnection(url, "sa", "")) {
            ejecutar(conexion, "insert into modelo (cliente, codigo, nombre, tipo, fecha_actualizacion) values (?, ?, ?, ?, ?)",
                    "MIG4", "ULL027", "CANDY", "BOLSO", ahora());
            ejecutar(conexion, """
                    insert into articulo (cliente, referencia, codigo_modelo, fecha_actualizacion)
                    values (?, ?, ?, ?)""", "MIG4", "ULL027.AL0103", "ULL027", ahora());
            ejecutar(conexion, """
                    insert into foto_modelo (cliente, codigo_modelo, nombre_fichero, principal, orden, bytes,
                                             fecha_actualizacion)
                    values (?, ?, ?, ?, ?, ?, ?)""", "MIG4", "ULL027", "principal.jpg", true, 0,
                    new byte[] {1, 2, 3}, ahora());
            ejecutar(conexion, """
                    insert into temporada_guardada (cliente, temporada, fecha_actualizacion)
                    values (?, ?, ?)""", "MIG4", "H26", ahora());
            temporadaId = unLong(conexion, "select id from temporada_guardada where cliente = 'MIG4'");
            ejecutar(conexion, """
                    insert into modelo_temporada (cliente, codigo_modelo, temporada_id) values (?, ?, ?)""",
                    "MIG4", "ULL027", temporadaId);
        }

        Flyway.configure().dataSource(url, "sa", "").load().migrate();

        try (Connection conexion = DriverManager.getConnection(url, "sa", "")) {
            long id = unLong(conexion, "select id from modelo where cliente = 'MIG4' and codigo = 'ULL027'");
            assertEquals(id, unLong(conexion, "select modelo_id from articulo where referencia = 'ULL027.AL0103'"));
            try (ResultSet foto = conexion.createStatement()
                    .executeQuery("select modelo_id, bytes from foto_modelo where nombre_fichero = 'principal.jpg'")) {
                assertTrue(foto.next());
                assertEquals(id, foto.getLong(1));
                assertArrayEquals(new byte[] {1, 2, 3}, foto.getBytes(2));
            }
            try (ResultSet vinculo = conexion.createStatement()
                    .executeQuery("select modelo_id, temporada_id from modelo_temporada")) {
                assertTrue(vinculo.next());
                assertEquals(id, vinculo.getLong(1));
                assertEquals(temporadaId, vinculo.getLong(2));
            }
            try (ResultSet modelo = conexion.createStatement()
                    .executeQuery("select etapa, nombre_prototipo, nombre from modelo where id = " + id)) {
                assertTrue(modelo.next());
                assertEquals("PRODUCCION", modelo.getString(1));
                assertNull(modelo.getString(2));
                assertEquals("CANDY", modelo.getString(3));
            }
        }
    }

    @Test
    void dosPrototiposConElMismoNombreConvivenEnTemporadasDistintasYNoEnLaMisma() throws Exception {
        String url = urlNueva();
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (Connection conexion = DriverManager.getConnection(url, "sa", "")) {
            ejecutar(conexion, "insert into temporada_guardada (cliente, temporada, fecha_actualizacion) values (?, ?, ?)",
                    "MIG4", "H26", ahora());
            ejecutar(conexion, "insert into temporada_guardada (cliente, temporada, fecha_actualizacion) values (?, ?, ?)",
                    "MIG4", "SS27", ahora());
            long t1 = unLong(conexion, "select id from temporada_guardada where temporada = 'H26'");
            long t2 = unLong(conexion, "select id from temporada_guardada where temporada = 'SS27'");

            insertarPrototipo(conexion, "PROTO A", t1);
            insertarPrototipo(conexion, "PROTO A", t2);
            assertThrows(SQLException.class, () -> insertarPrototipo(conexion, "PROTO A", t1));
        }
    }

    @Test
    void unModeloSinCodigoNiPrototipoSeRechaza() throws Exception {
        String url = urlNueva();
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (Connection conexion = DriverManager.getConnection(url, "sa", "")) {
            SQLException error = assertThrows(SQLException.class, () -> ejecutar(conexion, """
                    insert into modelo (cliente, codigo, nombre_prototipo, nombre, tipo, etapa, fecha_etapa,
                                        fecha_actualizacion)
                    values (?, null, null, ?, ?, ?, ?, ?)""", "MIG4", "SIN IDENTIDAD", "BOLSO", "PRODUCCION",
                    ahora(), ahora()));
            assertTrue(error.getMessage().toUpperCase().contains("CK_MODELO_IDENTIDAD"), error.getMessage());
        }
    }

    private static void insertarPrototipo(Connection conexion, String nombrePrototipo, long temporadaId) throws SQLException {
        ejecutar(conexion, """
                insert into modelo (cliente, codigo, nombre_prototipo, nombre, tipo, etapa, fecha_etapa,
                                    temporada_alta_id, fecha_actualizacion)
                values (?, null, ?, ?, ?, ?, ?, ?, ?)""", "MIG4", nombrePrototipo, nombrePrototipo, "BOLSO",
                "PROTOTIPO", ahora(), temporadaId, ahora());
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
