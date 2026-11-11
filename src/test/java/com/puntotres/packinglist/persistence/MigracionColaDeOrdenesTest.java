package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * La V6 cambia las prioridades por la cola: las órdenes abiertas de una base
 * llevada hasta la V5 salen en la cola con el orden que tenían (alta, normal o
 * sin prioridad, baja; a igualdad la más antigua), las altas como urgentes, y
 * ni los avisos ni las cerradas entran. La prioridad vieja se queda guardada.
 */
class MigracionColaDeOrdenesTest {

    @Test
    void lasOrdenesAbiertasEntranEnLaColaConElOrdenQueTenian() throws Exception {
        String url = "jdbc:h2:mem:migracion-v6-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
        Flyway.configure().dataSource(url, "sa", "").target("5").load().migrate();
        LocalDateTime base = LocalDateTime.of(2026, 11, 1, 9, 0);
        long baja;
        long normalVieja;
        long normalNueva;
        long sinPrioridad;
        long alta;
        long hecha;
        long aviso;
        try (Connection conexion = DriverManager.getConnection(url, "sa", "")) {
            ejecutar(conexion, """
                    insert into modelo (cliente, codigo, nombre, tipo, etapa, fecha_etapa, fecha_actualizacion)
                    values (?, ?, ?, ?, ?, ?, ?)""", "MIG6", "V6", "CANDY", "BOLSO", "PRODUCCION",
                    Timestamp.valueOf(base), Timestamp.valueOf(base));
            long modelo = unLong(conexion, "select id from modelo where cliente = 'MIG6'");
            baja = orden(conexion, modelo, "BAJA", "NUEVA", base);
            normalVieja = orden(conexion, modelo, "NORMAL", "VISTA", base.plusHours(1));
            normalNueva = orden(conexion, modelo, "NORMAL", "EN_CURSO", base.plusHours(3));
            sinPrioridad = orden(conexion, modelo, null, "NUEVA", base.plusHours(2));
            alta = orden(conexion, modelo, "ALTA", "NUEVA", base.plusHours(5));
            hecha = orden(conexion, modelo, "ALTA", "HECHA", base.minusDays(1));
            ejecutar(conexion, """
                    insert into modificacion (modelo_id, direccion, prioridad, estado, creada_en)
                    values (?, 'TALLER_A_DESPACHO', 'ALTA', 'NUEVA', ?)""", modelo, Timestamp.valueOf(base));
            aviso = unLong(conexion, "select max(id) from modificacion");
        }

        Flyway.configure().dataSource(url, "sa", "").load().migrate();

        try (Connection conexion = DriverManager.getConnection(url, "sa", "")) {
            assertEquals(1, posicion(conexion, alta));
            assertEquals(2, posicion(conexion, normalVieja));
            assertEquals(3, posicion(conexion, sinPrioridad), "Sin prioridad contaba como normal");
            assertEquals(4, posicion(conexion, normalNueva));
            assertEquals(5, posicion(conexion, baja));
            assertNull(posicion(conexion, hecha), "Una cerrada no entra en la cola");
            assertNull(posicion(conexion, aviso), "Un aviso del taller no entra en la cola");

            assertTrue(urgente(conexion, alta), "Las abiertas de prioridad alta pasan a urgentes");
            assertFalse(urgente(conexion, normalVieja));
            assertFalse(urgente(conexion, hecha), "Una cerrada no pasa a urgente");
            assertFalse(urgente(conexion, aviso));
            assertEquals("BAJA", texto(conexion, "select prioridad from modificacion where id = " + baja),
                    "La prioridad vieja se queda guardada");
        }
    }

    private static long orden(Connection conexion, long modelo, String prioridad, String estado, LocalDateTime cuando)
            throws SQLException {
        ejecutar(conexion, """
                insert into modificacion (modelo_id, direccion, prioridad, estado, titulo, creada_en)
                values (?, 'DESPACHO_A_TALLER', ?, ?, 'Orden', ?)""", modelo, prioridad, estado,
                Timestamp.valueOf(cuando));
        return unLong(conexion, "select max(id) from modificacion");
    }

    private static Integer posicion(Connection conexion, long id) throws SQLException {
        try (ResultSet filas = conexion.createStatement()
                .executeQuery("select posicion_cola from modificacion where id = " + id)) {
            assertTrue(filas.next());
            int valor = filas.getInt(1);
            return filas.wasNull() ? null : valor;
        }
    }

    private static boolean urgente(Connection conexion, long id) throws SQLException {
        try (ResultSet filas = conexion.createStatement()
                .executeQuery("select urgente from modificacion where id = " + id)) {
            assertTrue(filas.next());
            return filas.getBoolean(1);
        }
    }

    private static String texto(Connection conexion, String sql) throws SQLException {
        try (ResultSet filas = conexion.createStatement().executeQuery(sql)) {
            assertTrue(filas.next());
            return filas.getString(1);
        }
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
