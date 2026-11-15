package com.puntotres.packinglist.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.CacheControl;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.config.BibliotecaPielesProperties;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.persistence.DocumentoPiel;
import com.puntotres.packinglist.persistence.FichaPiel;
import com.puntotres.packinglist.persistence.PrecioPiel;
import com.puntotres.packinglist.persistence.Proveedor;
import com.puntotres.packinglist.persistence.TipoEventoPiel;
import com.puntotres.packinglist.service.corte.ConversionFotos;
import com.puntotres.packinglist.service.corte.ConversorFotos;
import com.puntotres.packinglist.service.corte.FotoModelo;
import com.puntotres.packinglist.service.pieles.BibliotecaPieles;
import com.puntotres.packinglist.service.pieles.BibliotecaPieles.Alta;
import com.puntotres.packinglist.service.pieles.BibliotecaPieles.Fila;
import com.puntotres.packinglist.service.pieles.BibliotecaPieles.Filtro;
import com.puntotres.packinglist.service.pieles.BibliotecaPieles.SinFicha;
import com.puntotres.packinglist.service.pieles.BibliotecaPieles.Tramo;
import com.puntotres.packinglist.service.pieles.Rango;

/**
 * La biblioteca de pieles: tres pantallas.
 *
 * - {@code /biblioteca-pieles}: TODAS las pieles en una tabla. Arriba las
 *   que tienen ficha (proveedor, tipo, grueso, precios...), con sus datos
 *   principales editables en la propia casilla; debajo las pieles de cliente
 *   que aún no tienen ficha, que son las que entraron desde producción con
 *   solo su referencia y su nombre. A una de esas se le PROPONE la ficha que
 *   se llama como ella, y se enlaza solo cuando alguien lo acepta.
 * - {@code /biblioteca-pieles/{id}}: la ficha entera de una piel, con sus
 *   tramos de precio, fotos, documentos, las referencias de cliente
 *   enlazadas y su recorrido.
 * - {@code /biblioteca-pieles/proveedores}: los proveedores y su valoración.
 *
 * Cambiar una casilla de las tablas va por fetch y contesta JSON, como en el
 * catálogo y en colores: recargando se perdería lo tecleado en las demás. Lo
 * que el servicio rechaza (un grueso que no es un número, un tipo que no
 * está en la lista) vuelve como 400 con el motivo, y la casilla lo enseña.
 *
 * Sin login, por decisión del usuario: como el resto de pantallas de esta
 * aplicación, se ve desde cualquier equipo de la red local.
 */
@Controller
public class BibliotecaPielesController {

    private static final String BASE = "/biblioteca-pieles";

    /** Lo que llega del formulario de la ficha: todo texto, tal como se teclea. */
    public static class FichaForm {

        private String proveedor;
        private String nombre;
        private String refProveedor;
        private String animal;
        private String tipo;
        private String grueso;
        private String superficie;
        private String stockService;
        private String moqPorColor;
        private String plazoEntregaDias;
        private String fechaPrecios;
        private String calidad;
        private String notaCalidad;
        private String comentarios;
        private List<TramoForm> tramos = new ArrayList<>();

        public String getProveedor() { return proveedor; }
        public void setProveedor(String proveedor) { this.proveedor = proveedor; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getRefProveedor() { return refProveedor; }
        public void setRefProveedor(String refProveedor) { this.refProveedor = refProveedor; }
        public String getAnimal() { return animal; }
        public void setAnimal(String animal) { this.animal = animal; }
        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }
        public String getGrueso() { return grueso; }
        public void setGrueso(String grueso) { this.grueso = grueso; }
        public String getSuperficie() { return superficie; }
        public void setSuperficie(String superficie) { this.superficie = superficie; }
        public String getStockService() { return stockService; }
        public void setStockService(String stockService) { this.stockService = stockService; }
        public String getMoqPorColor() { return moqPorColor; }
        public void setMoqPorColor(String moqPorColor) { this.moqPorColor = moqPorColor; }
        public String getPlazoEntregaDias() { return plazoEntregaDias; }
        public void setPlazoEntregaDias(String plazoEntregaDias) { this.plazoEntregaDias = plazoEntregaDias; }
        public String getFechaPrecios() { return fechaPrecios; }
        public void setFechaPrecios(String fechaPrecios) { this.fechaPrecios = fechaPrecios; }
        public String getCalidad() { return calidad; }
        public void setCalidad(String calidad) { this.calidad = calidad; }
        public String getNotaCalidad() { return notaCalidad; }
        public void setNotaCalidad(String notaCalidad) { this.notaCalidad = notaCalidad; }
        public String getComentarios() { return comentarios; }
        public void setComentarios(String comentarios) { this.comentarios = comentarios; }
        public List<TramoForm> getTramos() { return tramos; }
        public void setTramos(List<TramoForm> tramos) { this.tramos = tramos; }
    }

    /** Un tramo de precio del formulario: desde cuántos m² y a cuántos €/m². */
    public static class TramoForm {

        private String moq;
        private String precio;

        public String getMoq() { return moq; }
        public void setMoq(String moq) { this.moq = moq; }
        public String getPrecio() { return precio; }
        public void setPrecio(String precio) { this.precio = precio; }
    }

    /** Un tramo ya guardado, con los números como se enseñan (coma decimal). */
    public record TramoVista(String moq, String precio) {
    }

    private final BibliotecaPieles biblioteca;
    private final BibliotecaPielesProperties listas;
    private final ClientesProperties clientesProperties;
    private final ConversorFotos conversor;

    public BibliotecaPielesController(BibliotecaPieles biblioteca, BibliotecaPielesProperties listas,
                                      ClientesProperties clientesProperties, ConversorFotos conversor) {
        this.biblioteca = biblioteca;
        this.listas = listas;
        this.clientesProperties = clientesProperties;
        this.conversor = conversor;
    }

    // --- La tabla ---

    @GetMapping(BASE)
    public String tabla(@RequestParam(required = false) String q,
                        @RequestParam(required = false) Long proveedor,
                        @RequestParam(required = false) String cliente,
                        @RequestParam(required = false) String animal,
                        @RequestParam(required = false) String tipo,
                        @RequestParam(required = false) String grueso,
                        @RequestParam(required = false) String stock,
                        @RequestParam(required = false, defaultValue = "false") boolean sinFicha,
                        Model model) {
        Rango rangoGrueso = Rango.VACIO;
        try {
            rangoGrueso = Rango.de(grueso);
        } catch (IllegalArgumentException e) {
            model.addAttribute("mensajeError", "Filtro de grueso: " + e.getMessage());
        }
        Boolean conStock = "si".equals(stock) ? Boolean.TRUE : "no".equals(stock) ? Boolean.FALSE : null;
        Filtro filtro = new Filtro(q, proveedor, cliente, animal, tipo, rangoGrueso.minimo(),
                rangoGrueso.maximo(), conStock);
        List<Fila> filas = sinFicha ? List.of() : biblioteca.buscar(filtro);
        // Las pieles sin ficha no tienen proveedor, animal, tipo ni grueso: con
        // uno de esos filtros puesto no se sabe si pasarían, así que no salen.
        boolean filtroSoloDeFichas = proveedor != null || !blanco(animal) || !blanco(tipo)
                || !rangoGrueso.vacio() || conStock != null;
        List<SinFicha> sueltas = filtroSoloDeFichas ? List.of() : biblioteca.sinFicha().stream()
                .filter(suelta -> blanco(cliente) || suelta.piel().getCliente().equals(clave(cliente)))
                .filter(suelta -> blanco(q) || suelta.piel().getRefPiel().contains(clave(q))
                        || suelta.piel().getNombre().toUpperCase(Locale.ROOT).contains(clave(q)))
                .toList();
        model.addAttribute("filas", filas);
        model.addAttribute("sueltas", sueltas);
        model.addAttribute("filtroSoloDeFichas", filtroSoloDeFichas);
        model.addAttribute("hayFiltro", !filtro.vacio() || sinFicha);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("proveedorElegido", proveedor);
        model.addAttribute("clienteElegido", blanco(cliente) ? "" : clave(cliente));
        model.addAttribute("animalElegido", animal == null ? "" : animal);
        model.addAttribute("tipoElegido", tipo == null ? "" : tipo);
        model.addAttribute("grueso", grueso == null ? "" : grueso);
        model.addAttribute("stock", stock == null ? "" : stock);
        model.addAttribute("sinFicha", sinFicha);
        model.addAttribute("proveedores", biblioteca.proveedores());
        model.addAttribute("clientes", clientes());
        model.addAttribute("animales", listas.getAnimales());
        model.addAttribute("tipos", listas.getTipos());
        model.addAttribute("animalPorDefecto", listas.animalPorDefecto());
        // Para el desplegable de «enlazar con»: todas las fichas, filtradas o no.
        model.addAttribute("todasLasFichas", sueltas.isEmpty() ? List.of() : biblioteca.buscar(Filtro.NINGUNO));
        return "biblioteca-pieles";
    }

    /**
     * Alta rápida: proveedor y nombre, y poco más. «Buscada para» apunta el
     * primer paso del recorrido con la fecha de hoy, y si el alta viene de una
     * piel de cliente sin ficha («Crear ficha»), la deja enlazada.
     */
    @PostMapping(BASE + "/nueva")
    public String nueva(@RequestParam String proveedor, @RequestParam String nombre,
                        @RequestParam(required = false) String animal,
                        @RequestParam(required = false) String tipo,
                        @RequestParam(required = false) String buscadaPara,
                        @RequestParam(required = false) String enlazarCliente,
                        @RequestParam(required = false) String enlazarRef,
                        RedirectAttributes redirect) {
        Alta alta;
        try {
            alta = biblioteca.crear(proveedor, nombre, animal, tipo);
            if (!blanco(enlazarCliente) && !blanco(enlazarRef)) {
                biblioteca.enlazar(enlazarCliente, enlazarRef, alta.ficha().getId());
            }
            if (!alta.yaExistia() && !blanco(buscadaPara)) {
                biblioteca.apuntar(alta.ficha().getId(), LocalDate.now(), buscadaPara, TipoEventoPiel.BUSCADA,
                        null, null);
            }
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
            return "redirect:" + BASE;
        }
        redirect.addFlashAttribute("mensaje", alta.yaExistia()
                ? "Ese proveedor ya tenía la piel " + alta.ficha().getNombre() + ": es esta."
                : "Piel " + alta.ficha().getNombre() + " dada de alta. Completa lo que sepas de ella.");
        return "redirect:" + BASE + "/" + alta.ficha().getId();
    }

    /** Cambia una casilla de la tabla sin recargar; contesta el valor tal como ha quedado guardado. */
    @PostMapping(BASE + "/{id}/campo")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> campo(@PathVariable Long id, @RequestParam String campo,
                                                     @RequestParam(required = false) String valor) {
        try {
            FichaPiel ficha = biblioteca.guardarCampo(id, campo, valor);
            return ResponseEntity.ok(Map.of("ok", true, "valor", valorDe(ficha, campo)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", e.getMessage()));
        }
    }

    /** El valor de un campo como se enseña en su casilla, para devolverlo ya normalizado. */
    private String valorDe(FichaPiel ficha, String campo) {
        return switch (campo) {
            case "nombre" -> ficha.getNombre();
            case "refProveedor" -> ficha.getRefProveedor();
            case "animal" -> ficha.getAnimal();
            case "tipo" -> ficha.getTipo();
            case "grueso" -> new Rango(ficha.getGruesoMinMm(), ficha.getGruesoMaxMm()).texto();
            case "superficie" -> new Rango(ficha.getSuperficieMinM2(), ficha.getSuperficieMaxM2()).texto();
            case "stockService" -> siNo(ficha.getStockService());
            case "moqPorColor" -> siNo(ficha.getMoqPorColor());
            case "plazoEntregaDias" -> ficha.getPlazoEntregaDias() == null ? "" : ficha.getPlazoEntregaDias().toString();
            case "calidad" -> ficha.getCalidad() == null ? "" : ficha.getCalidad().toString();
            case "notaCalidad" -> ficha.getNotaCalidad();
            case "comentarios" -> ficha.getComentarios();
            default -> "";
        };
    }

    // --- La ficha ---

    @GetMapping(BASE + "/{id}")
    public String ficha(@PathVariable Long id, Model model) {
        FichaPiel ficha = biblioteca.ficha(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Proveedor proveedor = biblioteca.proveedor(ficha.getProveedorId()).orElse(null);
        List<TramoVista> tramos = new ArrayList<>();
        for (PrecioPiel tramo : biblioteca.preciosDe(id)) {
            tramos.add(new TramoVista(Rango.formato(tramo.getMoqM2()), Rango.formato(tramo.getPrecioEurM2())));
        }
        // Siempre dos filas en blanco al final: para añadir un tramo no hace falta ningún botón.
        tramos.add(new TramoVista("", ""));
        tramos.add(new TramoVista("", ""));
        model.addAttribute("ficha", ficha);
        model.addAttribute("proveedor", proveedor);
        model.addAttribute("grueso", new Rango(ficha.getGruesoMinMm(), ficha.getGruesoMaxMm()).texto());
        model.addAttribute("superficie", new Rango(ficha.getSuperficieMinM2(), ficha.getSuperficieMaxM2()).texto());
        model.addAttribute("stockService", siNo(ficha.getStockService()));
        model.addAttribute("moqPorColor", siNo(ficha.getMoqPorColor()));
        model.addAttribute("tramos", tramos);
        model.addAttribute("fotos", biblioteca.fotosDe(id));
        model.addAttribute("documentos", biblioteca.documentosDe(id));
        model.addAttribute("enlazadas", biblioteca.enlazadas(id));
        model.addAttribute("recorrido", biblioteca.recorrido(id));
        model.addAttribute("tiposEvento", TipoEventoPiel.values());
        model.addAttribute("hoy", LocalDate.now());
        model.addAttribute("proveedores", biblioteca.proveedores());
        model.addAttribute("clientes", clientes());
        model.addAttribute("animales", conActual(listas.getAnimales(), ficha.getAnimal()));
        model.addAttribute("tipos", conActual(listas.getTipos(), ficha.getTipo()));
        return "biblioteca-pieles-ficha";
    }

    /** El formulario entero de la ficha, con sus tramos de precio. O se guarda todo o nada. */
    @PostMapping(BASE + "/{id}")
    public String guardar(@PathVariable Long id, @ModelAttribute FichaForm form, RedirectAttributes redirect) {
        try {
            List<Tramo> tramos = new ArrayList<>();
            for (TramoForm tramo : form.getTramos()) {
                if (tramo != null) {
                    tramos.add(new Tramo(BibliotecaPieles.numero(tramo.getMoq(), "El mínimo de un tramo"),
                            BibliotecaPieles.numero(tramo.getPrecio(), "El precio de un tramo")));
                }
            }
            // En el orden de la pantalla. El proveedor antes que el nombre: si
            // cambian los dos, la comprobación de repetidos mira el definitivo.
            Map<String, String> campos = new LinkedHashMap<>();
            campos.put("proveedor", form.getProveedor());
            campos.put("nombre", form.getNombre());
            campos.put("refProveedor", form.getRefProveedor());
            campos.put("animal", form.getAnimal());
            campos.put("tipo", form.getTipo());
            campos.put("grueso", form.getGrueso());
            campos.put("superficie", form.getSuperficie());
            campos.put("stockService", form.getStockService());
            campos.put("moqPorColor", form.getMoqPorColor());
            campos.put("plazoEntregaDias", form.getPlazoEntregaDias());
            campos.put("fechaPrecios", form.getFechaPrecios());
            campos.put("calidad", form.getCalidad());
            campos.put("notaCalidad", form.getNotaCalidad());
            campos.put("comentarios", form.getComentarios());
            biblioteca.guardar(id, campos);
            biblioteca.guardarPrecios(id, tramos);
            redirect.addFlashAttribute("mensaje", "Guardado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("mensajeError", "No se ha guardado nada: " + e.getMessage());
        }
        return volverA(id);
    }

    @PostMapping(BASE + "/{id}/duplicar")
    public String duplicar(@PathVariable Long id, @RequestParam String nombre, RedirectAttributes redirect) {
        try {
            FichaPiel copia = biblioteca.duplicar(id, nombre);
            redirect.addFlashAttribute("mensaje", "Piel " + copia.getNombre()
                    + " creada con los datos de la anterior. Cambia lo que sea distinto.");
            return volverA(copia.getId());
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
            return volverA(id);
        }
    }

    @PostMapping(BASE + "/{id}/borrar")
    public String borrar(@PathVariable Long id, RedirectAttributes redirect) {
        String nombre = biblioteca.ficha(id).map(FichaPiel::getNombre).orElse("");
        biblioteca.borrar(id);
        redirect.addFlashAttribute("mensaje", "Piel " + nombre + " borrada de la biblioteca. Las pieles de "
                + "cliente que apuntaban a ella siguen en su sitio, ahora sin ficha.");
        return "redirect:" + BASE;
    }

    // --- Fotos ---

    @GetMapping(BASE + "/foto/{fotoId}")
    public ResponseEntity<byte[]> foto(@PathVariable Long fotoId) {
        return biblioteca.fotoConBytes(fotoId)
                .map(foto -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                        .body(foto.getJpeg()))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Varias fotos a la vez, HEIC incluido: pasan por el conversor del corte
     * (reduce a 1.600 px y endereza por el EXIF) en un directorio temporal
     * que se borra al acabar. Una que no se deja leer no impide guardar las
     * demás: se dice cuál.
     */
    @PostMapping(BASE + "/{id}/fotos")
    public String subirFotos(@PathVariable Long id, @RequestParam("fotos") List<MultipartFile> subidas,
                             RedirectAttributes redirect) {
        List<MultipartFile> conContenido = subidas.stream()
                .filter(fichero -> !fichero.isEmpty() && fichero.getOriginalFilename() != null).toList();
        if (conContenido.isEmpty()) {
            redirect.addFlashAttribute("mensajeError", "Elige al menos una foto.");
            return volverA(id);
        }
        Path directorio = null;
        try {
            directorio = Files.createTempDirectory("biblioteca-pieles-fotos-");
            List<FotoModelo> originales = new ArrayList<>();
            int numero = 0;
            for (MultipartFile fichero : conContenido) {
                String nombre = soloNombre(fichero.getOriginalFilename());
                // La extensión va al nombre de un fichero temporal: solo si es de las normales.
                String extension = nombre.matches("(?i).*\\.[a-z0-9]{1,5}")
                        ? nombre.substring(nombre.lastIndexOf('.')) : ".jpg";
                Path copia = directorio.resolve(String.format("%05d%s", ++numero, extension));
                fichero.transferTo(copia);
                originales.add(new FotoModelo("PIEL", nombre, copia));
            }
            ConversionFotos conversion = conversor.convertir(originales, directorio);
            conversion.esperar();
            int guardadas = 0;
            for (FotoModelo foto : originales) {
                Optional<Path> reducida = conversion.reducida(foto);
                if (reducida.isPresent()) {
                    biblioteca.guardarFoto(id, foto.nombreOriginal(), Files.readAllBytes(reducida.get()));
                    guardadas++;
                }
            }
            redirect.addFlashAttribute("mensaje", guardadas + (guardadas == 1 ? " foto guardada." : " fotos guardadas."));
            if (!conversion.avisos().isEmpty()) {
                redirect.addFlashAttribute("aviso", String.join(" ", conversion.avisos()));
            }
        } catch (IOException | RuntimeException e) {
            redirect.addFlashAttribute("mensajeError", "No se pudieron guardar las fotos: "
                    + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            redirect.addFlashAttribute("mensajeError", "Se ha interrumpido la conversión de las fotos.");
        } finally {
            CorteEnCurso.borrar(directorio);
        }
        return volverA(id);
    }

    @PostMapping(BASE + "/foto/{fotoId}/principal")
    public String fotoPrincipal(@PathVariable Long fotoId, @RequestParam Long ficha) {
        biblioteca.marcarPrincipal(fotoId);
        return volverA(ficha);
    }

    @PostMapping(BASE + "/foto/{fotoId}/borrar")
    public String borrarFoto(@PathVariable Long fotoId, @RequestParam Long ficha) {
        biblioteca.borrarFoto(fotoId);
        return volverA(ficha);
    }

    // --- Documentos ---

    /** Fichas técnicas y ofertas, tal como llegan. El tamaño se mira ANTES de leer los bytes. */
    @PostMapping(BASE + "/{id}/documentos")
    public String subirDocumentos(@PathVariable Long id, @RequestParam("documentos") List<MultipartFile> subidos,
                                  RedirectAttributes redirect) {
        int guardados = 0;
        List<String> rechazados = new ArrayList<>();
        for (MultipartFile fichero : subidos) {
            if (fichero.isEmpty() || fichero.getOriginalFilename() == null) {
                continue;
            }
            String nombre = soloNombre(fichero.getOriginalFilename());
            if (fichero.getSize() > BibliotecaPieles.MAX_BYTES_DOCUMENTO) {
                rechazados.add(nombre + " (pesa más de "
                        + BibliotecaPieles.MAX_BYTES_DOCUMENTO / (1024 * 1024) + " MB)");
                continue;
            }
            try {
                biblioteca.guardarDocumento(id, nombre, fichero.getContentType(), fichero.getBytes());
                guardados++;
            } catch (IOException | IllegalArgumentException e) {
                rechazados.add(nombre + (e.getMessage() != null ? " (" + e.getMessage() + ")" : ""));
            }
        }
        if (guardados > 0) {
            redirect.addFlashAttribute("mensaje", guardados
                    + (guardados == 1 ? " documento guardado." : " documentos guardados."));
        }
        if (!rechazados.isEmpty()) {
            redirect.addFlashAttribute("mensajeError", "No se han guardado: " + String.join(", ", rechazados) + ".");
        } else if (guardados == 0) {
            redirect.addFlashAttribute("mensajeError", "Elige al menos un documento.");
        }
        return volverA(id);
    }

    /**
     * Un PDF se abre en el navegador; cualquier otra cosa se descarga. El
     * tipo lo declara quien sube el fichero, así que nada más se sirve en
     * línea: un HTML subido como ficha técnica sería una página de este
     * mismo sitio.
     */
    @GetMapping(BASE + "/documento/{documentoId}")
    public ResponseEntity<byte[]> documento(@PathVariable Long documentoId) {
        DocumentoPiel documento = biblioteca.documento(documentoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean pdf = MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(documento.getMime());
        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder(pdf ? "inline" : "attachment")
                        .filename(documento.getNombreFichero(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(documento.getBytes());
    }

    @PostMapping(BASE + "/documento/{documentoId}/borrar")
    public String borrarDocumento(@PathVariable Long documentoId, @RequestParam Long ficha) {
        biblioteca.borrarDocumento(documentoId);
        return volverA(ficha);
    }

    // --- Recorrido ---

    @PostMapping(BASE + "/{id}/eventos")
    public String apuntar(@PathVariable Long id, @RequestParam(required = false) String fecha,
                          @RequestParam String cliente, @RequestParam String tipo,
                          @RequestParam(required = false) String modelo,
                          @RequestParam(required = false) String nota, RedirectAttributes redirect) {
        try {
            LocalDate dia = blanco(fecha) ? LocalDate.now() : LocalDate.parse(fecha.trim());
            biblioteca.apuntar(id, dia, cliente, TipoEventoPiel.valueOf(tipo), modelo, nota);
            redirect.addFlashAttribute("mensaje", "Apuntado en el recorrido.");
        } catch (DateTimeParseException e) {
            redirect.addFlashAttribute("mensajeError", "«" + fecha + "» no es una fecha.");
        } catch (IllegalArgumentException e) {
            // También el valueOf de un tipo que no existe, que no trae un mensaje para el usuario.
            redirect.addFlashAttribute("mensajeError", e.getMessage() != null && e.getMessage().startsWith("El evento")
                    ? e.getMessage() : "No se ha podido apuntar: revisa el cliente y qué pasó.");
        }
        return volverA(id);
    }

    @PostMapping(BASE + "/evento/{eventoId}/borrar")
    public String borrarEvento(@PathVariable Long eventoId, @RequestParam Long ficha) {
        biblioteca.borrarEvento(eventoId);
        return volverA(ficha);
    }

    // --- Enlaces con las pieles de cliente ---

    /**
     * Enlaza una piel de cliente con una ficha: desde la tabla (aceptar una
     * propuesta o elegir la ficha en el desplegable) o desde la propia ficha
     * (tecleando cliente y referencia). {@code volver=ficha} vuelve a la ficha.
     */
    @PostMapping(BASE + "/enlazar")
    public String enlazar(@RequestParam String cliente, @RequestParam String refPiel,
                          @RequestParam(required = false) Long ficha,
                          @RequestParam(required = false) String volver, RedirectAttributes redirect) {
        boolean aLaFicha = "ficha".equals(volver) && ficha != null;
        if (ficha == null) {
            redirect.addFlashAttribute("mensajeError", "Elige con qué piel de la biblioteca se enlaza.");
            return "redirect:" + BASE;
        }
        if (clientesProperties.clientePara(cliente).isEmpty()) {
            redirect.addFlashAttribute("mensajeError", "Cliente desconocido: " + cliente + ".");
            return aLaFicha ? volverA(ficha) : "redirect:" + BASE;
        }
        try {
            biblioteca.enlazar(cliente, refPiel, ficha);
            redirect.addFlashAttribute("mensaje", "Enlazada " + clave(cliente) + " " + clave(refPiel) + ".");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
        }
        return aLaFicha ? volverA(ficha) : "redirect:" + BASE;
    }

    @PostMapping(BASE + "/desenlazar")
    public String desenlazar(@RequestParam String cliente, @RequestParam String refPiel,
                             @RequestParam Long ficha, RedirectAttributes redirect) {
        biblioteca.desenlazar(cliente, refPiel);
        redirect.addFlashAttribute("mensaje", clave(cliente) + " " + clave(refPiel) + " ya no apunta a esta piel.");
        return volverA(ficha);
    }

    // --- Proveedores ---

    @GetMapping(BASE + "/proveedores")
    public String proveedores(Model model) {
        model.addAttribute("proveedores", biblioteca.proveedores());
        model.addAttribute("pielesPorProveedor", biblioteca.pielesPorProveedor());
        return "biblioteca-pieles-proveedores";
    }

    @PostMapping(BASE + "/proveedores/nuevo")
    public String nuevoProveedor(@RequestParam String nombre, RedirectAttributes redirect) {
        try {
            Proveedor proveedor = biblioteca.asegurarProveedor(nombre);
            redirect.addFlashAttribute("mensaje", "Proveedor " + proveedor.getNombre() + " guardado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:" + BASE + "/proveedores";
    }

    @PostMapping(BASE + "/proveedores/{id}/campo")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> campoProveedor(@PathVariable Long id, @RequestParam String campo,
                                                              @RequestParam(required = false) String valor) {
        try {
            Proveedor proveedor = biblioteca.guardarCampoProveedor(id, campo, valor);
            String guardado = switch (campo) {
                case "nombre" -> proveedor.getNombre();
                case "contacto" -> proveedor.getContacto();
                case "email" -> proveedor.getEmail();
                case "telefono" -> proveedor.getTelefono();
                case "pais" -> proveedor.getPais();
                case "condiciones" -> proveedor.getCondiciones();
                case "responsabilidad" -> texto(proveedor.getResponsabilidad());
                case "entregaATiempo" -> texto(proveedor.getEntregaATiempo());
                case "notaValoracion" -> proveedor.getNotaValoracion();
                default -> "";
            };
            return ResponseEntity.ok(Map.of("ok", true, "valor", guardado));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", e.getMessage()));
        }
    }

    @PostMapping(BASE + "/proveedores/{id}/borrar")
    public String borrarProveedor(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            biblioteca.borrarProveedor(id);
            redirect.addFlashAttribute("mensaje", "Proveedor borrado.");
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:" + BASE + "/proveedores";
    }

    // --- Internos ---

    /** Clave → nombre de todos los clientes del catálogo, en su orden. */
    private Map<String, String> clientes() {
        Map<String, String> clientes = new LinkedHashMap<>();
        clientesProperties.getClientes().forEach((clave, config) -> clientes.put(clave, nombreDe(config, clave)));
        return clientes;
    }

    private static String nombreDe(ClienteConfig config, String clave) {
        return config.getNombre() != null && !config.getNombre().isBlank() ? config.getNombre() : clave;
    }

    /**
     * La lista cerrada más el valor que la ficha ya tiene si no está en ella
     * (uno que se quitó del yml): sin esa opción el navegador elegiría otra y
     * guardar la ficha lo cambiaría en silencio.
     */
    private static List<String> conActual(List<String> lista, String actual) {
        if (actual == null || actual.isEmpty() || lista.contains(actual)) {
            return lista;
        }
        List<String> ampliada = new ArrayList<>(lista);
        ampliada.add(actual);
        return ampliada;
    }

    /**
     * El nombre del fichero subido sin la ruta que mande el navegador, con
     * cualquiera de las dos barras. A mano y no con {@code Paths.get}: el
     * nombre lo escribe quien sube el fichero, y un acento (en un servidor
     * sin UTF-8) o un carácter que el sistema no admite en una ruta lo haría
     * saltar con una excepción antes de guardar nada.
     */
    static String soloNombre(String nombreSubido) {
        String nombre = nombreSubido.substring(
                Math.max(nombreSubido.lastIndexOf('/'), nombreSubido.lastIndexOf('\\')) + 1).trim();
        return nombre.isEmpty() ? "fichero" : nombre;
    }

    private static String volverA(Long fichaId) {
        return "redirect:" + BASE + "/" + fichaId;
    }

    private static String siNo(Boolean valor) {
        return valor == null ? "" : valor.toString();
    }

    private static String texto(Integer valor) {
        return valor == null ? "" : valor.toString();
    }

    private static String clave(String texto) {
        return texto.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean blanco(String texto) {
        return texto == null || texto.isBlank();
    }
}
