package com.puntotres.packinglist.service.pieles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.persistence.Articulo;
import com.puntotres.packinglist.persistence.ArticuloRepository;
import com.puntotres.packinglist.persistence.FichaPiel;
import com.puntotres.packinglist.persistence.MemoriaPieles;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.Piel;
import com.puntotres.packinglist.persistence.PielId;
import com.puntotres.packinglist.persistence.PielRepository;
import com.puntotres.packinglist.persistence.PrecioPiel;
import com.puntotres.packinglist.persistence.Proveedor;
import com.puntotres.packinglist.persistence.ResumenFotoPiel;
import com.puntotres.packinglist.persistence.TemporadaGuardada;
import com.puntotres.packinglist.persistence.TemporadaGuardadaRepository;
import com.puntotres.packinglist.persistence.TipoEventoPiel;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.pieles.BibliotecaPieles.Alta;
import com.puntotres.packinglist.service.pieles.BibliotecaPieles.Fila;
import com.puntotres.packinglist.service.pieles.BibliotecaPieles.Filtro;
import com.puntotres.packinglist.service.pieles.BibliotecaPieles.Paso;
import com.puntotres.packinglist.service.pieles.BibliotecaPieles.SinFicha;
import com.puntotres.packinglist.service.pieles.BibliotecaPieles.Tramo;
import com.puntotres.packinglist.testutil.FotosDePrueba;

/**
 * La biblioteca de pieles contra la base de datos. La base es la misma para
 * toda la suite, así que cada test usa su propio proveedor y sus propias
 * referencias de piel, y las pieles de cliente van a DROLE DU MONSIEUR, que
 * no usa ningún otro test.
 */
@SpringBootTest
class BibliotecaPielesTest {

    private static final String CLIENTE = "DROLE DU MONSIEUR";

    @Autowired
    private BibliotecaPieles biblioteca;

    @Autowired
    private MemoriaPieles memoriaPieles;

    @Autowired
    private PielRepository pieles;

    @Autowired
    private CatalogoModelos catalogo;

    @Autowired
    private ArticuloRepository articulos;

    @Autowired
    private TemporadaGuardadaRepository temporadas;

    // --- Alta y datos ---

    @Test
    void elAltaSoloPideProveedorYNombreYElAnimalEsElPrimeroDeLaLista() {
        Alta alta = biblioteca.crear("  curtidos   alta ", " Alpaca  touch ", null, null);

        assertFalse(alta.yaExistia());
        FichaPiel ficha = alta.ficha();
        assertEquals("ALPACA TOUCH", ficha.getNombre(), "en mayúsculas y con un solo espacio");
        assertEquals("Vaca", ficha.getAnimal(), "el animal por defecto");
        assertEquals("", ficha.getTipo());
        assertNull(ficha.getStockService(), "no se sabe: ni sí ni no");
        assertEquals("CURTIDOS ALTA", biblioteca.proveedor(ficha.getProveedorId()).orElseThrow().getNombre(),
                "el proveedor se crea con el alta");
    }

    @Test
    void elMismoProveedorConElMismoNombreEsLaMismaPielSeEscribaComoSeEscriba() {
        Alta primera = biblioteca.crear("Curtidos Repe", "Napa Lisa", "Cabra", "Napa");
        Alta segunda = biblioteca.crear("CURTIDOS REPE", "napa  lisa", "Vaca", "Serraje");
        Alta otroProveedor = biblioteca.crear("Curtidos Repe Dos", "Napa Lisa", null, null);

        assertTrue(segunda.yaExistia());
        assertEquals(primera.ficha().getId(), segunda.ficha().getId());
        assertEquals("Cabra", biblioteca.ficha(primera.ficha().getId()).orElseThrow().getAnimal(),
                "el alta repetida no cambia nada de la que había");
        assertNotEquals(primera.ficha().getId(), otroProveedor.ficha().getId(),
                "con otro proveedor es otra piel aunque se llame igual");
    }

    @Test
    void animalYTipoSonListasCerradas() {
        Long id = biblioteca.crear("Curtidos Listas", "Piel Listas", null, null).ficha().getId();

        assertEquals("Serraje de forro", biblioteca.guardarCampo(id, "tipo", "serraje de FORRO").getTipo(),
                "se guarda escrito como en la lista");
        assertEquals("Cabra", biblioteca.guardarCampo(id, "animal", "cabra").getAnimal());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> biblioteca.guardarCampo(id, "tipo", "Terciopelo"));
        assertTrue(error.getMessage().contains("Terciopelo"));
        assertTrue(error.getMessage().contains("Napa"), "dice cuáles sí valen");
        assertThrows(IllegalArgumentException.class, () -> biblioteca.guardarCampo(id, "animal", "Cocodrilo"),
                "solo hay dos animales en la lista");
        assertEquals("Serraje de forro", biblioteca.ficha(id).orElseThrow().getTipo(), "lo rechazado no cambia nada");

        assertEquals("", biblioteca.guardarCampo(id, "tipo", "").getTipo(), "vacío es no se sabe, y siempre vale");
    }

    @Test
    void elGruesoYLaSuperficieSeTecleanComoUnValorOUnRango() {
        Long id = biblioteca.crear("Curtidos Medidas", "Piel Medidas", null, null).ficha().getId();

        FichaPiel ficha = biblioteca.guardarCampo(id, "grueso", "1.1 - 1.3 mm");
        assertEquals(1.1, ficha.getGruesoMinMm());
        assertEquals(1.3, ficha.getGruesoMaxMm());
        ficha = biblioteca.guardarCampo(id, "superficie", "4,5");
        assertEquals(4.5, ficha.getSuperficieMinM2());
        assertEquals(4.5, ficha.getSuperficieMaxM2());

        assertThrows(IllegalArgumentException.class, () -> biblioteca.guardarCampo(id, "grueso", "fino"));
        assertEquals(1.1, biblioteca.ficha(id).orElseThrow().getGruesoMinMm(), "lo que no vale no borra lo que había");

        ficha = biblioteca.guardarCampo(id, "grueso", "");
        assertNull(ficha.getGruesoMinMm());
        assertNull(ficha.getGruesoMaxMm());
    }

    @Test
    void elFormularioDeLaFichaGuardaTodoONada() {
        Long id = biblioteca.crear("Curtidos Formulario", "Piel Formulario", null, "Napa").ficha().getId();

        assertThrows(IllegalArgumentException.class, () -> biblioteca.guardar(id, Map.of(
                "comentarios", "Buena mano", "calidad", "9")), "la calidad va de 1 a 5");
        FichaPiel ficha = biblioteca.ficha(id).orElseThrow();
        assertEquals("", ficha.getComentarios(), "con un campo mal no se guarda ninguno");

        biblioteca.guardar(id, Map.of("comentarios", " Buena mano, algo irregular ", "calidad", "4",
                "stockService", "true", "plazoEntregaDias", "21", "fechaPrecios", "2026-11-03",
                "refProveedor", " art. 1203 "));
        ficha = biblioteca.ficha(id).orElseThrow();
        assertEquals("Buena mano, algo irregular", ficha.getComentarios(), "los comentarios se guardan como se escriben");
        assertEquals(4, ficha.getCalidad());
        assertEquals(Boolean.TRUE, ficha.getStockService());
        assertEquals(21, ficha.getPlazoEntregaDias());
        assertEquals(LocalDate.of(2026, 11, 3), ficha.getFechaPrecios());
        assertEquals("ART. 1203", ficha.getRefProveedor());
    }

    @Test
    void cambiarElNombreAUnoQueElProveedorYaTieneSeRechaza() {
        biblioteca.crear("Curtidos Nombres", "Piel Uno", null, null);
        Long dos = biblioteca.crear("Curtidos Nombres", "Piel Dos", null, null).ficha().getId();

        assertThrows(IllegalArgumentException.class, () -> biblioteca.guardarCampo(dos, "nombre", "piel uno"));
        assertThrows(IllegalArgumentException.class, () -> biblioteca.guardarCampo(dos, "nombre", "  "));
        assertEquals("PIEL DOS", biblioteca.ficha(dos).orElseThrow().getNombre());
    }

    // --- Precios ---

    @Test
    void losTramosSeSustituyenEnterosYVanDelMinimoMasBajoAlMasAlto() {
        Long id = biblioteca.crear("Curtidos Precios", "Piel Precios", null, null).ficha().getId();

        biblioteca.guardarPrecios(id, List.of(new Tramo(200.0, 38.0), new Tramo(50.0, 42.5),
                new Tramo(null, null), new Tramo(null, 48.0), new Tramo(1000.0, null)));

        List<PrecioPiel> tramos = biblioteca.preciosDe(id);
        assertEquals(4, tramos.size(), "la fila sin mínimo ni precio no se rellenó: se descarta");
        assertNull(tramos.get(0).getMoqM2(), "el precio sin mínimo (stock service) va el primero");
        assertEquals(50.0, tramos.get(1).getMoqM2());
        assertEquals(42.5, tramos.get(1).getPrecioEurM2());
        assertNull(tramos.get(3).getPrecioEurM2(), "un mínimo del que aún no se sabe el precio se guarda");

        Fila fila = biblioteca.buscar(new Filtro("PIEL PRECIOS", null, null, null, null, null, null, null)).get(0);
        assertEquals(List.of("sin mínimo → 48 €", "50 m² → 42,5 €", "200 m² → 38 €", "1000 m² → ?"), fila.tramosTexto());

        biblioteca.guardarPrecios(id, List.of(new Tramo(100.0, 40.0)));
        assertEquals(1, biblioteca.preciosDe(id).size(), "una oferta nueva sustituye a la anterior entera");
    }

    // --- Enlaces con las pieles de cliente ---

    @Test
    void unaPielDeClienteQueSeLlamaIgualSeProponePeroNoSeEnlazaSola() {
        Long ficha = biblioteca.crear("Curtidos Propuesta", "Vachette Propuesta", null, null).ficha().getId();
        memoriaPieles.guardarPiel(CLIENTE, "BPP01", "vachette  propuesta");
        memoriaPieles.guardarPiel(CLIENTE, "BPP02", "Otra Cosa");
        memoriaPieles.guardarPiel(CLIENTE, "BPP03", "");

        assertEquals(List.of(ficha), propuestasDe("BPP01"), "se llama igual, sin mirar mayúsculas ni espacios");
        assertEquals(List.of(), propuestasDe("BPP02"));
        assertEquals(List.of(), propuestasDe("BPP03"), "una piel sin nombre no se parece a ninguna");
        assertNull(piel("BPP01").getFichaPielId(), "proponer no enlaza: lo acepta una persona");

        biblioteca.enlazar(CLIENTE, "bpp01", ficha);

        assertEquals(ficha, piel("BPP01").getFichaPielId());
        assertTrue(biblioteca.sinFicha().stream().noneMatch(suelta -> suelta.piel().getRefPiel().equals("BPP01")),
                "enlazada, ya no sale entre las que no tienen ficha");
    }

    @Test
    void enlazarRellenaElNombreVacioYNoPisaElQueYaHabia() {
        Long ficha = biblioteca.crear("Curtidos Enlace", "Serraje Enlace", null, null).ficha().getId();
        memoriaPieles.guardarPiel(CLIENTE, "BPE01", "");
        memoriaPieles.guardarPiel(CLIENTE, "BPE02", "SERRAJE ALABAMA");

        biblioteca.enlazar(CLIENTE, "BPE01", ficha);
        biblioteca.enlazar(CLIENTE, "BPE02", ficha);
        biblioteca.enlazar(CLIENTE, "BPE03", ficha);

        assertEquals("SERRAJE ENLACE", piel("BPE01").getNombre(), "sin nombre, toma el de la ficha");
        assertEquals("SERRAJE ALABAMA", piel("BPE02").getNombre(), "el que alguien tecleó no se pisa");
        assertEquals("SERRAJE ENLACE", piel("BPE03").getNombre(), "una referencia que no existía se da de alta");
        assertEquals(List.of("BPE01", "BPE02", "BPE03"),
                biblioteca.enlazadas(ficha).stream().map(Piel::getRefPiel).toList());

        biblioteca.desenlazar(CLIENTE, "BPE02");
        assertNull(piel("BPE02").getFichaPielId());
        assertEquals("SERRAJE ALABAMA", piel("BPE02").getNombre(), "desenlazar no toca la piel del cliente");
    }

    @Test
    void borrarLaFichaDejaLasPielesDeClienteComoEstabanAntesDeEnlazarlas() {
        Long ficha = biblioteca.crear("Curtidos Borrar", "Piel Borrar", null, null).ficha().getId();
        memoriaPieles.guardarPiel(CLIENTE, "BPB01", "PIEL DEL CLIENTE");
        biblioteca.enlazar(CLIENTE, "BPB01", ficha);
        biblioteca.guardarPrecios(ficha, List.of(new Tramo(50.0, 40.0)));
        biblioteca.guardarFoto(ficha, "foto.jpg", FotosDePrueba.jpeg(40, 30, Color.GRAY));
        biblioteca.apuntar(ficha, LocalDate.of(2026, 3, 12), "APC", TipoEventoPiel.MOSTRADA, null, null);

        biblioteca.borrar(ficha);

        assertTrue(biblioteca.ficha(ficha).isEmpty());
        assertTrue(biblioteca.preciosDe(ficha).isEmpty());
        assertTrue(biblioteca.fotosDe(ficha).isEmpty());
        assertTrue(biblioteca.recorrido(ficha).isEmpty());
        Piel piel = piel("BPB01");
        assertEquals("PIEL DEL CLIENTE", piel.getNombre(), "la piel del cliente sigue: la usan el corte y el catálogo");
        assertNull(piel.getFichaPielId());
    }

    // --- Recorrido ---

    @Test
    void elRecorridoJuntaLoApuntadoAManoYLoQueSabeElCatalogo() {
        Long ficha = biblioteca.crear("Curtidos Recorrido", "Piel Recorrido", null, null).ficha().getId();
        // Un artículo del catálogo que lleva la piel BPR01, en una temporada.
        Modelo modelo = catalogo.asegurarModelo(CLIENTE, "BPM901", "");
        biblioteca.enlazar(CLIENTE, "BPR01", ficha);
        articulos.save(new Articulo(CLIENTE, "BPM901-BPR01", modelo.getId(), "BPR01"));
        TemporadaGuardada temporada = temporadas.save(new TemporadaGuardada(CLIENTE, "BP-RECORRIDO 27"));
        catalogo.vincularTemporada(modelo.getId(), temporada.getId());
        // Y dos apuntes a mano, de después.
        LocalDate manana = LocalDate.now().plusDays(1);
        biblioteca.apuntar(ficha, manana.plusDays(30), "apc", TipoEventoPiel.PROTOTIPO, "proto b", null);
        biblioteca.apuntar(ficha, manana, "APC", TipoEventoPiel.MOSTRADA, null, " Les gustó el tacto ");

        List<Paso> recorrido = biblioteca.recorrido(ficha);

        assertEquals(3, recorrido.size());
        Paso delCatalogo = recorrido.get(0);
        assertFalse(delCatalogo.fechaExacta(), "del catálogo se sabe la temporada, no el día");
        assertEquals(CLIENTE, delCatalogo.cliente());
        assertEquals("Producción · BP-RECORRIDO 27", delCatalogo.que());
        assertEquals("BPM901-BPR01", delCatalogo.detalle());
        assertNull(delCatalogo.eventoId(), "no es un apunte: no se puede borrar desde aquí");

        Paso mostrada = recorrido.get(1);
        assertTrue(mostrada.fechaExacta());
        assertEquals(manana, mostrada.fecha());
        assertEquals("APC", mostrada.cliente());
        assertEquals("Mostrada", mostrada.que());
        assertEquals("Les gustó el tacto", mostrada.nota());

        Paso prototipo = recorrido.get(2);
        assertEquals("Prototipo", prototipo.que());
        assertEquals("PROTO B", prototipo.detalle(), "el modelo es texto, en mayúsculas");

        biblioteca.borrarEvento(mostrada.eventoId());
        assertEquals(2, biblioteca.recorrido(ficha).size());
    }

    @Test
    void unApunteNecesitaClienteYQuePaso() {
        Long ficha = biblioteca.crear("Curtidos Apunte", "Piel Apunte", null, null).ficha().getId();

        assertThrows(IllegalArgumentException.class,
                () -> biblioteca.apuntar(ficha, LocalDate.now(), " ", TipoEventoPiel.BUSCADA, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> biblioteca.apuntar(ficha, LocalDate.now(), "AMI", null, null, null));
        assertEquals(LocalDate.now(),
                biblioteca.apuntar(ficha, null, "AMI", TipoEventoPiel.BUSCADA, null, null).getFecha(),
                "sin fecha es hoy");
    }

    // --- Duplicar, proveedores, fotos y filtros ---

    @Test
    void duplicarCopiaLosDatosDePartidaYLosTramosPeroNoLoQueEsDeEsaPiel() {
        Long id = biblioteca.crear("Curtidos Duplicar", "Piel Original", "Cabra", "Napa").ficha().getId();
        biblioteca.guardar(id, Map.of("grueso", "0,8-1", "stockService", "true", "plazoEntregaDias", "30",
                "calidad", "5", "comentarios", "Solo de esta"));
        biblioteca.guardarPrecios(id, List.of(new Tramo(50.0, 42.0)));
        biblioteca.guardarFoto(id, "foto.jpg", FotosDePrueba.jpeg(40, 30, Color.GRAY));

        FichaPiel copia = biblioteca.duplicar(id, "piel copia");

        assertEquals("PIEL COPIA", copia.getNombre());
        assertEquals(biblioteca.ficha(id).orElseThrow().getProveedorId(), copia.getProveedorId());
        assertEquals("Cabra", copia.getAnimal());
        assertEquals("Napa", copia.getTipo());
        assertEquals(0.8, copia.getGruesoMinMm());
        assertEquals(Boolean.TRUE, copia.getStockService());
        assertEquals(30, copia.getPlazoEntregaDias());
        assertEquals(1, biblioteca.preciosDe(copia.getId()).size());
        assertNull(copia.getCalidad(), "la calidad es de la otra piel");
        assertEquals("", copia.getComentarios());
        assertTrue(biblioteca.fotosDe(copia.getId()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> biblioteca.duplicar(id, "Piel Original"));
    }

    @Test
    void laValoracionEsDelProveedorVaDe1A5YUnoConPielesNoSeBorra() {
        Proveedor proveedor = biblioteca.asegurarProveedor("Curtidos Valorados");
        Long id = proveedor.getId();
        assertNull(proveedor.getFechaValoracion());

        biblioteca.guardarCampoProveedor(id, "responsabilidad", "4");
        biblioteca.guardarCampoProveedor(id, "entregaATiempo", "2");
        biblioteca.guardarCampoProveedor(id, "notaValoracion", "Tarda en contestar");
        biblioteca.guardarCampoProveedor(id, "pais", " Italia ");
        assertThrows(IllegalArgumentException.class, () -> biblioteca.guardarCampoProveedor(id, "responsabilidad", "6"));
        assertThrows(IllegalArgumentException.class, () -> biblioteca.guardarCampoProveedor(id, "entregaATiempo", "bien"));
        assertThrows(IllegalArgumentException.class, () -> biblioteca.guardarCampoProveedor(id, "nombre", ""));

        Proveedor guardado = biblioteca.proveedor(id).orElseThrow();
        assertEquals(4, guardado.getResponsabilidad());
        assertEquals(2, guardado.getEntregaATiempo());
        assertEquals("Tarda en contestar", guardado.getNotaValoracion());
        assertEquals("Italia", guardado.getPais());
        assertTrue(guardado.getFechaValoracion() != null, "la valoración lleva su fecha");

        biblioteca.crear("Curtidos Valorados", "Piel Valorada", null, null);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> biblioteca.borrarProveedor(id));
        assertTrue(error.getMessage().contains("1 piel"));
        assertTrue(biblioteca.proveedor(id).isPresent());

        Long vacio = biblioteca.asegurarProveedor("Curtidos Sin Pieles").getId();
        biblioteca.borrarProveedor(vacio);
        assertTrue(biblioteca.proveedor(vacio).isEmpty());
    }

    @Test
    void lasFotosTienenNombreUnicoLaPrimeraEsLaPrincipalYAlBorrarlaPasaALaSiguiente() {
        Long id = biblioteca.crear("Curtidos Fotos", "Piel Fotos", null, null).ficha().getId();
        byte[] jpeg = FotosDePrueba.jpeg(40, 30, Color.GRAY);

        ResumenFotoPiel primera = biblioteca.guardarFoto(id, "image.jpg", jpeg);
        ResumenFotoPiel segunda = biblioteca.guardarFoto(id, "image.jpg", jpeg);

        assertTrue(primera.principal());
        assertFalse(segunda.principal());
        assertEquals("image (2).jpg", segunda.nombreFichero(), "la cámara del móvil llama igual a todas");
        assertEquals(primera.id(), biblioteca.principalesDe(List.of(id)).get(id));

        biblioteca.marcarPrincipal(segunda.id());
        assertEquals(segunda.id(), biblioteca.principalesDe(List.of(id)).get(id));

        biblioteca.borrarFoto(segunda.id());
        List<ResumenFotoPiel> quedan = biblioteca.fotosDe(id);
        assertEquals(1, quedan.size());
        assertTrue(quedan.get(0).principal(), "una piel con fotos siempre tiene una principal");
    }

    @Test
    void losFiltrosDeLaTabla() {
        Long proveedor = biblioteca.asegurarProveedor("Curtidos Filtro").getId();
        Long fina = biblioteca.crear("Curtidos Filtro", "Filtro Fina", "Cabra", "Napa").ficha().getId();
        Long gruesa = biblioteca.crear("Curtidos Filtro", "Filtro Gruesa", "Vaca", "Serraje").ficha().getId();
        biblioteca.guardar(fina, Map.of("grueso", "0,6-0,8", "stockService", "true"));
        biblioteca.guardar(gruesa, Map.of("grueso", "1,4-1,6", "stockService", "false", "refProveedor", "FG-77"));
        biblioteca.enlazar(CLIENTE, "BPF01", gruesa);

        assertEquals(List.of(fina, gruesa), ids(new Filtro(null, proveedor, null, null, null, null, null, null)),
                "por nombre dentro del proveedor");
        assertEquals(List.of(fina), ids(new Filtro(null, proveedor, null, "Cabra", null, null, null, null)));
        assertEquals(List.of(gruesa), ids(new Filtro(null, proveedor, null, null, "serraje", null, null, null)));
        assertEquals(List.of(gruesa), ids(new Filtro(null, proveedor, null, null, null, 1.5, 1.5, null)));
        assertEquals(List.of(fina), ids(new Filtro(null, proveedor, null, null, null, null, 1.0, null)));
        assertEquals(List.of(fina), ids(new Filtro(null, proveedor, null, null, null, null, null, true)));
        assertEquals(List.of(gruesa), ids(new Filtro(null, proveedor, CLIENTE, null, null, null, null, null)),
                "las que usa ese cliente");
        assertEquals(List.of(gruesa), ids(new Filtro("fg-77", proveedor, null, null, null, null, null, null)),
                "por la referencia del proveedor");
        assertEquals(List.of(gruesa), ids(new Filtro("bpf01", proveedor, null, null, null, null, null, null)),
                "por la referencia del cliente");
        assertEquals(List.of(fina, gruesa), ids(new Filtro("curtidos filtro", null, null, null, null, null, null, null)),
                "por el nombre del proveedor");
    }

    // --- Ayudas ---

    private List<Long> ids(Filtro filtro) {
        return biblioteca.buscar(filtro).stream().map(fila -> fila.ficha().getId()).toList();
    }

    private List<Long> propuestasDe(String refPiel) {
        return biblioteca.sinFicha().stream()
                .filter(suelta -> suelta.piel().getCliente().equals(CLIENTE) && suelta.piel().getRefPiel().equals(refPiel))
                .findFirst().map(SinFicha::propuestas).orElseThrow()
                .stream().map(BibliotecaPieles.Propuesta::fichaId).toList();
    }

    private Piel piel(String refPiel) {
        return pieles.findById(new PielId(CLIENTE, refPiel)).orElseThrow();
    }
}
