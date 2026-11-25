package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

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
 * La V15 pasa el peso del artículo de "neto de una caja llena" a "neto por
 * unidad" sin perder ninguno que se pueda convertir: el de la caja llena
 * entre las unidades por caja. Uno sin unidades no tiene de dónde sacar la
 * unidad y se queda sin peso, igual que un artículo que nunca lo tuvo.
 */
class MigracionPesoPorUnidadTest {

    @Test
    void elPesoDeLaCajaLlenaPasaAPesoPorUnidad() throws Exception {
        String url = "jdbc:h2:mem:migracion-v15-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
        Flyway.configure().dataSource(url, "sa", "").target("14").load().migrate();
        try (Connection conexion = DriverManager.getConnection(url, "sa", "")) {
            LocalDateTime ahora = LocalDateTime.of(2026, 11, 25, 9, 0);
            ejecutar(conexion, """
                    insert into modelo (cliente, codigo, nombre, tipo, etapa, fecha_etapa, fecha_actualizacion)
                    values (?, ?, ?, ?, ?, ?, ?)""", "MIG15", "ULL027", "CANDY", "BOLSO", "PRODUCCION",
                    Timestamp.valueOf(ahora), Timestamp.valueOf(ahora));
            long modelo = unLong(conexion, "select id from modelo where cliente = 'MIG15'");
            articulo(conexion, modelo, "ULL027.AL0001", 12, 6.0, ahora);
            articulo(conexion, modelo, "ULL027.AL0002", 3, 1.0, ahora);
            articulo(conexion, modelo, "ULL027.AL0003", null, 5.0, ahora);
            articulo(conexion, modelo, "ULL027.AL0004", 10, null, ahora);
        }

        Flyway.configure().dataSource(url, "sa", "").load().migrate();

        try (Connection conexion = DriverManager.getConnection(url, "sa", "")) {
            assertEquals(0.5, peso(conexion, "ULL027.AL0001"), 1e-9);
            assertEquals(0.3333, peso(conexion, "ULL027.AL0002"), 1e-9, "cuatro decimales");
            assertNull(peso(conexion, "ULL027.AL0003"), "sin unidades por caja no se puede convertir");
            assertNull(peso(conexion, "ULL027.AL0004"));
            assertEquals(12, unLong(conexion,
                    "select unidades_por_caja from articulo where referencia = 'ULL027.AL0001'"),
                    "las unidades por caja no se tocan");
            try (ResultSet columnas = conexion.getMetaData().getColumns(null, null, "ARTICULO", "PESO_NETO_KG")) {
                assertFalse(columnas.next(), "la columna del peso de la caja llena desaparece");
            }
        }
    }

    private static void articulo(Connection conexion, long modelo, String referencia, Integer unidades,
                                 Double pesoCajaLlena, LocalDateTime cuando) throws SQLException {
        ejecutar(conexion, """
                insert into articulo (cliente, referencia, modelo_id, unidades_por_caja, peso_neto_kg,
                                      fecha_actualizacion)
                values (?, ?, ?, ?, ?, ?)""", "MIG15", referencia, modelo, unidades, pesoCajaLlena,
                Timestamp.valueOf(cuando));
    }

    private static Double peso(Connection conexion, String referencia) throws SQLException {
        try (ResultSet filas = conexion.createStatement().executeQuery(
                "select peso_neto_unidad_kg from articulo where referencia = '" + referencia + "'")) {
            filas.next();
            double valor = filas.getDouble(1);
            return filas.wasNull() ? null : valor;
        }
    }

    private static void ejecutar(Connection conexion, String sql, Object... valores) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) {
                sentencia.setObject(i + 1, valores[i]);
            }
            sentencia.executeUpdate();
        }
    }

    private static long unLong(Connection conexion, String sql) throws SQLException {
        try (ResultSet filas = conexion.createStatement().executeQuery(sql)) {
            filas.next();
            return filas.getLong(1);
        }
    }
}
