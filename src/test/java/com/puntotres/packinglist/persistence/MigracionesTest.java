package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Las migraciones de Flyway son ahora la única fuente del esquema: Hibernate
 * ya no lo crea. Este test comprueba que se aplican y que la tabla de control
 * existe, que es lo que distingue "Flyway ha corrido" de "Hibernate ha vuelto
 * a crear las tablas por su cuenta".
 */
@SpringBootTest
class MigracionesTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void lasMigracionesSeAplicanYNoHayNingunaFallida() throws Exception {
        List<String> fallidas = new ArrayList<>();
        int aplicadas = 0;
        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     // Flyway crea la tabla con el nombre entre comillas, así que H2 la
                     // guarda en minúsculas y hay que pedirla igual.
                     "select \"version\", \"success\" from \"flyway_schema_history\" order by \"installed_rank\"")) {
            while (rs.next()) {
                aplicadas++;
                if (!rs.getBoolean("success")) {
                    fallidas.add(rs.getString("version"));
                }
            }
        }
        assertTrue(aplicadas >= 1, "Flyway no ha aplicado ninguna migración");
        assertEquals(List.of(), fallidas, "Hay migraciones fallidas");
    }

    @Test
    void elEsquemaDeLasTablasDeHoySigueEnPie() throws Exception {
        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement()) {
            for (String tabla : List.of("tara_caja", "memoria_caja_referencia", "temporada_guardada")) {
                try (ResultSet rs = st.executeQuery("select count(*) from " + tabla)) {
                    assertTrue(rs.next(), "No se puede consultar la tabla " + tabla);
                }
            }
        }
    }
}
