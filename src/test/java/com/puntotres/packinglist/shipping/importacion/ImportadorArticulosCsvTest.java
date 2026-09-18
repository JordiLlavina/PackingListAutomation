package com.puntotres.packinglist.shipping.importacion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.puntotres.packinglist.shipping.model.Article;
import com.puntotres.packinglist.shipping.model.ArticleRepository;
import com.puntotres.packinglist.shipping.service.ArticleService;

class ImportadorArticulosCsvTest {

    private final ArticleRepository repo = mock(ArticleRepository.class);
    private final ArticleService servicio = new ArticleService(repo);
    private final ImportadorArticulosCsv importador = new ImportadorArticulosCsv(servicio);

    private byte[] csv() throws Exception {
        return new ClassPathResource("ejemplos/envios/articulos.csv").getInputStream().readAllBytes();
    }

    @Test
    void separaValidasDeErroneasYLeeLaComaDecimal() throws Exception {
        PrevisualizacionArticulos prev = importador.previsualizar(csv(), "articulos.csv");

        assertEquals(2, prev.validas().size());
        assertEquals(2, prev.erroneas().size());
        FilaArticulo cinturon = prev.validas().get(0);
        assertEquals("UBL029", cinturon.referencia());
        assertEquals(new BigDecimal("8.50"), cinturon.valorUnitario());
        assertEquals(0.18, cinturon.pesoNeto(), 0.0001);
        assertTrue(prev.erroneas().get(0).errores().get(0).contains("código HS"));
        assertTrue(prev.erroneas().get(1).errores().get(0).contains("referencia"));
    }

    @Test
    void unaReferenciaQueYaExisteSeMarcaYAlImportarSeActualiza() throws Exception {
        Article existente = new Article("UBL029", "Cinturón", "42033000", "MA");
        when(repo.findByReferenciaIgnoreCase("UBL029")).thenReturn(Optional.of(existente));
        when(repo.findAllByOrderByReferenciaAsc()).thenReturn(java.util.List.of(existente));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PrevisualizacionArticulos prev = importador.previsualizar(csv(), "a.csv");
        assertTrue(prev.validas().get(0).yaExiste());
        assertFalse(prev.validas().get(1).yaExiste());

        ResultadoImportacion r = importador.importar(prev);

        assertEquals(1, r.altas());
        assertEquals(1, r.actualizadas());
        assertEquals(2, r.errores().size());
        assertEquals("Cinturón de piel de vacuno", existente.getDescripcionAduanera());
    }

    @Test
    void sinLaCabeceraEsperadaAvisa() {
        assertThrows(com.puntotres.packinglist.shipping.service.DatoInvalidoException.class,
                () -> importador.previsualizar("ref,desc\nA,B\n".getBytes(), "x.csv"));
    }
}
