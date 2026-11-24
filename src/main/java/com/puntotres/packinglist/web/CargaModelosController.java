package com.puntotres.packinglist.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.modelos.service.ImagenesDePdf;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.TemporadaGuardada;
import com.puntotres.packinglist.service.cargamodelos.CargadorModelos;
import com.puntotres.packinglist.service.cargamodelos.CargadorModelos.CargaFallida;
import com.puntotres.packinglist.service.cargamodelos.CargadorModelos.ResumenCarga;
import com.puntotres.packinglist.service.cargamodelos.CarpetaModelos;
import com.puntotres.packinglist.service.cargamodelos.ConstructorLote;
import com.puntotres.packinglist.service.cargamodelos.ConstructorLote.Candidatas;
import com.puntotres.packinglist.service.cargamodelos.EdicionModelo;
import com.puntotres.packinglist.service.cargamodelos.EsqueletoCarpetas;
import com.puntotres.packinglist.service.cargamodelos.LectorOrigenes;
import com.puntotres.packinglist.service.cargamodelos.LecturaOrigen;
import com.puntotres.packinglist.service.cargamodelos.LecturaOrigen.ArticuloLeido;
import com.puntotres.packinglist.service.cargamodelos.LoteCarga;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.EstadoModelo;
import com.puntotres.packinglist.service.cargamodelos.OrigenCarga;
import com.puntotres.packinglist.service.cargamodelos.VistasCarga;
import com.puntotres.packinglist.service.cargamodelos.VistasCarga.Cuentas;
import com.puntotres.packinglist.service.cargamodelos.VistasCarga.Progreso;
import com.puntotres.packinglist.service.cargamodelos.VistasCarga.VistaLote;
import com.puntotres.packinglist.service.cargamodelos.VistasCarga.VistaModelo;
import com.puntotres.packinglist.service.corte.ConversorFotos;
import com.puntotres.packinglist.service.corte.LectorFotos;

/**
 * Carga de modelos: la puerta de entrada REVISADA de los modelos al catálogo.
 * Dos pantallas: el origen de la información (/carga-modelos) y la batería
 * (/carga-modelos/bateria), donde cada modelo se completa y se acepta o se
 * cancela antes de que nada llegue al sistema.
 *
 * La batería es una sola página que se pinta en el navegador: pide el lote a
 * {@code /carga-modelos/estado} y manda cada cambio por {@code fetch}. El
 * servidor es la única fuente de verdad —el lote vive en la sesión—, así que
 * recargar la página no pierde nada de lo tecleado.
 *
 * Las peticiones de la batería se hacen de una en una sobre el lote
 * ({@code synchronized}): el navegador puede mandar dos seguidas (un campo y
 * un «Aceptar») y el lote no está pensado para dos hilos.
 */
@Controller
public class CargaModelosController {

    private static final String SIN_CARGA = "La carga ya no está: la sesión ha caducado o se ha empezado otra. "
            + "Vuelve a elegir el origen.";

    /** Un cliente en la pantalla de origen: con qué orígenes se puede usar. */
    public record OpcionCliente(String nombre, boolean conTemporada, boolean conSms) {
    }

    /** La respuesta a un cambio en un modelo: el modelo, los recuentos y lo que haya que decirle al usuario. */
    public record RespuestaModelo(VistaModelo modelo, Cuentas cuentas, Progreso fotos, List<String> avisos) {
    }

    public record RespuestaCarga(ResumenCarga resumen, VistaLote lote) {
    }

    public record RespuestaNuevo(int id, VistaLote lote) {
    }

    public record EleccionFicha(String loteId, List<String> claves) {
    }

    /** No hay carga en la sesión: la pantalla lo dice y manda al origen. */
    static class SinCarga extends RuntimeException {
    }

    private final CargaModelosEnCurso enCurso;
    private final ClientesProperties clientesProperties;
    private final ArchivoTemporadas temporadas;
    private final LectorOrigenes lectores;
    private final ConstructorLote constructor;
    private final CargadorModelos cargador;
    private final VistasCarga vistas;
    private final ImagenesDePdf imagenesDePdf;

    public CargaModelosController(CargaModelosEnCurso enCurso, ClientesProperties clientesProperties,
                                  ArchivoTemporadas temporadas, LectorOrigenes lectores,
                                  ConstructorLote constructor, CargadorModelos cargador, VistasCarga vistas,
                                  ImagenesDePdf imagenesDePdf) {
        this.enCurso = enCurso;
        this.clientesProperties = clientesProperties;
        this.temporadas = temporadas;
        this.lectores = lectores;
        this.constructor = constructor;
        this.cargador = cargador;
        this.vistas = vistas;
        this.imagenesDePdf = imagenesDePdf;
    }

    // --- Pantalla 1: el origen de la información ---

    /**
     * {@code cliente} y {@code temporada} llegan desde /temporadas («Cargar
     * sus modelos»): la pantalla sale con ellos ya elegidos.
     */
    @GetMapping("/carga-modelos")
    public String origen(@RequestParam(required = false) String cliente,
                         @RequestParam(required = false) Long temporada, Model model) {
        Map<String, OpcionCliente> clientes = new LinkedHashMap<>();
        clientesProperties.getClientes().forEach((clave, config) -> clientes.put(clave, new OpcionCliente(
                config.getNombre(), lectores.admite(OrigenCarga.TEMPORADA, clave),
                lectores.admite(OrigenCarga.SMS, clave))));
        model.addAttribute("clientes", clientes);
        model.addAttribute("temporadas", temporadas.paraElDesplegableDelCorte());
        if (!model.containsAttribute("cliente")) {
            model.addAttribute("cliente", cliente);
        }
        if (!model.containsAttribute("temporada")) {
            model.addAttribute("temporada", temporada);
        }
        if (!model.containsAttribute("origen")) {
            model.addAttribute("origen", OrigenCarga.TEMPORADA.name());
        }
        enCurso.lote().ifPresent(lote -> model.addAttribute("enMarcha", lote.modelos().size()));
        return "carga-modelos";
    }

    /** «Ir a Cargar Modelos»: lee el origen elegido, monta el lote y pasa a la batería. */
    @PostMapping("/carga-modelos/origen")
    public String cargarOrigen(@RequestParam String origen,
                               @RequestParam String cliente,
                               @RequestParam(required = false) Long temporadaId,
                               @RequestParam(required = false) MultipartFile excel,
                               @RequestParam(name = "fotos", required = false) MultipartFile zip,
                               @RequestParam(required = false) List<MultipartFile> carpeta,
                               RedirectAttributes redirect) {
        OrigenCarga elOrigen;
        try {
            elOrigen = OrigenCarga.valueOf(origen);
        } catch (IllegalArgumentException e) {
            return error(redirect, "Elige de dónde salen los modelos.", OrigenCarga.TEMPORADA.name(), cliente,
                    temporadaId);
        }
        ClienteConfig config = clientesProperties.clientePara(cliente).orElse(null);
        if (config == null) {
            return error(redirect, "Cliente desconocido: " + cliente + ".", origen, cliente, temporadaId);
        }
        boolean conZip = zip != null && !zip.isEmpty();
        // Un selector de carpeta sin elegir manda igual una parte sin nombre.
        List<MultipartFile> ficheros = carpeta == null ? List.of() : carpeta.stream()
                .filter(fichero -> fichero.getOriginalFilename() != null
                        && !fichero.getOriginalFilename().isBlank())
                .toList();
        if (conZip && !ficheros.isEmpty()) {
            return error(redirect, "Sube la carpeta de modelos o su zip, no los dos.", origen, cliente, temporadaId);
        }
        if (elOrigen == OrigenCarga.GRUPO && !conZip && ficheros.isEmpty()) {
            return error(redirect, "Sube la carpeta de modelos (o su zip): cada subcarpeta es un modelo.", origen,
                    cliente, temporadaId);
        }

        Path directorio = null;
        try {
            Leido leido = elOrigen.conExcel() ? leer(elOrigen, cliente, config, temporadaId, excel) : null;
            directorio = Files.createTempDirectory(ConversorFotos.PREFIJO_DIRECTORIO);
            CarpetaModelos.Lectura deLaCarpeta = leerCarpeta(conZip ? zip : null, ficheros, directorio);
            LoteCarga lote = switch (elOrigen) {
                case MANUAL -> constructor.manual(cliente, directorio);
                case GRUPO -> constructor.deGrupo(cliente, deLaCarpeta, directorio);
                default -> constructor.deExcel(elOrigen, cliente, leido.temporadaId(), leido.lectura(),
                        deLaCarpeta, directorio);
            };
            if (lote.modelos().isEmpty()) {
                throw new IllegalArgumentException(elOrigen == OrigenCarga.GRUPO
                        ? "la carpeta no tiene ninguna subcarpeta de modelo con fotos o fichas técnicas"
                        : "el excel no trae ningún modelo");
            }
            enCurso.empezar(lote);
        } catch (IllegalArgumentException e) {
            CorteEnCurso.borrar(directorio);
            return error(redirect, "No se han podido cargar los modelos: " + e.getMessage() + ".", origen, cliente,
                    temporadaId);
        } catch (IOException | RuntimeException e) {
            CorteEnCurso.borrar(directorio);
            return error(redirect, conZip && directorio != null
                    ? "No se ha podido abrir el zip de la carpeta de modelos: ¿es un fichero .zip?"
                    : "No se ha podido leer lo subido: comprueba que el excel es el de este origen.", origen,
                    cliente, temporadaId);
        }
        return "redirect:/carga-modelos/bateria";
    }

    /**
     * El esqueleto vacío de la carpeta de carpetas de un origen con excel:
     * un zip con la carpeta de la temporada y una carpeta vacía por modelo.
     */
    @PostMapping("/carga-modelos/esqueleto")
    @ResponseBody
    public ResponseEntity<byte[]> esqueleto(@RequestParam String origen,
                                            @RequestParam String cliente,
                                            @RequestParam(required = false) Long temporadaId,
                                            @RequestParam(required = false) MultipartFile excel) throws IOException {
        OrigenCarga elOrigen = OrigenCarga.valueOf(origen);
        ClienteConfig config = clientesProperties.clientePara(cliente).orElseThrow(
                () -> new IllegalArgumentException("Cliente desconocido: " + cliente));
        if (!elOrigen.conExcel()) {
            throw new IllegalArgumentException("El esqueleto sale de un excel: en este origen la carpeta la haces tú");
        }
        Leido leido = leer(elOrigen, cliente, config, temporadaId, excel);
        Map<String, String> nombreDelModelo = new LinkedHashMap<>();
        for (ArticuloLeido articulo : leido.lectura().articulos()) {
            nombreDelModelo.merge(articulo.modelo(), articulo.nombreModelo(),
                    (antes, nuevo) -> antes.isBlank() ? nuevo : antes);
        }
        if (nombreDelModelo.isEmpty()) {
            throw new IllegalArgumentException("El excel no trae ningún modelo");
        }
        Set<String> carpetas = new LinkedHashSet<>();
        nombreDelModelo.forEach((codigo, nombre) -> carpetas.add(EsqueletoCarpetas.carpetaDe(codigo, nombre)));
        String raiz = leido.raiz();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("Carpetas " + config.getNombre() + " " + raiz + ".zip",
                                java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(EsqueletoCarpetas.zip(raiz, carpetas));
    }

    /** Lo leído de un origen con excel: a qué temporada se vincula y cómo se llama la raíz del esqueleto. */
    private record Leido(LecturaOrigen lectura, Long temporadaId, String raiz) {
    }

    private Leido leer(OrigenCarga origen, String cliente, ClienteConfig config, Long temporadaId,
                       MultipartFile excel) throws IOException {
        if (!lectores.admite(origen, cliente)) {
            throw new IllegalArgumentException("este origen todavía está en desarrollo para " + config.getNombre());
        }
        boolean conExcel = excel != null && !excel.isEmpty();
        // La temporada solo cuenta si es del cliente elegido: el desplegable se rellena en el navegador.
        Optional<TemporadaGuardada> temporada = temporadas.paraElEnvio(temporadaId, cliente);
        String nombreExcel = conExcel && excel.getOriginalFilename() != null
                ? excel.getOriginalFilename().replaceAll("(?i)\\.xlsx$", "") : "";
        String raiz = temporada.map(TemporadaGuardada::getTemporada).orElse(nombreExcel);
        Long vinculada = temporada.map(TemporadaGuardada::getId).orElse(null);
        return switch (origen) {
            case TEMPORADA -> {
                TemporadaGuardada guardada = temporada.filter(TemporadaGuardada::tienePedido).orElseThrow(
                        () -> new IllegalArgumentException("elige una temporada guardada de " + config.getNombre()
                                + " con su excel de pedido"));
                yield new Leido(lectores.dePedido(cliente, guardada.getExcelPedido(),
                        guardada.tieneProduccion() ? guardada.getExcelProduccion() : null), vinculada, raiz);
            }
            case SMS -> {
                if (!conExcel) {
                    throw new IllegalArgumentException("sube el excel de pedido de SMS");
                }
                yield new Leido(lectores.deSms(cliente, excel.getBytes()), vinculada, raiz);
            }
            case PRODUCCION -> {
                // El subido a mano manda sobre el guardado en la temporada.
                byte[] bytes = conExcel ? excel.getBytes() : temporada.filter(TemporadaGuardada::tieneProduccion)
                        .map(TemporadaGuardada::getExcelProduccion).orElseThrow(() -> new IllegalArgumentException(
                                "sube el excel de producción, o elige una temporada que lo tenga guardado"));
                yield new Leido(lectores.deProduccion(cliente, bytes), vinculada, raiz);
            }
            default -> throw new IllegalArgumentException("este origen no lleva excel");
        };
    }

    private static CarpetaModelos.Lectura leerCarpeta(MultipartFile zip, List<MultipartFile> ficheros,
                                                      Path directorio) throws IOException {
        if (zip != null) {
            Path copia = directorio.resolve("subido.zip");
            zip.transferTo(copia);
            try {
                return CarpetaModelos.leerZip(copia, directorio);
            } finally {
                Files.deleteIfExists(copia);
            }
        }
        if (ficheros.isEmpty()) {
            return CarpetaModelos.Lectura.vacia();
        }
        return CarpetaModelos.leer(ficheros.stream()
                .map(fichero -> new LectorFotos.Fichero(fichero.getOriginalFilename(), fichero::getInputStream))
                .toList(), directorio);
    }

    private static String error(RedirectAttributes redirect, String mensaje, String origen, String cliente,
                                Long temporadaId) {
        redirect.addFlashAttribute("error", mensaje);
        redirect.addFlashAttribute("origen", origen);
        redirect.addFlashAttribute("cliente", cliente);
        redirect.addFlashAttribute("temporada", temporadaId);
        return "redirect:/carga-modelos";
    }

    // --- Pantalla 2: la batería ---

    @GetMapping("/carga-modelos/bateria")
    public String bateria() {
        return enCurso.lote().isPresent() ? "carga-modelos-bateria" : "redirect:/carga-modelos";
    }

    /** Descarta la carga en marcha y vuelve al origen. */
    @PostMapping("/carga-modelos/descartar")
    public String descartar() {
        enCurso.descartar();
        return "redirect:/carga-modelos";
    }

    @GetMapping("/carga-modelos/estado")
    @ResponseBody
    public VistaLote estado() {
        LoteCarga lote = lote();
        synchronized (lote) {
            return vistas.de(lote);
        }
    }

    /** Solo cómo van las fotos: la pantalla pregunta mientras se convierten. */
    @GetMapping("/carga-modelos/progreso")
    @ResponseBody
    public Progreso progreso() {
        return vistas.progreso(lote());
    }

    /** Mantiene viva la sesión mientras se trabaja en la batería. */
    @PostMapping("/carga-modelos/latido")
    @ResponseBody
    public ResponseEntity<Void> latido() {
        lote();
        return ResponseEntity.noContent().build();
    }

    /** El panel de un modelo, entero, cada vez que cambia un campo. */
    @PutMapping("/carga-modelos/modelos/{id}")
    @ResponseBody
    public RespuestaModelo editar(@PathVariable int id, @RequestBody EdicionModelo edicion) {
        LoteCarga lote = lote();
        synchronized (lote) {
            ModeloEnCarga modelo = modelo(lote, id);
            constructor.aplicar(lote, modelo, edicion);
            return respuesta(lote, modelo, List.of());
        }
    }

    /** «Aceptar Modelo», «Cancelar Modelo» o devolverlo a pendiente. */
    @PostMapping("/carga-modelos/modelos/{id}/estado")
    @ResponseBody
    public VistaLote marcar(@PathVariable int id, @RequestParam String estado) {
        LoteCarga lote = lote();
        synchronized (lote) {
            ModeloEnCarga modelo = modelo(lote, id);
            EstadoModelo nuevo = EstadoModelo.valueOf(estado);
            if (!constructor.marcar(lote, modelo, nuevo)) {
                throw new IllegalArgumentException("Todavía no se puede aceptar: "
                        + String.join("; ", constructor.faltan(lote, modelo)));
            }
            return vistas.de(lote);
        }
    }

    /** «Añadir otro»: un modelo en blanco más, con el cliente del anterior. */
    @PostMapping("/carga-modelos/modelos")
    @ResponseBody
    public RespuestaNuevo anadir(@RequestParam(required = false) String cliente) {
        LoteCarga lote = lote();
        synchronized (lote) {
            ModeloEnCarga nuevo = constructor.anadirModelo(lote, cliente);
            return new RespuestaNuevo(nuevo.id(), vistas.de(lote));
        }
    }

    @PostMapping("/carga-modelos/aceptar-completos")
    @ResponseBody
    public VistaLote aceptarCompletos() {
        LoteCarga lote = lote();
        synchronized (lote) {
            constructor.aceptarCompletos(lote);
            return vistas.de(lote);
        }
    }

    /** Fotos y fichas técnicas subidas desde el panel de un modelo. */
    @PostMapping("/carga-modelos/modelos/{id}/ficheros")
    @ResponseBody
    public RespuestaModelo subir(@PathVariable int id, @RequestParam List<MultipartFile> ficheros)
            throws IOException {
        LoteCarga lote = lote();
        synchronized (lote) {
            ModeloEnCarga modelo = modelo(lote, id);
            List<LectorFotos.Fichero> subidos = ficheros.stream()
                    .filter(fichero -> !fichero.isEmpty())
                    .map(fichero -> new LectorFotos.Fichero(fichero.getOriginalFilename(), fichero::getInputStream))
                    .toList();
            if (subidos.isEmpty()) {
                throw new IllegalArgumentException("No has elegido ningún fichero");
            }
            return respuesta(lote, modelo, constructor.anadirFicheros(lote, modelo, subidos));
        }
    }

    /** La foto reducida de un modelo del lote; 404 mientras no esté convertida. */
    @GetMapping("/carga-modelos/modelos/{id}/fotos/{fotoId}")
    public ResponseEntity<byte[]> foto(@PathVariable int id, @PathVariable String fotoId) throws IOException {
        LoteCarga lote = lote();
        Optional<Path> jpeg;
        synchronized (lote) {
            jpeg = constructor.jpegDe(modelo(lote, id), fotoId);
        }
        if (jpeg.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(Files.readAllBytes(jpeg.get()));
    }

    /** La ficha técnica, para abrirla en otra pestaña. Solo PDF: es lo único que el lote guarda como ficha. */
    @GetMapping("/carga-modelos/modelos/{id}/fichas/{fichaId}")
    public ResponseEntity<byte[]> ficha(@PathVariable int id, @PathVariable String fichaId) throws IOException {
        LoteCarga lote = lote();
        Optional<Path> pdf;
        synchronized (lote) {
            pdf = constructor.pdfDe(modelo(lote, id), fichaId);
        }
        if (pdf.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .body(Files.readAllBytes(pdf.get()));
    }

    /** Las imágenes de una ficha técnica, para el emergente donde se eligen. */
    @GetMapping("/carga-modelos/modelos/{id}/fichas/{fichaId}/candidatas")
    @ResponseBody
    public Candidatas candidatas(@PathVariable int id, @PathVariable String fichaId) throws IOException {
        LoteCarga lote = lote();
        synchronized (lote) {
            return constructor.candidatas(modelo(lote, id), fichaId);
        }
    }

    @GetMapping("/carga-modelos/candidatas/{loteId}/{clave}")
    public ResponseEntity<byte[]> candidata(@PathVariable String loteId, @PathVariable String clave) {
        lote();
        return imagenesDePdf.candidata(loteId, clave)
                .map(png -> ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.noStore()).body(png))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Lo elegido en el emergente de una ficha técnica: esas, y solo esas, son sus fotos. */
    @PostMapping("/carga-modelos/modelos/{id}/fichas/{fichaId}/elegir")
    @ResponseBody
    public RespuestaModelo elegir(@PathVariable int id, @PathVariable String fichaId,
                                  @RequestBody EleccionFicha eleccion) throws IOException {
        LoteCarga lote = lote();
        synchronized (lote) {
            ModeloEnCarga modelo = modelo(lote, id);
            constructor.elegirCandidatas(lote, modelo, fichaId, eleccion.loteId(), eleccion.claves());
            return respuesta(lote, modelo, List.of());
        }
    }

    @DeleteMapping("/carga-modelos/modelos/{id}/fichas/{fichaId}")
    @ResponseBody
    public RespuestaModelo quitarFicha(@PathVariable int id, @PathVariable String fichaId) {
        LoteCarga lote = lote();
        synchronized (lote) {
            ModeloEnCarga modelo = modelo(lote, id);
            constructor.quitarFicha(modelo, fichaId);
            return respuesta(lote, modelo, List.of());
        }
    }

    /** «Cargar modelos al Sistema»: los que están en verde, todos o ninguno. */
    @PostMapping("/carga-modelos/cargar")
    @ResponseBody
    public RespuestaCarga cargar() {
        LoteCarga lote = lote();
        synchronized (lote) {
            ResumenCarga resumen = cargador.cargar(lote);
            return new RespuestaCarga(resumen, vistas.de(lote));
        }
    }

    private RespuestaModelo respuesta(LoteCarga lote, ModeloEnCarga modelo, List<String> avisos) {
        return new RespuestaModelo(vistas.de(lote, modelo), vistas.cuentas(lote), vistas.progreso(lote),
                new ArrayList<>(avisos));
    }

    private LoteCarga lote() {
        return enCurso.lote().orElseThrow(SinCarga::new);
    }

    private static ModeloEnCarga modelo(LoteCarga lote, int id) {
        return lote.modelo(id).orElseThrow(() -> new IllegalArgumentException(
                "Ese modelo ya no está en la carga: recarga la página"));
    }

    // --- Errores de las peticiones de la batería: siempre un JSON con su mensaje ---

    @ExceptionHandler(SinCarga.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> sinCarga() {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of("mensaje", SIN_CARGA));
    }

    @ExceptionHandler({IllegalArgumentException.class, CargaFallida.class})
    @ResponseBody
    public ResponseEntity<Map<String, String>> peticionMala(RuntimeException e) {
        HttpStatus estado = e instanceof CargaFallida ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(estado).body(Map.of("mensaje",
                e.getMessage() != null ? e.getMessage() : "No se ha podido hacer"));
    }

    @ExceptionHandler(IOException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> discoMalo(IOException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("mensaje",
                "No se ha podido leer o guardar un fichero: vuelve a intentarlo"));
    }
}
