package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * La V10 añade «Pedir troqueles» a los avisos del taller que siguen abiertos
 * en una base llevada hasta la V9: aplicando si entre sus razones están los
 * patrones o los troqueles y con «No aplica» si no. Los cerrados y las
 * órdenes para el taller no lo ganan, y lo que ya tenían no se toca.
 */
class MigracionPedirTroquelesTest {

    @Test
    void losAvisosAbiertosGananPedirTroquelesConLaReglaDeSusRazones() throws Exception {
        String url = "jdbc:h2:mem:migracion-v10-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
        Flyway.configure().dataSource(url, "sa", "").target("9").load().migrate();
        LocalDateTime base = LocalDateTime.of(2026, 11, 1, 9, 0);
        long conPatrones;
        long conTroqueles;
        long sinNinguna;
        long sinRazon;
        long hecho;
        long orden;
        try (Connection conexion = DriverManager.getConnection(url, "sa", "")) {
            ejecutar(conexion, """
                    insert into modelo (cliente, codigo, nombre, tipo, etapa, fecha_etapa, fecha_actualizacion)
                    values (?, ?, ?, ?, ?, ?, ?)""", "MIG10", "V10", "CANDY", "BOLSO", "PRODUCCION",
                    Timestamp.valueOf(base), Timestamp.valueOf(base));
            long modelo = unLong(conexion, "select id from modelo where cliente = 'MIG10'");
            conPatrones = modificacion(conexion, modelo, "TALLER_A_DESPACHO", "CONSUMO_PIEL,MEDIDAS_PATRONES", "NUEVA", base);
            conTroqueles = modificacion(conexion, modelo, "TALLER_A_DESPACHO", "TROQUELES", "EN_CURSO", base);
            sinNinguna = modificacion(conexion, modelo, "TALLER_A_DESPACHO", "CONSUMO_PIEL,FORNITURAS", "VISTA", base);
            sinRazon = modificacion(conexion, modelo, "TALLER_A_DESPACHO", null, "NUEVA", base);
            hecho = modificacion(conexion, modelo, "TALLER_A_DESPACHO", "TROQUELES", "HECHA", base);
            orden = modificacion(conexion, modelo, "DESPACHO_A_TALLER", "TROQUELES", "NUEVA", base);
            ejecutar(conexion, "insert into modificacion_destino (modificacion_id, destino, aplica) values (?, 'ICSUITE', false)",
                    sinNinguna);
        }

        Flyway.configure().dataSource(url, "sa", "").load().migrate();

        try (Connection conexion = DriverManager.getConnection(url, "sa", "")) {
            assertTrue(aplicaPedirTroqueles(conexion, conPatrones), "Medidas / Patrones pide troqueles");
            assertTrue(aplicaPedirTroqueles(conexion, conTroqueles), "Cambio de troqueles pide troqueles");
            assertFalse(aplicaPedirTroqueles(conexion, sinNinguna), "Sin esas razones nace con «No aplica»");
            assertFalse(aplicaPedirTroqueles(conexion, sinRazon));
            assertEquals(0, pedirTroqueles(conexion, hecho), "Un aviso cerrado no gana una tarea nueva");
            assertEquals(0, pedirTroqueles(conexion, orden), "Una orden para el taller no tiene destinos");
            assertEquals(1, unLong(conexion, "select count(*) from modificacion_destino where modificacion_id = "
                    + sinNinguna + " and destino = 'ICSUITE' and aplica = false"), "Lo que ya tenía no se toca");
        }
    }

    private static long modificacion(Connection conexion, long modelo, String direccion, String tipoCambio,
                                     String estado, LocalDateTime cuando) throws SQLException {
        ejecutar(conexion, """
                insert into modificacion (modelo_id, direccion, tipo_cambio, estado, creada_en)
                values (?, ?, ?, ?, ?)""", modelo, direccion, tipoCambio, estado, Timestamp.valueOf(cuando));
        return unLong(conexion, "select max(id) from modificacion");
    }

    private static boolean aplicaPedirTroqueles(Connection conexion, long id) throws SQLException {
        assertEquals(1, pedirTroqueles(conexion, id));
        try (ResultSet filas = conexion.createStatement().executeQuery(
                "select aplica from modificacion_destino where destino = 'PEDIR_TROQUELES' and modificacion_id = " + id)) {
            assertTrue(filas.next());
            return filas.getBoolean(1);
        }
    }

    private static long pedirTroqueles(Connection conexion, long id) throws SQLException {
        return unLong(conexion, "select count(*) from modificacion_destino where destino = 'PEDIR_TROQUELES'"
                + " and modificacion_id = " + id);
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
