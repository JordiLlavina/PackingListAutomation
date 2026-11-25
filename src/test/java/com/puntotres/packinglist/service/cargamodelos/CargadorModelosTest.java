package com.puntotres.packinglist.service.cargamodelos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.modelos.service.Documentos;
import com.puntotres.packinglist.persistence.Articulo;
import com.puntotres.packinglist.persistence.ArticuloColor;
import com.puntotres.packinglist.persistence.Etapa;
import com.puntotres.packinglist.persistence.MemoriaPieles;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ResumenFoto;
import com.puntotres.packinglist.persistence.TablaColores;
import com.puntotres.packinglist.persistence.TipoArticulo;
import com.puntotres.packinglist.service.cargamodelos.CargadorModelos.CargaFallida;
import com.puntotres.packinglist.service.cargamodelos.CargadorModelos.ResumenCarga;
import com.puntotres.packinglist.service.cargamodelos.EdicionModelo.EdicionColor;
import com.puntotres.packinglist.service.cargamodelos.EdicionModelo.EdicionFoto;
import com.puntotres.packinglist.service.cargamodelos.EdicionModelo.EdicionPiel;
import com.puntotres.packinglist.service.cargamodelos.LecturaOrigen.ArticuloLeido;
import com.puntotres.packinglist.service.cargamodelos.LecturaOrigen.ColorLeido;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.EstadoModelo;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.FotoEnCarga;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoModelo;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;
import com.puntotres.packinglist.service.corte.LectorFotos;

/**
 * La carga de modelos de punta a punta, contra el catálogo de verdad: qué
 * entra, qué solo rellena un hueco, qué pisa y qué pasa cuando algo falla.
 * Cada test usa sus propias referencias (CM…): la base es la de toda la suite.
 */
@SpringBootTest
class CargadorModelosTest {

    @Autowired
    private ConstructorLote constructor;
    @Autowired
    private CargadorModelos cargador;
    @Autowired
    private CatalogoModelos catalogo;
    @Autowired
    private FotosCatalogo fotosCatalogo;
    @Autowired
    private Documentos documentos;
    @Autowired
    private MemoriaPieles memoriaPieles;
    @Autowired
    private TablaColores tablaColores;

    @TempDir
    Path directorio;

    private static EdicionModelo soloModelo(String codigo, String nombre) {
        return new EdicionModelo(null, codigo, null, null, nombre, null, null, null, null);
    }

    private static EdicionPiel piel(String referencia, String nombrePiel, String forro, EdicionColor... colores) {
        return new EdicionPiel(referencia, nombrePiel, forro, null, null, null, null,
                colores.length == 0 ? null : List.of(colores));
    }

    private static ArticuloLeido leido(String referencia, String modelo, String refPiel, String nombreModelo,
                                       String nombrePiel, String forro, ColorLeido... colores) {
        return new ArticuloLeido(referencia, modelo, refPiel, nombreModelo, nombrePiel, forro, List.of(),
                List.of(colores));
    }

    private LoteCarga deExcel(OrigenCarga origen, ArticuloLeido... articulos) throws Exception {
        return constructor.deExcel(origen, "AMI", null, new LecturaOrigen(List.of(articulos), Map.of(), List.of()),
                CarpetaModelos.Lectura.vacia(), directorio);
    }

    private static byte[] jpeg(Color color) throws Exception {
        BufferedImage imagen = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        var lienzo = imagen.createGraphics();
        lienzo.setColor(color);
        lienzo.fillRect(0, 0, 40, 30);
        lienzo.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "jpg", salida);
        return salida.toByteArray();
    }

    private static LectorFotos.Fichero fichero(String ruta, byte[] contenido) {
        return new LectorFotos.Fichero(ruta, () -> new ByteArrayInputStream(contenido));
    }

    private static void esperarFotos(LoteCarga lote) throws InterruptedException {
        long limite = System.currentTimeMillis() + 30_000;
        while (!lote.fotosListas()) {
            assertTrue(System.currentTimeMillis() < limite, "las fotos no han acabado en 30 s");
            Thread.sleep(50);
        }
    }

    @Test
    void unModeloNuevoEscritoAManoEntraConSuPielSuColorYSuEtapa() {
        LoteCarga lote = constructor.manual("AMI", directorio);
        ModeloEnCarga modelo = lote.modelos().get(0);
        assertFalse(constructor.marcar(lote, modelo, EstadoModelo.ACEPTADO), "sin referencia ni nombre no se acepta");

        constructor.aplicar(lote, modelo, new EdicionModelo("AMI", "cma100", null, null, "Bolso de prueba", null,
                null, List.of(piel("cma100.al9001", "napa", "badana",
                new EdicionColor("9001", "Rojo", null, "negro", null))), null));
        assertTrue(constructor.faltan(lote, modelo).isEmpty());
        assertTrue(constructor.marcar(lote, modelo, EstadoModelo.ACEPTADO));

        ResumenCarga resumen = cargador.cargar(lote);

        assertEquals(1, resumen.modelosNuevos());
        assertEquals(1, resumen.pieles());
        assertEquals(1, resumen.colores());
        Modelo guardado = catalogo.modelo("AMI", "CMA100").orElseThrow();
        assertEquals("Bolso de prueba", guardado.getNombre());
        assertEquals(Etapa.PROTOTIPO, guardado.getEtapa(), "lo escrito a mano nace prototipo");
        Articulo articulo = catalogo.articulo("AMI", "CMA100.AL9001").orElseThrow();
        assertEquals(guardado.getId(), articulo.getModeloId());
        assertEquals("BADANA", articulo.getForro());
        assertEquals("NAPA", memoriaPieles.pielesDe("AMI").stream()
                .filter(piel -> piel.getRefPiel().equals("AL9001")).findFirst().orElseThrow().getNombre());
        ArticuloColor color = catalogo.coloresDe("AMI", "CMA100.AL9001").get(0);
        assertEquals("9001", color.getCodigoColor());
        assertEquals("ROJO", color.getColorPiel(), "la piel principal arranca con el color del bolso");
        assertEquals("NEGRO", color.getColorForro());
        assertEquals("Rojo", tablaColores.buscar("AMI", "9001").orElseThrow().getDescripcion());
        assertTrue(lote.modelos().isEmpty(), "lo cargado sale del lote");
    }

    @Test
    void laEtapaDeUnModeloNuevoLaPoneElOrigen() throws Exception {
        LoteCarga lote = deExcel(OrigenCarga.SMS, leido("CMS110.AL9002", "CMS110", "AL9002", "Muestra", "", ""));
        constructor.aceptarCompletos(lote);

        cargador.cargar(lote);

        assertEquals(Etapa.SMS, catalogo.modelo("AMI", "CMS110").orElseThrow().getEtapa());
    }

    @Test
    void enUnModeloQueYaExisteLoDelOrigenSoloRellenaHuecos() throws Exception {
        catalogo.asegurarArticulo("AMI", "CME200.AL9003");
        catalogo.guardarCampoModelo("AMI", "CME200", CampoModelo.NOMBRE, "NOMBRE GUARDADO");
        catalogo.guardarCampo("AMI", "CME200.AL9003", CampoArticulo.FORRO, "BADANA", false);

        LoteCarga lote = deExcel(OrigenCarga.SMS, leido("CME200.AL9003", "CME200", "AL9003", "Nombre del excel",
                "NAPA DEL EXCEL", "FORRO DEL EXCEL", new ColorLeido("9003", "Verde", "VERDE", "", List.of())));
        ModeloEnCarga modelo = lote.modelos().get(0);

        assertTrue(modelo.existe());
        assertEquals("NOMBRE GUARDADO", modelo.nombre, "lo guardado manda sobre lo leído");
        assertEquals("BADANA", modelo.pieles.get(0).forro);
        assertEquals("NAPA DEL EXCEL", modelo.pieles.get(0).nombrePiel, "el hueco sí se rellena");
        assertEquals(Etapa.PRODUCCION, modelo.etapa, "la etapa de un modelo que existe no la cambia el origen");
        assertFalse(constructor.novedades(modelo).isEmpty(), "trae un color y un nombre de piel: no es gris");

        constructor.aceptarCompletos(lote);
        ResumenCarga resumen = cargador.cargar(lote);

        assertEquals(0, resumen.modelosNuevos());
        assertEquals(1, resumen.modelosCompletados());
        Modelo guardado = catalogo.modelo("AMI", "CME200").orElseThrow();
        assertEquals("NOMBRE GUARDADO", guardado.getNombre());
        assertEquals(Etapa.PRODUCCION, guardado.getEtapa());
        assertEquals("BADANA", catalogo.articulo("AMI", "CME200.AL9003").orElseThrow().getForro());
        assertEquals("NAPA DEL EXCEL", memoriaPieles.pielesDe("AMI").stream()
                .filter(piel -> piel.getRefPiel().equals("AL9003")).findFirst().orElseThrow().getNombre());
        assertEquals(1, catalogo.coloresDe("AMI", "CME200.AL9003").size());
    }

    @Test
    void loQueSeTocaEnElPanelSiPisaLoGuardado() throws Exception {
        catalogo.asegurarArticulo("AMI", "CMT210.AL9004");
        catalogo.guardarCampoModelo("AMI", "CMT210", CampoModelo.NOMBRE, "VIEJO");
        catalogo.guardarCampo("AMI", "CMT210.AL9004", CampoArticulo.FORRO, "BADANA", false);
        memoriaPieles.guardarPiel("AMI", "AL9004", "NAPA VIEJA");

        LoteCarga lote = deExcel(OrigenCarga.TEMPORADA, leido("CMT210.AL9004", "CMT210", "AL9004", "", "", ""));
        ModeloEnCarga modelo = lote.modelos().get(0);
        constructor.aplicar(lote, modelo, new EdicionModelo(null, null, null, null, "Nombre corregido", "CINTURON",
                "SMS", List.of(piel("CMT210.AL9004", "napa nueva", "serraje")), null));
        constructor.aceptarCompletos(lote);

        cargador.cargar(lote);

        Modelo guardado = catalogo.modelo("AMI", "CMT210").orElseThrow();
        assertEquals("Nombre corregido", guardado.getNombre());
        assertEquals(TipoArticulo.CINTURON, guardado.getTipo());
        assertEquals(Etapa.SMS, guardado.getEtapa(), "la etapa cambia solo si se cambia en el panel");
        assertEquals("SERRAJE", catalogo.articulo("AMI", "CMT210.AL9004").orElseThrow().getForro());
        assertEquals("NAPA NUEVA", memoriaPieles.pielesDe("AMI").stream()
                .filter(piel -> piel.getRefPiel().equals("AL9004")).findFirst().orElseThrow().getNombre());
    }

    @Test
    void unModeloQueYaExisteYNoTraeNadaNuevoNoTieneNovedades() throws Exception {
        catalogo.asegurarArticulo("AMI", "CMG220.AL9005");
        catalogo.guardarCampoModelo("AMI", "CMG220", CampoModelo.NOMBRE, "YA ESTABA");

        LoteCarga lote = deExcel(OrigenCarga.TEMPORADA, leido("CMG220.AL9005", "CMG220", "AL9005", "", "", ""));

        ModeloEnCarga modelo = lote.modelos().get(0);
        assertTrue(modelo.existe());
        assertTrue(constructor.novedades(modelo).isEmpty(), "sale en gris y al final");
        assertEquals(0, constructor.aceptarCompletos(lote), "aceptar los completos no arrastra a los que no traen nada");
        assertTrue(constructor.faltan(lote, modelo).isEmpty(), "pero se puede aceptar igualmente");
    }

    @Test
    void lasFotosEntranEnElOrdenDeLaTiraYElPdfComoDocumentoDelModelo() throws Exception {
        CarpetaModelos.Lectura carpeta = CarpetaModelos.leer(List.of(
                fichero("SIN-TEMPORADA/CMF300 bolso con fotos/b.jpg", jpeg(Color.BLUE)),
                fichero("SIN-TEMPORADA/CMF300 bolso con fotos/a.jpg", jpeg(Color.RED)),
                fichero("SIN-TEMPORADA/CMF300 bolso con fotos/principal.jpg", jpeg(Color.GREEN)),
                fichero("SIN-TEMPORADA/CMF300 bolso con fotos/ficha.pdf", "%PDF-1.4 falso".getBytes())), directorio);
        LoteCarga lote = constructor.deGrupo("AMI", carpeta, directorio);
        esperarFotos(lote);

        ModeloEnCarga modelo = lote.modelos().get(0);
        assertEquals("CMF300", modelo.codigo);
        assertEquals("bolso con fotos", modelo.nombre, "el resto del nombre de la carpeta es el nombre de partida");
        assertEquals(Etapa.PROTOTIPO, modelo.etapa);
        assertEquals(List.of("principal.jpg", "a.jpg", "b.jpg"),
                modelo.fotos.stream().map(foto -> foto.nombre).toList(), "la que se llama principal va delante");
        assertTrue(modelo.fotos.stream().allMatch(foto -> foto.seleccionada), "las subidas salen seleccionadas");
        assertEquals(1, modelo.fichas.size());
        assertTrue(lote.avisos().stream().anyMatch(aviso -> aviso.contains("SIN-TEMPORADA")),
                "la raíz no es una temporada guardada: se avisa");

        // El usuario arrastra b delante y deselecciona a.
        FotoEnCarga b = modelo.fotos.get(2);
        constructor.aplicar(lote, modelo, new EdicionModelo(null, null, null, null, null, null, null, null, List.of(
                new EdicionFoto(b.id, true),
                new EdicionFoto(modelo.fotos.get(0).id, true),
                new EdicionFoto(modelo.fotos.get(1).id, false))));
        constructor.aceptarCompletos(lote);
        ResumenCarga resumen = cargador.cargar(lote);

        assertEquals(2, resumen.fotos());
        assertEquals(1, resumen.fichas());
        Long modeloId = catalogo.modelo("AMI", "CMF300").orElseThrow().getId();
        List<ResumenFoto> fotos = fotosCatalogo.de(modeloId);
        assertEquals(List.of("b.jpg", "principal.jpg"), fotos.stream().map(ResumenFoto::nombreFichero).toList());
        assertTrue(fotos.get(0).principal(), "la primera de la tira es la principal");
        assertEquals(1, documentos.de(modeloId).size());
        assertEquals("ficha.pdf", documentos.de(modeloId).get(0).nombre());
    }

    @Test
    void lasFotosGuardadasSalenEnLaTiraYLasNuevasVanDetras() throws Exception {
        Long modeloId = catalogo.asegurarModelo("AMI", "CMF310", "CON FOTO").getId();
        fotosCatalogo.guardar(modeloId, "vieja.jpg", jpeg(Color.BLACK));

        LoteCarga lote = constructor.deGrupo("AMI", CarpetaModelos.leer(List.of(
                fichero("X/CMF310/nueva.jpg", jpeg(Color.WHITE))), directorio), directorio);
        esperarFotos(lote);
        ModeloEnCarga modelo = lote.modelos().get(0);

        assertEquals(List.of("vieja.jpg", "nueva.jpg"), modelo.fotos.stream().map(foto -> foto.nombre).toList());
        assertEquals("CON FOTO", modelo.nombre);
        assertEquals(List.of("1 foto"), constructor.novedades(modelo));

        constructor.aceptarCompletos(lote);
        cargador.cargar(lote);

        List<ResumenFoto> fotos = fotosCatalogo.de(modeloId);
        assertEquals(List.of("vieja.jpg", "nueva.jpg"), fotos.stream().map(ResumenFoto::nombreFichero).toList());
        assertTrue(fotos.get(0).principal());
    }

    @Test
    void unaFotoQueNoSeDejaLeerNoSeCargaYLasDemasSi() throws Exception {
        LoteCarga lote = constructor.deGrupo("AMI", CarpetaModelos.leer(List.of(
                fichero("X/CMF320 con una rota/buena.jpg", jpeg(Color.RED)),
                fichero("X/CMF320 con una rota/rota.jpg", "esto no es una imagen".getBytes())), directorio),
                directorio);
        esperarFotos(lote);
        constructor.aceptarCompletos(lote);

        ResumenCarga resumen = cargador.cargar(lote);

        assertEquals(1, resumen.fotos());
        Long modeloId = catalogo.modelo("AMI", "CMF320").orElseThrow().getId();
        assertEquals(List.of("buena.jpg"), fotosCatalogo.de(modeloId).stream().map(ResumenFoto::nombreFichero).toList());
    }

    @Test
    void unaCarpetaQueNoEsDeNingunModeloDelExcelSeAvisaYNoCreaModelo() throws Exception {
        CarpetaModelos.Lectura carpeta = CarpetaModelos.leer(List.of(
                fichero("X/CMX400.AL9006/a.jpg", jpeg(Color.RED)),
                fichero("X/CMX499/b.jpg", jpeg(Color.RED))), directorio);

        LoteCarga lote = constructor.deExcel(OrigenCarga.PRODUCCION, "AMI", null, new LecturaOrigen(
                List.of(leido("CMX400.AL9006", "CMX400", "AL9006", "Con carpeta", "", "")), Map.of(), List.of()),
                carpeta, directorio);
        esperarFotos(lote);

        assertEquals(1, lote.modelos().size());
        assertEquals(1, lote.modelos().get(0).fotos.size(), "la carpeta con la referencia entera es del modelo");
        assertTrue(lote.avisos().stream().anyMatch(aviso -> aviso.contains("CMX499")));
    }

    @Test
    void dosModelosConLaMismaReferenciaNoSePuedenAceptarLosDos() {
        LoteCarga lote = constructor.manual("AMI", directorio);
        ModeloEnCarga primero = lote.modelos().get(0);
        ModeloEnCarga segundo = constructor.anadirModelo(lote, "AMI");
        constructor.aplicar(lote, primero, soloModelo("CMD500", "Uno"));
        constructor.aplicar(lote, segundo, soloModelo("CMD500", "Otro"));

        assertTrue(constructor.marcar(lote, primero, EstadoModelo.ACEPTADO));
        assertFalse(constructor.marcar(lote, segundo, EstadoModelo.ACEPTADO));
        assertTrue(constructor.faltan(lote, segundo).stream().anyMatch(falta -> falta.contains("Ya hay otro")));
    }

    @Test
    void siUnModeloFallaNoEntraNinguno() {
        LoteCarga lote = constructor.manual("AMI", directorio);
        ModeloEnCarga bueno = lote.modelos().get(0);
        ModeloEnCarga malo = constructor.anadirModelo(lote, "AMI");
        constructor.aplicar(lote, bueno, soloModelo("CMN510", "Bueno"));
        constructor.aplicar(lote, malo, soloModelo("CMN511", "Malo"));
        constructor.aceptarCompletos(lote);
        // Una temporada que ya no existe (alguien la ha borrado mientras se revisaba el lote).
        malo.temporadaId = 987_654L;

        CargaFallida fallo = assertThrows(CargaFallida.class, () -> cargador.cargar(lote));

        assertTrue(fallo.getMessage().contains("CMN511"), fallo.getMessage());
        assertTrue(catalogo.modelo("AMI", "CMN510").isEmpty(), "todo o nada: el bueno tampoco ha entrado");
        assertEquals(2, lote.modelos().size(), "y el lote sigue entero");
    }

    @Test
    void unModeloNuevoQueAlguienDaDeAltaMientrasTantoSeCompletaEnVezDeFallar() {
        LoteCarga lote = constructor.manual("AMI", directorio);
        ModeloEnCarga modelo = lote.modelos().get(0);
        constructor.aplicar(lote, modelo, soloModelo("CMR520", "Nombre del lote"));
        constructor.aceptarCompletos(lote);
        catalogo.asegurarModelo("AMI", "CMR520", "");

        ResumenCarga resumen = cargador.cargar(lote);

        assertEquals(0, resumen.modelosNuevos());
        assertEquals(1, resumen.modelosCompletados());
        assertEquals("Nombre del lote", catalogo.modelo("AMI", "CMR520").orElseThrow().getNombre());
    }

    @Test
    void alEscribirLaReferenciaDeUnModeloQueExisteElPanelTraeLoGuardado() {
        catalogo.asegurarArticulo("AMI", "CMC530.AL9007");
        catalogo.guardarCampoModelo("AMI", "CMC530", CampoModelo.NOMBRE, "GUARDADO");

        LoteCarga lote = constructor.manual("AMI", directorio);
        ModeloEnCarga modelo = lote.modelos().get(0);
        constructor.aplicar(lote, modelo, soloModelo("cmc530", null));

        assertTrue(modelo.existe());
        assertEquals("GUARDADO", modelo.nombre);
        assertEquals(1, modelo.pieles.size());
        assertTrue(modelo.pieles.get(0).existe);

        constructor.aplicar(lote, modelo, soloModelo("CMC531", null));
        assertFalse(modelo.existe(), "al cambiar la referencia deja de ser el que existía");
    }

    @Test
    void enUnGenericoElModeloEsSuArticuloYGuardaSuEmbalaje() {
        LoteCarga lote = constructor.manual("KAMI", directorio);
        ModeloEnCarga modelo = lote.modelos().get(0);
        constructor.aplicar(lote, modelo, soloModelo("CMK540", "Generico"));
        assertEquals(1, modelo.pieles.size(), "sin partir la referencia, el artículo es el modelo");
        constructor.aplicar(lote, modelo, new EdicionModelo(null, null, null, null, null, null, null, List.of(
                new EdicionPiel("CMK540", null, null, null, "60X40X40", "10", "4,5", null)), null));
        constructor.aceptarCompletos(lote);

        cargador.cargar(lote);

        Articulo articulo = catalogo.articulo("KAMI", "CMK540").orElseThrow();
        assertEquals("60x40x40", articulo.getMedidaCaja());
        assertEquals(10, articulo.getUnidadesPorCaja());
        assertEquals(4.5, articulo.getPesoNetoUnidadKg());
    }

    @Test
    void unaPielDeOtroModeloOUnNumeroMalEscritoNoDejanAceptar() {
        LoteCarga lote = constructor.manual("AMI", directorio);
        ModeloEnCarga modelo = lote.modelos().get(0);
        constructor.aplicar(lote, modelo, new EdicionModelo(null, "CMV550", null, null, "Con errores", null, null,
                List.of(new EdicionPiel("CMV999.AL9008", null, null, null, null, "diez", null, null)), null));

        List<String> faltan = constructor.faltan(lote, modelo);
        assertTrue(faltan.stream().anyMatch(falta -> falta.contains("no es del modelo")), faltan.toString());
        assertTrue(faltan.stream().anyMatch(falta -> falta.contains("unidades por caja")), faltan.toString());
    }

    @Test
    void unAceptadoQueDejaDeEstarCompletoVuelveAPendiente() {
        LoteCarga lote = constructor.manual("AMI", directorio);
        ModeloEnCarga modelo = lote.modelos().get(0);
        constructor.aplicar(lote, modelo, soloModelo("CMP560", "Completo"));
        constructor.marcar(lote, modelo, EstadoModelo.ACEPTADO);

        constructor.aplicar(lote, modelo, soloModelo("CMP560", ""));

        assertEquals(EstadoModelo.PENDIENTE, modelo.estado());
        assertThrows(IllegalArgumentException.class, () -> cargador.cargar(lote), "no hay nada aceptado que cargar");
    }
}
