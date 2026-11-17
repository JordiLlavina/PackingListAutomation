package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.persistence.FichaPiel;
import com.puntotres.packinglist.persistence.MemoriaPieles;
import com.puntotres.packinglist.persistence.PielId;
import com.puntotres.packinglist.persistence.PielRepository;
import com.puntotres.packinglist.persistence.PrecioPiel;
import com.puntotres.packinglist.persistence.ResumenDocumentoPiel;
import com.puntotres.packinglist.persistence.ResumenFotoPiel;
import com.puntotres.packinglist.persistence.TipoEventoPiel;
import com.puntotres.packinglist.service.pieles.BibliotecaPieles;
import com.puntotres.packinglist.service.pieles.BibliotecaPieles.Paso;
import com.puntotres.packinglist.service.pieles.BibliotecaPieles.Tramo;
import com.puntotres.packinglist.testutil.FotosDePrueba;

/**
 * Las pantallas de la biblioteca de pieles. Cada test usa su proveedor y sus
 * referencias, y las pieles de cliente van a PALOMA WOOL con referencias
 * propias (BPW...): la base de datos en memoria es la misma para toda la suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BibliotecaPielesControllerTest {

    private static final String CLIENTE = "PALOMA WOOL";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BibliotecaPieles biblioteca;

    @Autowired
    private MemoriaPieles memoriaPieles;

    @Autowired
    private PielRepository pieles;

    @Test
    void elMenuTieneLaTarjetaDeLaBiblioteca() throws Exception {
        mvc.perform(get("/menu"))
                .andExpect(content().string(containsString(
                        "<a class=\"tarjeta menu-tarjeta\" href=\"/biblioteca-pieles\">")))
                .andExpect(content().string(containsString("Biblioteca de pieles")));
    }

    @Test
    void elAltaPideLoMinimoLlevaALaFichaYApuntaParaQuienSeBusco() throws Exception {
        mvc.perform(post("/biblioteca-pieles/nueva")
                        .param("proveedor", "Web Alta").param("nombre", "Napa Web Alta")
                        .param("animal", "Cabra").param("tipo", "Napa").param("buscadaPara", "APC"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("mensaje", containsString("NAPA WEB ALTA")));

        FichaPiel ficha = unica("NAPA WEB ALTA");
        assertEquals("Cabra", ficha.getAnimal());
        assertEquals("Napa", ficha.getTipo());
        List<Paso> recorrido = biblioteca.recorrido(ficha.getId());
        assertEquals(1, recorrido.size());
        assertEquals("APC", recorrido.get(0).cliente());
        assertEquals("Buscada", recorrido.get(0).que());
        assertEquals(LocalDate.now(), recorrido.get(0).fecha());

        // La misma otra vez: lleva a la que había y no apunta nada más.
        mvc.perform(post("/biblioteca-pieles/nueva")
                        .param("proveedor", "web alta").param("nombre", "napa web alta").param("buscadaPara", "AMI"))
                .andExpect(redirectedUrl("/biblioteca-pieles/" + ficha.getId()))
                .andExpect(flash().attribute("mensaje", containsString("ya tenía")));
        assertEquals(1, biblioteca.recorrido(ficha.getId()).size());
    }

    @Test
    void unAltaSinNombreVuelveALaTablaConElMotivo() throws Exception {
        mvc.perform(post("/biblioteca-pieles/nueva").param("proveedor", "Web Sin Nombre").param("nombre", " "))
                .andExpect(redirectedUrl("/biblioteca-pieles"))
                .andExpect(flash().attribute("mensajeError", containsString("nombre")));
    }

    @Test
    void laTablaEnsenaLasPielesConFichaConSusCasillasYLosFiltros() throws Exception {
        Long id = biblioteca.crear("Web Tabla", "Serraje Web Tabla", "Vaca", "Serraje").ficha().getId();
        biblioteca.guardarCampo(id, "grueso", "1,1-1,3");
        biblioteca.guardarPrecios(id, List.of(new Tramo(50.0, 42.5)));
        biblioteca.enlazar(CLIENTE, "BPW10", id);

        mvc.perform(get("/biblioteca-pieles").param("q", "serraje web tabla"))
                .andExpect(status().isOk())
                .andExpect(view().name("biblioteca-pieles"))
                .andExpect(content().string(containsString(">SERRAJE WEB TABLA</a>")))
                .andExpect(content().string(containsString("WEB TABLA")))
                .andExpect(content().string(containsString("data-url=\"/biblioteca-pieles/" + id + "/campo\"")))
                .andExpect(content().string(containsString("value=\"1,1-1,3\"")))
                .andExpect(content().string(containsString("50 m² → 42,5 €")))
                .andExpect(content().string(containsString(">" + CLIENTE + " BPW10<")))
                // Las dos listas cerradas, tal como las dio el usuario.
                .andExpect(content().string(containsString("<option value=\"Vaca\"")))
                .andExpect(content().string(containsString("<option value=\"Cabra\"")))
                .andExpect(content().string(containsString("<option value=\"Serraje de forro\"")))
                .andExpect(content().string(containsString("Quitar filtros")));

        mvc.perform(get("/biblioteca-pieles").param("q", "serraje web tabla").param("grueso", "2"))
                .andExpect(content().string(not(containsString(">SERRAJE WEB TABLA</a>"))))
                .andExpect(content().string(containsString("Ninguna piel de la biblioteca pasa esos filtros")));

        mvc.perform(get("/biblioteca-pieles").param("grueso", "gordo"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Filtro de grueso")));
    }

    @Test
    void losTiposSalenEnOrdenAlfabetico() throws Exception {
        String html = mvc.perform(get("/biblioteca-pieles")).andReturn().getResponse().getContentAsString();
        String alta = html.substring(html.indexOf("name=\"tipo\""));

        int anterior = -1;
        for (String tipo : List.of("Animal print", "Cabra", "Cocodrilo", "Grabado", "Napa", "Nubuck", "Release",
                "Serpiente", "Serraje", "Serraje de forro")) {
            int posicion = alta.indexOf("<option value=\"" + tipo + "\"");
            assertTrue(posicion > anterior, tipo + " va detrás del anterior");
            anterior = posicion;
        }
    }

    @Test
    void unaCasillaSeGuardaPorJsonYDevuelveElValorComoHaQuedado() throws Exception {
        Long id = biblioteca.crear("Web Casilla", "Piel Web Casilla", null, null).ficha().getId();

        mvc.perform(post("/biblioteca-pieles/" + id + "/campo").param("campo", "grueso").param("valor", "1.1 - 1.3 mm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.valor").value("1,1-1,3"));
        mvc.perform(post("/biblioteca-pieles/" + id + "/campo").param("campo", "tipo").param("valor", "nubuck"))
                .andExpect(jsonPath("$.valor").value("Nubuck"));
        mvc.perform(post("/biblioteca-pieles/" + id + "/campo").param("campo", "stockService").param("valor", "true"))
                .andExpect(jsonPath("$.valor").value("true"));

        mvc.perform(post("/biblioteca-pieles/" + id + "/campo").param("campo", "tipo").param("valor", "Terciopelo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.mensaje").value(containsString("no está en la lista")));
        mvc.perform(post("/biblioteca-pieles/" + id + "/campo").param("campo", "calidad").param("valor", "7"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/biblioteca-pieles/999999/campo").param("campo", "grueso").param("valor", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("ya no existe")));

        FichaPiel ficha = biblioteca.ficha(id).orElseThrow();
        assertEquals("Nubuck", ficha.getTipo());
        assertEquals(1.3, ficha.getGruesoMaxMm());
        assertNull(ficha.getCalidad());
    }

    @Test
    void laFichaSeGuardaEnteraConSusTramosDePrecio() throws Exception {
        Long id = biblioteca.crear("Web Ficha", "Piel Web Ficha", null, null).ficha().getId();

        mvc.perform(get("/biblioteca-pieles/" + id))
                .andExpect(status().isOk())
                .andExpect(view().name("biblioteca-pieles-ficha"))
                .andExpect(content().string(containsString("PIEL WEB FICHA · WEB FICHA")))
                .andExpect(content().string(containsString("name=\"tramos[0].moq\"")))
                .andExpect(content().string(containsString("name=\"comentarios\"")))
                .andExpect(content().string(containsString("Esta piel no tiene fotos")));

        mvc.perform(post("/biblioteca-pieles/" + id)
                        .param("proveedor", "Web Ficha").param("nombre", "Piel Web Ficha")
                        .param("refProveedor", "wf-12").param("animal", "Vaca").param("tipo", "Release")
                        .param("grueso", "1,2").param("superficie", "4-5").param("stockService", "true")
                        .param("moqPorColor", "true").param("plazoEntregaDias", "28")
                        .param("fechaPrecios", "2026-11-10").param("calidad", "4")
                        .param("notaCalidad", "Regular en el segundo lote").param("comentarios", "Mancha con el agua")
                        .param("tramos[0].moq", "200").param("tramos[0].precio", "38")
                        .param("tramos[1].moq", "50").param("tramos[1].precio", "42,5")
                        .param("tramos[2].moq", "").param("tramos[2].precio", ""))
                .andExpect(redirectedUrl("/biblioteca-pieles/" + id))
                .andExpect(flash().attribute("mensaje", "Guardado."));

        FichaPiel ficha = biblioteca.ficha(id).orElseThrow();
        assertEquals("WF-12", ficha.getRefProveedor());
        assertEquals("Release", ficha.getTipo());
        assertEquals(1.2, ficha.getGruesoMinMm());
        assertEquals(5.0, ficha.getSuperficieMaxM2());
        assertEquals(Boolean.TRUE, ficha.getMoqPorColor());
        assertEquals(28, ficha.getPlazoEntregaDias());
        assertEquals(4, ficha.getCalidad());
        assertEquals("Mancha con el agua", ficha.getComentarios());
        List<PrecioPiel> tramos = biblioteca.preciosDe(id);
        assertEquals(2, tramos.size());
        assertEquals(50.0, tramos.get(0).getMoqM2(), "el mínimo de pedido es el tramo más bajo");
        assertEquals(42.5, tramos.get(0).getPrecioEurM2());

        mvc.perform(get("/biblioteca-pieles/" + id))
                .andExpect(content().string(containsString("value=\"42,5\"")))
                .andExpect(content().string(containsString("Mancha con el agua")))
                .andExpect(content().string(containsString("value=\"2026-11-10\"")));
    }

    @Test
    void unDatoMalEnLaFichaNoGuardaNadaYLoDice() throws Exception {
        Long id = biblioteca.crear("Web Ficha Mal", "Piel Web Ficha Mal", null, null).ficha().getId();

        mvc.perform(post("/biblioteca-pieles/" + id)
                        .param("proveedor", "Web Ficha Mal").param("nombre", "Piel Web Ficha Mal")
                        .param("comentarios", "No debe guardarse")
                        .param("tramos[0].moq", "cincuenta").param("tramos[0].precio", "42"))
                .andExpect(redirectedUrl("/biblioteca-pieles/" + id))
                .andExpect(flash().attribute("mensajeError", containsString("cincuenta")));

        assertEquals("", biblioteca.ficha(id).orElseThrow().getComentarios());
        assertTrue(biblioteca.preciosDe(id).isEmpty());
    }

    @Test
    void unaFichaQueNoExisteEsUn404() throws Exception {
        mvc.perform(get("/biblioteca-pieles/999999")).andExpect(status().isNotFound());
    }

    @Test
    void lasFotosSeSubenSeReducenYSeSirven() throws Exception {
        Long id = biblioteca.crear("Web Fotos", "Piel Web Fotos", null, null).ficha().getId();
        MockMultipartFile foto = new MockMultipartFile("fotos", "C:\\fotos\\muestra.jpg", "image/jpeg",
                FotosDePrueba.jpeg(2400, 1200, Color.DARK_GRAY));

        mvc.perform(multipart("/biblioteca-pieles/" + id + "/fotos").file(foto))
                .andExpect(redirectedUrl("/biblioteca-pieles/" + id))
                .andExpect(flash().attribute("mensaje", "1 foto guardada."));

        List<ResumenFotoPiel> fotos = biblioteca.fotosDe(id);
        assertEquals(1, fotos.size());
        assertTrue(fotos.get(0).principal());
        byte[] servida = mvc.perform(get("/biblioteca-pieles/foto/" + fotos.get(0).id()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        java.awt.image.BufferedImage imagen = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(servida));
        assertEquals(1600, imagen.getWidth(), "reducida a 1.600 px de lado, como las del catálogo");

        mvc.perform(get("/biblioteca-pieles/" + id))
                .andExpect(content().string(containsString("/biblioteca-pieles/foto/" + fotos.get(0).id())));
        mvc.perform(get("/biblioteca-pieles").param("q", "piel web fotos"))
                .andExpect(content().string(containsString("class=\"miniatura\"")));

        mvc.perform(post("/biblioteca-pieles/foto/" + fotos.get(0).id() + "/borrar").param("ficha", id.toString()))
                .andExpect(redirectedUrl("/biblioteca-pieles/" + id));
        assertTrue(biblioteca.fotosDe(id).isEmpty());
        mvc.perform(get("/biblioteca-pieles/foto/" + fotos.get(0).id())).andExpect(status().isNotFound());
    }

    @Test
    void laFichaTecnicaSeGuardaTalCualElPdfSeAbreYLoDemasSeDescarga() throws Exception {
        Long id = biblioteca.crear("Web Documentos", "Piel Web Documentos", null, null).ficha().getId();
        byte[] pdf = "%PDF-1.4 ficha de prueba".getBytes(StandardCharsets.US_ASCII);
        byte[] html = "<script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII);

        mvc.perform(multipart("/biblioteca-pieles/" + id + "/documentos")
                        .file(new MockMultipartFile("documentos", "ficha técnica.pdf", "application/pdf", pdf))
                        .file(new MockMultipartFile("documentos", "oferta.html", "text/html", html)))
                .andExpect(redirectedUrl("/biblioteca-pieles/" + id))
                .andExpect(flash().attribute("mensaje", "2 documentos guardados."));

        List<ResumenDocumentoPiel> documentos = biblioteca.documentosDe(id);
        assertEquals(List.of("ficha técnica.pdf", "oferta.html"),
                documentos.stream().map(ResumenDocumentoPiel::nombreFichero).toList());

        mvc.perform(get("/biblioteca-pieles/documento/" + documentos.get(0).id()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(content().bytes(pdf));
        // Un HTML subido como documento nunca se sirve como página de este sitio.
        mvc.perform(get("/biblioteca-pieles/documento/" + documentos.get(1).id()))
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        mvc.perform(post("/biblioteca-pieles/documento/" + documentos.get(1).id() + "/borrar")
                        .param("ficha", id.toString()))
                .andExpect(redirectedUrl("/biblioteca-pieles/" + id));
        assertEquals(1, biblioteca.documentosDe(id).size());
    }

    @Test
    void unaPielDeClienteSinFichaSeVeConSuPropuestaYSeEnlazaAlAceptarla() throws Exception {
        Long id = biblioteca.crear("Web Propuesta", "Vachette Web Propuesta", null, null).ficha().getId();
        memoriaPieles.guardarPiel(CLIENTE, "BPW20", "Vachette Web Propuesta");

        mvc.perform(get("/biblioteca-pieles").param("q", "BPW20"))
                .andExpect(content().string(containsString("Pieles de cliente sin ficha (1)")))
                .andExpect(content().string(containsString(">BPW20<")))
                .andExpect(content().string(containsString("¿Es VACHETTE WEB PROPUESTA de WEB PROPUESTA?")))
                .andExpect(content().string(containsString("Sí, enlazar")));
        assertNull(pieles.findById(new PielId(CLIENTE, "BPW20")).orElseThrow().getFichaPielId(),
                "pintar la propuesta no enlaza nada");

        mvc.perform(post("/biblioteca-pieles/enlazar")
                        .param("cliente", CLIENTE).param("refPiel", "BPW20").param("ficha", id.toString()))
                .andExpect(redirectedUrl("/biblioteca-pieles"))
                .andExpect(flash().attribute("mensaje", containsString("BPW20")));

        assertEquals(id, pieles.findById(new PielId(CLIENTE, "BPW20")).orElseThrow().getFichaPielId());
        mvc.perform(get("/biblioteca-pieles/" + id))
                .andExpect(content().string(containsString(">" + CLIENTE + " BPW20<")))
                .andExpect(content().string(containsString("Quitar enlace")));

        mvc.perform(post("/biblioteca-pieles/desenlazar")
                        .param("cliente", CLIENTE).param("refPiel", "BPW20").param("ficha", id.toString()))
                .andExpect(redirectedUrl("/biblioteca-pieles/" + id));
        assertNull(pieles.findById(new PielId(CLIENTE, "BPW20")).orElseThrow().getFichaPielId());
    }

    @Test
    void crearLaFichaDesdeUnaPielDeClienteLaDejaEnlazada() throws Exception {
        memoriaPieles.guardarPiel(CLIENTE, "BPW30", "Napa Web Creada");

        mvc.perform(post("/biblioteca-pieles/nueva")
                        .param("proveedor", "Web Creada").param("nombre", "Napa Web Creada")
                        .param("enlazarCliente", CLIENTE).param("enlazarRef", "BPW30").param("buscadaPara", ""))
                .andExpect(status().is3xxRedirection());

        FichaPiel ficha = unica("NAPA WEB CREADA");
        assertEquals(ficha.getId(), pieles.findById(new PielId(CLIENTE, "BPW30")).orElseThrow().getFichaPielId());
        assertTrue(biblioteca.recorrido(ficha.getId()).isEmpty(), "sin «buscada para» no se apunta nada");
    }

    @Test
    void enlazarConUnClienteQueNoExisteSeRechaza() throws Exception {
        Long id = biblioteca.crear("Web Cliente Malo", "Piel Web Cliente Malo", null, null).ficha().getId();

        mvc.perform(post("/biblioteca-pieles/enlazar").param("cliente", "NO EXISTE").param("refPiel", "X1")
                        .param("ficha", id.toString()).param("volver", "ficha"))
                .andExpect(redirectedUrl("/biblioteca-pieles/" + id))
                .andExpect(flash().attribute("mensajeError", containsString("Cliente desconocido")));
        assertTrue(biblioteca.enlazadas(id).isEmpty());
    }

    @Test
    void elRecorridoSeApuntaYSeVeEnLaFicha() throws Exception {
        Long id = biblioteca.crear("Web Recorrido", "Piel Web Recorrido", null, null).ficha().getId();

        mvc.perform(post("/biblioteca-pieles/" + id + "/eventos")
                        .param("fecha", "2026-03-12").param("cliente", "apc").param("tipo", "MOSTRADA")
                        .param("modelo", "proto b").param("nota", "Les gustó"))
                .andExpect(redirectedUrl("/biblioteca-pieles/" + id))
                .andExpect(flash().attribute("mensaje", containsString("Apuntado")));

        mvc.perform(get("/biblioteca-pieles/" + id))
                .andExpect(content().string(containsString("12/03/2026")))
                .andExpect(content().string(containsString(">APC<")))
                .andExpect(content().string(containsString(">Mostrada<")))
                .andExpect(content().string(containsString(">PROTO B<")))
                .andExpect(content().string(containsString("Les gustó")));

        mvc.perform(post("/biblioteca-pieles/" + id + "/eventos").param("cliente", "APC").param("tipo", "INVENTADO"))
                .andExpect(flash().attribute("mensajeError", containsString("No se ha podido apuntar")));
        mvc.perform(post("/biblioteca-pieles/" + id + "/eventos")
                        .param("fecha", "ayer").param("cliente", "APC").param("tipo", "BUSCADA"))
                .andExpect(flash().attribute("mensajeError", containsString("no es una fecha")));

        Long evento = biblioteca.recorrido(id).get(0).eventoId();
        mvc.perform(post("/biblioteca-pieles/evento/" + evento + "/borrar").param("ficha", id.toString()))
                .andExpect(redirectedUrl("/biblioteca-pieles/" + id));
        assertTrue(biblioteca.recorrido(id).isEmpty());
    }

    @Test
    void duplicarYBorrarUnaFicha() throws Exception {
        Long id = biblioteca.crear("Web Duplicar", "Piel Web Original", "Cabra", "Napa").ficha().getId();
        biblioteca.apuntar(id, LocalDate.now(), "AMI", TipoEventoPiel.BUSCADA, null, null);

        mvc.perform(post("/biblioteca-pieles/" + id + "/duplicar").param("nombre", "Piel Web Copia"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("mensaje", containsString("PIEL WEB COPIA")));
        FichaPiel copia = unica("PIEL WEB COPIA");
        assertEquals("Napa", copia.getTipo());

        mvc.perform(post("/biblioteca-pieles/" + id + "/duplicar").param("nombre", "Piel Web Copia"))
                .andExpect(redirectedUrl("/biblioteca-pieles/" + id))
                .andExpect(flash().attribute("mensajeError", containsString("ya tiene una piel")));

        mvc.perform(post("/biblioteca-pieles/" + id + "/borrar"))
                .andExpect(redirectedUrl("/biblioteca-pieles"))
                .andExpect(flash().attribute("mensaje", containsString("PIEL WEB ORIGINAL")));
        assertTrue(biblioteca.ficha(id).isEmpty());
        assertTrue(biblioteca.ficha(copia.getId()).isPresent());
    }

    @Test
    void laPantallaDeProveedoresEditaLaValoracionCasillaACasilla() throws Exception {
        mvc.perform(post("/biblioteca-pieles/proveedores/nuevo").param("nombre", "Web Proveedor Uno"))
                .andExpect(redirectedUrl("/biblioteca-pieles/proveedores"));
        Long id = biblioteca.asegurarProveedor("Web Proveedor Uno").getId();
        String url = "/biblioteca-pieles/proveedores/" + id + "/campo";

        mvc.perform(post(url).param("campo", "responsabilidad").param("valor", "5"))
                .andExpect(jsonPath("$.ok").value(true)).andExpect(jsonPath("$.valor").value("5"));
        mvc.perform(post(url).param("campo", "notaValoracion").param("valor", " Contesta el mismo día "))
                .andExpect(jsonPath("$.valor").value("Contesta el mismo día"));
        mvc.perform(post(url).param("campo", "entregaATiempo").param("valor", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("1 a 5")));

        mvc.perform(get("/biblioteca-pieles/proveedores"))
                .andExpect(status().isOk())
                .andExpect(view().name("biblioteca-pieles-proveedores"))
                .andExpect(content().string(containsString("value=\"WEB PROVEEDOR UNO\"")))
                .andExpect(content().string(containsString("value=\"Contesta el mismo día\"")))
                .andExpect(content().string(containsString("data-url=\"" + url + "\"")))
                .andExpect(content().string(containsString("valorado el")));

        // Con una piel no se puede borrar; se dice por qué y sigue ahí.
        Long ficha = biblioteca.crear("Web Proveedor Uno", "Piel Web Proveedor", null, null).ficha().getId();
        mvc.perform(post("/biblioteca-pieles/proveedores/" + id + "/borrar"))
                .andExpect(flash().attribute("mensajeError", containsString("tiene 1 piel")));
        mvc.perform(get("/biblioteca-pieles/" + ficha))
                .andExpect(content().string(containsString("responsabilidad <strong>5/5</strong>")));
        biblioteca.borrar(ficha);
        mvc.perform(post("/biblioteca-pieles/proveedores/" + id + "/borrar"))
                .andExpect(flash().attribute("mensaje", "Proveedor borrado."));
        assertTrue(biblioteca.proveedor(id).isEmpty());
    }

    /** La única ficha con ese nombre: los nombres de los tests no se repiten entre proveedores. */
    private FichaPiel unica(String nombre) {
        List<BibliotecaPieles.Fila> filas = biblioteca.buscar(
                new BibliotecaPieles.Filtro(nombre, null, null, null, null, null, null, null));
        assertEquals(1, filas.size(), nombre);
        return filas.get(0).ficha();
    }
}
