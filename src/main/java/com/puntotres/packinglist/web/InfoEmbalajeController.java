package com.puntotres.packinglist.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.model.DatosHojaEmbalaje;
import com.puntotres.packinglist.persistence.ArchivoComentariosEmbalaje;
import com.puntotres.packinglist.persistence.ArchivoHojasEmbalaje;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.FotoCatalogo;
import com.puntotres.packinglist.persistence.HojaEmbalaje;
import com.puntotres.packinglist.persistence.ResumenHojaEmbalaje;
import com.puntotres.packinglist.persistence.TemporadaGuardada;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;
import com.puntotres.packinglist.service.embalaje.ArticuloEmbalaje;
import com.puntotres.packinglist.service.embalaje.ArticulosEmbalaje;
import com.puntotres.packinglist.service.embalaje.InfoEmbalajeDocBuilder;
import com.puntotres.packinglist.service.embalaje.PedidoEmbalajeService;

/**
 * «Etiqueta Info de Embalaje para Taller»: la hoja A4 que va pegada en el
 * sobre de etiquetas de un modelo. Tres pantallas: entrada (cliente y su
 * pedido) → formulario (una hoja por artículo, tantas veces como sobres) →
 * hojas antiguas (verlas, volver a bajarlas o importarlas al formulario).
 *
 * El controlador solo enruta: leer el pedido es de {@link PedidoEmbalajeService},
 * escribir el Word de {@link InfoEmbalajeDocBuilder}, guardar de
 * {@link ArchivoHojasEmbalaje} y pasar del formulario a los datos de
 * {@link HojaEmbalajeForm}.
 */
@Controller
public class InfoEmbalajeController {

    private static final MediaType TIPO_DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    /** Mismo texto genérico que las etiquetas de artículo, para lo que no es nuestro mensaje. */
    private static final String MENSAJE_FORMATO_INESPERADO =
            "el fichero no tiene el formato esperado: revisa que sea el excel de pedido del cliente";

    private final PedidoEmbalajeService pedidoEmbalaje;
    private final ArchivoHojasEmbalaje archivo;
    private final ArchivoComentariosEmbalaje comentarios;
    private final InfoEmbalajeDocBuilder docBuilder;
    private final FotosCatalogo fotos;
    private final ArchivoTemporadas temporadas;
    private final ClientesProperties clientesProperties;
    private final EmbalajeEnCurso enCurso;

    public InfoEmbalajeController(PedidoEmbalajeService pedidoEmbalaje, ArchivoHojasEmbalaje archivo,
                                  ArchivoComentariosEmbalaje comentarios, InfoEmbalajeDocBuilder docBuilder,
                                  FotosCatalogo fotos, ArchivoTemporadas temporadas,
                                  ClientesProperties clientesProperties, EmbalajeEnCurso enCurso) {
        this.pedidoEmbalaje = pedidoEmbalaje;
        this.archivo = archivo;
        this.comentarios = comentarios;
        this.docBuilder = docBuilder;
        this.fotos = fotos;
        this.temporadas = temporadas;
        this.clientesProperties = clientesProperties;
        this.enCurso = enCurso;
    }

    // --- Pantalla 1: cliente y pedido ---

    @GetMapping("/info-embalaje")
    public String entrada(Model model) {
        model.addAttribute("clientes", vistaDeClientes());
        model.addAttribute("temporadasJs", temporadas.paraElDesplegable());
        return "info-embalaje";
    }

    @PostMapping("/info-embalaje/cargar")
    public String cargar(@RequestParam String cliente,
                         @RequestParam(required = false) Long temporadaGuardadaId,
                         @RequestParam(required = false) String temporada,
                         @RequestParam(required = false) MultipartFile pedido,
                         RedirectAttributes redirect) {
        if (!pedidoEmbalaje.soporta(cliente)) {
            redirect.addFlashAttribute("error",
                    "El cliente '" + cliente + "' todavía no tiene lector de pedido para la info de embalaje");
            return "redirect:/info-embalaje";
        }
        // Un excel subido a mano manda sobre el guardado; el guardado, solo si
        // es de este cliente y de pedido: el desplegable se rellena en el
        // navegador y cambiar de cliente después dejaría enviada la de otro.
        boolean conExcel = pedido != null && !pedido.isEmpty();
        Optional<TemporadaGuardada> guardada = conExcel ? Optional.empty()
                : temporadas.conPedidoParaElEnvio(temporadaGuardadaId, cliente);
        if (!conExcel && guardada.isEmpty()) {
            redirect.addFlashAttribute("error", temporadaGuardadaId != null
                    ? "La temporada elegida ya no existe o no es de este cliente: vuelve a elegirla "
                            + "o sube el excel de pedido"
                    : "Falta el excel de pedido del cliente: elige una temporada guardada o súbelo");
            return "redirect:/info-embalaje";
        }

        ArticulosEmbalaje leidos;
        try {
            byte[] excel = conExcel ? pedido.getBytes() : guardada.get().getExcel();
            leidos = pedidoEmbalaje.articulosDe(cliente, excel);
        } catch (IllegalArgumentException e) {
            // Nuestras IllegalArgumentException ya llevan mensaje en español.
            redirect.addFlashAttribute("error", "No se pudo leer el pedido: "
                    + (e.getMessage() != null ? e.getMessage() : MENSAJE_FORMATO_INESPERADO));
            return "redirect:/info-embalaje";
        } catch (IOException | RuntimeException e) {
            redirect.addFlashAttribute("error", "No se pudo leer el pedido: " + MENSAJE_FORMATO_INESPERADO);
            return "redirect:/info-embalaje";
        }
        if (leidos.articulos().isEmpty()) {
            redirect.addFlashAttribute("error", "El excel de pedido no tiene ningún artículo: "
                    + "revisa que sea el de este cliente");
            return "redirect:/info-embalaje";
        }

        String nombreTemporada = temporada != null && !temporada.isBlank() ? temporada.trim()
                : guardada.map(TemporadaGuardada::getTemporada).orElse("");
        empezar(cliente, nombreTemporada, guardada.map(TemporadaGuardada::getId).orElse(null));
        enCurso.getArticulos().addAll(leidos.articulos());
        enCurso.getAvisos().addAll(leidos.avisos());
        return "redirect:/info-embalaje/formulario";
    }

    // --- Pantalla 2: el formulario ---

    @GetMapping("/info-embalaje/formulario")
    public String formulario(Model model) {
        if (enCurso.estaVacio()) {
            return "redirect:/info-embalaje";
        }
        int siguiente = archivo.siguienteNumeroEnvio();
        DatosHojaEmbalaje partida = enCurso.getPartida();
        HojaEmbalajeForm formulario;
        if (partida != null) {
            formulario = HojaEmbalajeForm.desde(partida);
            elegido(partida).ifPresent(a -> formulario.setFotoId(a.fotoId()));
        } else {
            formulario = new HojaEmbalajeForm();
            formulario.setNumeroEnvio(siguiente);
        }
        model.addAttribute("enCurso", enCurso);
        model.addAttribute("articulosJs", enCurso.getArticulos());
        model.addAttribute("articuloElegido", partida == null ? -1
                : enCurso.getArticulos().indexOf(elegido(partida).orElse(null)));
        model.addAttribute("partida", partida);
        model.addAttribute("formulario", formulario);
        model.addAttribute("siguienteNumero", siguiente);
        model.addAttribute("talleres", archivo.talleres());
        model.addAttribute("comentariosGuardados", comentarios.todos());
        model.addAttribute("hoy", LocalDate.now());
        return "info-embalaje-formulario";
    }

    /**
     * Guarda la hoja y devuelve el Word. La pantalla lo pide con {@code fetch}
     * y descarga el blob en vez de enviar el formulario: así no se recarga,
     * lo tecleado sigue ahí para la hoja del siguiente artículo, y puede
     * enseñar con qué número se ha guardado (cabecera {@code X-Numero-Envio})
     * y cuál es el siguiente libre ({@code X-Siguiente-Numero}). El siguiente
     * lo dice el contador y no la pantalla: tras repetir el envío 270 de una
     * hoja importada, el 271 puede estar ya usado.
     * Sin JavaScript, el envío normal del formulario descarga igual el Word.
     */
    @PostMapping("/info-embalaje/generar")
    public ResponseEntity<?> generar(@ModelAttribute HojaEmbalajeForm form) throws IOException {
        // Los rechazos van en texto plano y no como redirección: quien llama
        // es el fetch de la pantalla, que enseña el texto tal cual.
        if (enCurso.estaVacio()) {
            return rechazo(HttpStatus.CONFLICT, "La sesión ha caducado o no hay pedido cargado: vuelve a "
                    + "cargar el pedido desde la entrada. No se ha guardado nada.");
        }
        // Una hoja sin artículo no le dice nada al taller y gastaría un número de envío.
        if (form.getReferencia() == null || form.getReferencia().isBlank()) {
            return rechazo(HttpStatus.BAD_REQUEST, "Elige o escribe el artículo: sin referencia no se "
                    + "ha guardado nada.");
        }
        DatosHojaEmbalaje datos = form.aDatos(enCurso, LocalDate.now(), archivo::siguienteNumeroEnvio);
        byte[] foto = fotos.conBytes(form.getFotoId()).map(FotoCatalogo::getJpeg).orElse(null);
        // Primero el Word y luego guardar: una hoja que no se ha podido
        // escribir no debe gastar un número de envío ni salir en los antiguos.
        byte[] contenido = docBuilder.generar(datos, foto);
        HojaEmbalaje guardada = archivo.guardar(datos, enCurso.getTemporadaId());
        return word(datos, contenido)
                .header("X-Hoja-Id", String.valueOf(guardada.getId()))
                .header("X-Numero-Envio", String.valueOf(guardada.getNumeroEnvio()))
                .header("X-Siguiente-Numero", String.valueOf(archivo.siguienteNumeroEnvio()))
                .body(contenido);
    }

    private static ResponseEntity<String> rechazo(HttpStatus estado, String motivo) {
        return ResponseEntity.status(estado)
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(motivo);
    }

    // --- Pantalla 3: hojas antiguas ---

    @GetMapping("/info-embalaje/antiguos")
    public String antiguos(@RequestParam(required = false) Long id, Model model) {
        List<ResumenHojaEmbalaje> hojas = archivo.todas();
        // Sin id se enseña la más reciente: el panel vacío no ayuda a nadie.
        Long elegidaId = id != null ? id : hojas.stream().findFirst().map(ResumenHojaEmbalaje::id).orElse(null);
        model.addAttribute("hojas", hojas);
        model.addAttribute("elegida", archivo.datosDe(elegidaId).orElse(null));
        model.addAttribute("elegidaId", elegidaId);
        return "info-embalaje-antiguos";
    }

    /**
     * Regenera el Word desde lo guardado. La foto no está en el snapshot: es la
     * principal del modelo en el catálogo de hoy, que es la que se gestiona en
     * {@code /catalogo}.
     */
    @GetMapping("/info-embalaje/antiguos/{id}/docx")
    public ResponseEntity<byte[]> descargarAntigua(@PathVariable Long id) throws IOException {
        Optional<DatosHojaEmbalaje> datos = archivo.datosDe(id);
        if (datos.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        byte[] foto = fotoPrincipal(datos.get().cliente(), datos.get().codigoModelo());
        byte[] contenido = docBuilder.generar(datos.get(), foto);
        return word(datos.get(), contenido).body(contenido);
    }

    /**
     * Lleva una hoja antigua al formulario como punto de partida. Si su
     * temporada sigue guardada (y es de pedido de ese cliente) se vuelve a leer
     * el pedido, para poder pasar a otro artículo; si no, o si ya no se deja
     * leer, el desplegable solo trae el artículo de la hoja. Eso no es un
     * error: la hoja guardada ya tiene todo lo que hace falta para repetirla o
     * corregirla, y una temporada borrada no debería impedirlo.
     */
    @PostMapping("/info-embalaje/importar")
    public String importar(@RequestParam Long id, RedirectAttributes redirect) {
        Optional<DatosHojaEmbalaje> datos = archivo.datosDe(id);
        if (datos.isEmpty()) {
            redirect.addFlashAttribute("error", "La hoja elegida ya no existe");
            return "redirect:/info-embalaje/antiguos";
        }
        DatosHojaEmbalaje partida = datos.get();
        Long temporadaId = archivo.hoja(id).map(HojaEmbalaje::getTemporadaId).orElse(null);
        Optional<TemporadaGuardada> guardada = temporadas.conPedidoParaElEnvio(temporadaId, partida.cliente());

        empezar(partida.cliente(), partida.temporada(), guardada.map(TemporadaGuardada::getId).orElse(null));
        enCurso.setPartida(partida);
        if (!partida.nombreCliente().isBlank()) {
            enCurso.setNombreCliente(partida.nombreCliente());
        }
        if (guardada.isPresent() && pedidoEmbalaje.soporta(partida.cliente())) {
            try {
                ArticulosEmbalaje leidos = pedidoEmbalaje.articulosDe(partida.cliente(), guardada.get().getExcel());
                enCurso.getArticulos().addAll(leidos.articulos());
                enCurso.getAvisos().addAll(leidos.avisos());
            } catch (IOException | RuntimeException e) {
                enCurso.getAvisos().add("El pedido de la temporada " + guardada.get().getTemporada()
                        + " no se ha podido leer: solo se ofrece el artículo de la hoja importada");
            }
        }
        if (elegido(partida).isEmpty()) {
            if (!enCurso.getArticulos().isEmpty()) {
                enCurso.getAvisos().add("La referencia " + partida.referencia() + " · " + partida.codigoColor()
                        + " de la hoja importada ya no está en el pedido de la temporada");
            }
            enCurso.getArticulos().add(0, articuloDe(partida));
        }
        return "redirect:/info-embalaje/formulario";
    }

    // --- Internos ---

    /** Deja la sesión lista para un pedido nuevo, sin nada del anterior. */
    private void empezar(String cliente, String temporada, Long temporadaId) {
        enCurso.reiniciar();
        enCurso.setCliente(cliente);
        enCurso.setNombreCliente(nombreDe(cliente));
        enCurso.setTemporada(temporada);
        enCurso.setTemporadaId(temporadaId);
    }

    /** El artículo del pedido cargado que corresponde a la hoja importada. */
    private Optional<ArticuloEmbalaje> elegido(DatosHojaEmbalaje partida) {
        return enCurso.getArticulos().stream()
                .filter(a -> a.referencia().equals(partida.referencia())
                        && a.codigoColor().equals(partida.codigoColor()))
                .findFirst();
    }

    /**
     * El artículo de una hoja guardada, para el desplegable cuando no hay
     * pedido del que sacarlo. La foto se busca en el catálogo igual que al
     * descargar una antigua: si no, la misma hoja saldría con foto desde la
     * pantalla de antiguos y sin ella al generarla desde el formulario.
     */
    private ArticuloEmbalaje articuloDe(DatosHojaEmbalaje datos) {
        Long fotoId = fotos.principalDe(datos.cliente(), datos.codigoModelo()).orElse(null);
        return new ArticuloEmbalaje(datos.referencia(), datos.codigoColor(), datos.descripcionColor(),
                datos.nombreBolso(), datos.codigoModelo(), fotoId, datos.medidaCaja(), datos.unidadesPorCaja(),
                datos.bolsaTela(), datos.etiquetaInterior(), datos.instruccionEspecial(), datos.destinaciones());
    }

    private byte[] fotoPrincipal(String cliente, String codigoModelo) {
        return fotos.principalDe(cliente, codigoModelo)
                .flatMap(fotos::conBytes)
                .map(FotoCatalogo::getJpeg).orElse(null);
    }

    private static ResponseEntity.BodyBuilder word(DatosHojaEmbalaje datos, byte[] contenido) {
        return ResponseEntity.ok()
                .contentType(TIPO_DOCX)
                .contentLength(contenido.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(InfoEmbalajeDocBuilder.nombreFichero(datos), StandardCharsets.UTF_8)
                        .build().toString());
    }

    /**
     * Catálogo para el desplegable: todos los clientes, marcando los que aún
     * no tienen lector de pedido para que la pantalla los apague en vez de
     * esconderlos (la misma forma que las etiquetas de artículo).
     */
    private Map<String, Map<String, String>> vistaDeClientes() {
        Map<String, Map<String, String>> vista = new LinkedHashMap<>();
        clientesProperties.getClientes().forEach((clave, config) -> {
            Map<String, String> datos = new LinkedHashMap<>();
            datos.put("nombre", nombreDe(config, clave));
            datos.put("disponible", String.valueOf(pedidoEmbalaje.soporta(clave)));
            datos.put("placeholderTemporada", config.getPlaceholderTemporada() != null
                    ? config.getPlaceholderTemporada() : "");
            vista.put(clave, datos);
        });
        return vista;
    }

    private String nombreDe(String clave) {
        ClienteConfig config = clientesProperties.getClientes().get(clave);
        return config == null ? clave : nombreDe(config, clave);
    }

    private static String nombreDe(ClienteConfig config, String clave) {
        return config.getNombre() != null && !config.getNombre().isBlank() ? config.getNombre() : clave;
    }
}
