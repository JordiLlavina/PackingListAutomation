package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * La V3 pasa de "un excel y su tipo" a "dos excels" sin perder nada de lo
 * guardado: es la primera migración con datos desde que la aplicación se usa,
 * y la regla es que nada de lo que se recuerda se pierda. Se prueba contra
 * una base H2 propia llevada primero hasta la V2, con filas de los dos tipos.
 */
class MigracionTemporadaDosExcelsTest {

    @Test
    void cadaExcelAcabaEnSuColumnaYElTipoDesaparece() throws Exception {
        String url = "jdbc:h2:mem:migracion-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
        Flyway.configure().dataSource(url, "sa", "").target("2").load().migrate();
        try (Connection conexion = DriverManager.getConnection(url, "sa", "")) {
            insertar(conexion, "AMI", "H26", "pedido.xlsx", new byte[] {1, 2}, "PEDIDO");
            insertar(conexion, "KAMI", "E26", "prod.xlsx", new byte[] {3, 4}, "PRODUCCION");
        }

        Flyway.configure().dataSource(url, "sa", "").load().migrate();

        try (Connection conexion = DriverManager.getConnection(url, "sa", "");
             ResultSet filas = conexion.createStatement().executeQuery("""
                     select cliente, nombre_fichero_pedido, excel_pedido,
                            nombre_fichero_produccion, excel_produccion
                       from temporada_guardada order by cliente""")) {
            filas.next();
            assertEquals("AMI", filas.getString(1));
            assertEquals("pedido.xlsx", filas.getString(2));
            assertArrayEquals(new byte[] {1, 2}, filas.getBytes(3));
            assertNull(filas.getString(4));
            assertNull(filas.getBytes(5));
            filas.next();
            assertEquals("KAMI", filas.getString(1));
            assertNull(filas.getString(2), "el de producción ya no se hace pasar por pedido");
            assertNull(filas.getBytes(3));
            assertEquals("prod.xlsx", filas.getString(4));
            assertArrayEquals(new byte[] {3, 4}, filas.getBytes(5));
        }
    }

    private static void insertar(Connection conexion, String cliente, String temporada, String fichero,
                                 byte[] excel, String tipo) throws Exception {
        try (PreparedStatement insercion = conexion.prepareStatement("""
                insert into temporada_guardada (cliente, temporada, nombre_fichero, excel, tipo_excel,
                                                fecha_actualizacion)
                values (?, ?, ?, ?, ?, ?)""")) {
            insercion.setString(1, cliente);
            insercion.setString(2, temporada);
            insercion.setString(3, fichero);
            insercion.setBytes(4, excel);
            insercion.setString(5, tipo);
            insercion.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            insercion.executeUpdate();
        }
    }
}
