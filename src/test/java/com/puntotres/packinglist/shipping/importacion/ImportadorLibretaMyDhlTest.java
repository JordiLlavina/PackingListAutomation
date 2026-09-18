package com.puntotres.packinglist.shipping.importacion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.puntotres.packinglist.shipping.model.*;
import com.puntotres.packinglist.shipping.service.BillingAccountService;
import com.puntotres.packinglist.shipping.service.ContactService;
import com.puntotres.packinglist.shipping.service.DatosContacto;

class ImportadorLibretaMyDhlTest {

    private ContactService contactos;
    private BillingAccountService cuentas;
    private ImportadorLibretaMyDhl importador;
    private byte[] csv;

    @BeforeEach
    void preparar() throws Exception {
        contactos = mock(ContactService.class);
        cuentas = mock(BillingAccountService.class);
        when(cuentas.asegurar(anyString(), anyString(), anyBoolean(), any()))
                .thenAnswer(inv -> new BillingAccount(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
        when(contactos.guardar(isNull(), any())).thenAnswer(inv -> new Contact("x", "x", "x", "ES"));
        importador = new ImportadorLibretaMyDhl(contactos, cuentas);
        csv = new ClassPathResource("ejemplos/envios/libreta-mydhl-recorte.csv").getInputStream().readAllBytes();
    }

    @Test
    void leeLas44ColumnasConElDobleBomYClasificaLasFilas() {
        PrevisualizacionLibreta prev = importador.previsualizar(csv, "libreta.csv");

        assertEquals(8, prev.filas().size());
        assertEquals(5, prev.listas().size());
        assertEquals(1, prev.duplicadas().size());
        assertEquals(2, prev.erroneas().size());
    }

    @Test
    void elMapeoDeColumnasEsElDeLaLibretaDeMyDhl() {
        FilaLibreta jean = importador.previsualizar(csv, "l.csv").filas().get(2);
        DatosContacto d = jean.datos();

        assertEquals("MAISON CLIENTE", d.empresa());
        assertEquals("JEAN CLIENT", d.persona());
        assertEquals("+33612345678", d.telefono(), "prefijo + número sin el cero inicial");
        assertEquals("jean@maison.fr", d.email());
        assertEquals("FR12345678901", d.nifVat());
        assertEquals("FR12345678901000", d.eori());
        assertEquals("FR", d.pais());
        assertEquals("2 Rue Exemple", d.direccion1());
        assertEquals("Bâtiment B", d.direccion2());
        assertEquals("75002", d.codigoPostal());
        assertEquals("Paris", d.ciudad());
        assertEquals("Ile-de-France", d.provincia());
        assertEquals(ContactType.BUSINESS, d.tipo());
        assertFalse(d.residencial());
        assertEquals(Incoterm.DAP, d.incotermPorDefecto());
        assertTrue(d.notas().contains("Entregar en recepción"));
        assertTrue(d.notas().contains("compta@maison.fr"), "Email 2 va a notas");
        assertTrue(d.notas().contains("ext. 12"));
        assertEquals(List.of("JEAN CLIENT at MAISON CLIENTE"), d.alias());
        assertEquals("956483537", jean.cuentaTransporte());
        assertEquals("956483537", jean.cuentaAranceles());
        assertNull(jean.cuentaRemitente());
    }

    @Test
    void losAliasVaciosOQueSoloDicenAtSeDescartanYElParticularEsPrivate() {
        List<FilaLibreta> filas = importador.previsualizar(csv, "l.csv").filas();

        assertEquals(List.of(), filas.get(0).datos().alias(), "'ANA PEREZ at ' es la persona más basura");
        assertEquals(List.of(), filas.get(3).datos().alias(), "' at MAISON CLIENTE' es la empresa más basura");
        assertEquals(ContactType.PRIVATE, filas.get(7).datos().tipo());
        assertTrue(filas.get(7).datos().residencial());
        assertEquals("Av. Ejemplo 10, 3º 2ª", filas.get(7).datos().direccion1());
    }

    @Test
    void losErroresDicenQueFalta() {
        List<FilaLibreta> erroneas = importador.previsualizar(csv, "l.csv").erroneas();

        assertTrue(erroneas.get(0).errores().get(0).contains("país"));
        assertTrue(erroneas.get(1).errores().get(0).contains("dirección"));
    }

    @Test
    void unDuplicadoDentroDelCsvSeSenalaConLaFilaOriginal() {
        FilaLibreta dup = importador.previsualizar(csv, "l.csv").duplicadas().get(0);

        assertEquals(4, dup.numero());
        assertEquals("fila 3", dup.duplicadoDe());
    }

    @Test
    void unContactoQueYaEstaEnLaLibretaTambienEsDuplicado() {
        when(contactos.existe("TALLER EJEMPLO SARL", "ANA PEREZ")).thenReturn(true);

        PrevisualizacionLibreta prev = importador.previsualizar(csv, "l.csv");

        assertEquals(2, prev.duplicadas().size());
        assertEquals("ya en la libreta", prev.duplicadas().get(0).duplicadoDe());
    }

    @Test
    void importarDaDeAltaLasListasYLosDuplicadosMarcadosYCreaLasCuentas() {
        PrevisualizacionLibreta prev = importador.previsualizar(csv, "l.csv");

        ResultadoImportacion resultado = importador.importar(prev, Set.of(4));

        assertEquals(6, resultado.altas());
        assertEquals(0, resultado.omitidas());
        assertEquals(2, resultado.errores().size());
        verify(contactos, times(6)).guardar(isNull(), any());
        verify(cuentas).asegurar("956483537", "MAISON CLIENTE", false, EnumSet.of(BillingRole.PAYER, BillingRole.DUTIES));
        verify(cuentas).asegurar("300112049", "PUNTOTRES BAGS & BELTS SLU", false, EnumSet.of(BillingRole.SHIPPER));
        verify(cuentas).asegurar("300112049", "PELLI ESEMPIO SRL", false, EnumSet.of(BillingRole.SHIPPER, BillingRole.PAYER));
    }

    @Test
    void losDuplicadosNoMarcadosSeOmiten() {
        ResultadoImportacion resultado = importador.importar(importador.previsualizar(csv, "l.csv"), Set.of());

        assertEquals(5, resultado.altas());
        assertEquals(1, resultado.omitidas());
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIf("hayExportReal")
    void elExportRealDeMyDhlSeLeeEnteroYSinErrores() throws Exception {
        java.nio.file.Path real = exportReal();
        PrevisualizacionLibreta prev = importador.previsualizar(java.nio.file.Files.readAllBytes(real), real.getFileName().toString());

        assertEquals(213, prev.filas().size());
        assertEquals(0, prev.erroneas().size(), prev.erroneas().toString());
        assertTrue(prev.duplicadas().size() >= 4, "el export real trae al menos cuatro grupos repetidos");
    }

    static boolean hayExportReal() { return exportReal() != null; }

    static java.nio.file.Path exportReal() {
        try (var ficheros = java.nio.file.Files.list(java.nio.file.Path.of("docs", "Envios DHL"))) {
            return ficheros.filter(f -> f.getFileName().toString().startsWith("address-book-") && f.toString().endsWith(".csv")).findFirst().orElse(null);
        } catch (java.io.IOException e) {
            return null;
        }
    }
}
