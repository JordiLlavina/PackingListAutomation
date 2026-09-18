package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El esquema lo crea Flyway y Hibernate solo lo valida. Si alguien vuelve a
 * poner ddl-auto: update, la tabla de historial de Flyway sigue existiendo
 * pero las tablas nuevas se crearían por dos caminos distintos y acabarían
 * divergiendo entre la base del almacén y la de los tests.
 */
@SpringBootTest
class EsquemaFlywayTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayHaAplicadoElBaselineYLasTablasExisten() {
        List<String> versiones = jdbc.queryForList(
                "select version from flyway_schema_history where success = true order by installed_rank",
                String.class);
        assertTrue(versiones.contains("1"), "V1 aplicada: " + versiones);

        Integer tablas = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'PUBLIC' "
                        + "and table_name in ('TARA_CAJA', 'MEMORIA_CAJA_REFERENCIA', 'TEMPORADA_GUARDADA')",
                Integer.class);
        assertEquals(3, tablas);
    }
}
