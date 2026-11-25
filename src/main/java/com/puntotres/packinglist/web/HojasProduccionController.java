package com.puntotres.packinglist.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

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

import com.puntotres.packinglist.config.CatalogoClientes;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.model.DatosHojaProduccion;
import com.puntotres.packinglist.persistence.ArchivoHojasProduccion;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.FotoCatalogo;
import com.puntotres.packinglist.persistence.HojaProduccion;
import com.puntotres.packinglist.persistence.ResumenHojaProduccion;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;
import com.puntotres.packinglist.service.corte.ConversionFotos;
import com.puntotres.packinglist.service.corte.ConversorFotos;
import com.puntotres.packinglist.service.corte.FotoModelo;
import com.puntotres.packinglist.service.produccion.ArticuloProduccion;
import com.puntotres.packinglist.service.produccion.ArticulosProduccion;
import com.puntotres.packinglist.service.produccion.CatalogoProduccion;
import com.puntotres.packinglist.service.produccion.HojaProduccionExcelBuilder;
import com.puntotres.packinglist.service.produccion.OpcionesHojaProduccion;

/**
 * Hojas de Producción para los talleres externos: un formulario que genera el
 * excel y lo guarda, y la pantalla de hojas antiguas (verlas, bajarlas otra
 * vez o importarlas al formulario).
 *
 * Todo en una pantalla y sin sesión (petición del usuario): cliente y
 * temporada se eligen en el propio formulario y los artículos de esa
 * temporada, con las pieles de cada modelo, se piden por {@code fetch}
 * ({@link #catalogo}). Importar una hoja antigua es abrir el formulario con
 * {@code ?importar=<id>}.
 *
 * <b>La foto</b> sale, por orden: la subida en el formulario, la subida con la
 * hoja importada de la que se parte, o la principal del modelo en el catálogo.
 * Las dos primeras se guardan con la hoja; la del catálogo no, se vuelve a
 * buscar al regenerar, como en la info de embalaje.
 */
@Controller
public class HojasProduccionController {

    private static final MediaType TIPO_XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ArchivoHojasProduccion archivo;
    private final HojaProduccionExcelBuilder excel;
    private final ArticulosProduccion articulos;
    private final CatalogoClientes catalogoClientes;
    private final ArchivoTemporadas temporadas;
    private final FotosCatalogo fotos;
    private final ConversorFotos conversor;

    public HojasProduccionController(ArchivoHojasProduccion archivo, HojaProduccionExcelBuilder excel,
                                     ArticulosProduccion articulos, CatalogoClientes catalogoClientes,
                                     ArchivoTemporadas temporadas, FotosCatalogo fotos, ConversorFotos conversor) {
        this.archivo = archivo;
        this.excel = excel;
        this.articulos = articulos;
        this.catalogoClientes = catalogoClientes;
        this.temporadas = temporadas;
        this.fotos = fotos;
        this.conversor = conversor;
    }

    // --- El formulario ---

    @GetMapping("/hojas-produccion")
    public String formulario(@RequestParam(required = false) Long importar, Model model) {
        Optional<DatosHojaProduccion> importada = archivo.datosDe(importar);
        if (importar != null && importada.isEmpty()) {
            model.addAttribute("error", "La hoja elegida ya no existe: se abre el formulario vacío");
        }
        HojaProduccionForm formulario = importada.map(HojaProduccionForm::desde).orElseGet(() -> {
            HojaProduccionForm nuevo = new HojaProduccionForm();
            nuevo.setFechaSalida(LocalDate.now());
            nuevo.getLineas().add(new HojaProduccionForm.Linea());
            return nuevo;
        });
        if (formulario.getLineas().isEmpty()) {
            formulario.getLineas().add(new HojaProduccionForm.Linea());
        }
        // La foto de la hoja importada: la suya, si se subió con ella; si no,
        // la principal del modelo en el catálogo de hoy.
        importada.ifPresent(datos -> {
            if (archivo.fotoDe(importar).isPresent()) {
                formulario.setFotoHojaId(importar);
            } else {
                fotos.principalDe(datos.cliente(), datos.modeloParaFoto()).ifPresent(formulario::setFotoId);
            }
        });
        model.addAttribute("formulario", formulario);
        model.addAttribute("importada", importada.orElse(null));
        model.addAttribute("siguienteNumero", importada.map(d -> archivo.siguienteNumero(d.taller())).orElse(null));
        model.addAttribute("clientes", nombresDeClientes());
        model.addAttribute("temporadasJs", temporadas.paraElDesplegableDelCorte());
        model.addAttribute("talleres", talleres());
        model.addAttribute("siguientesPorTaller", archivo.siguientesPorTaller());
        model.addAttribute("marcadoPorTaller", OpcionesHojaProduccion.MARCADO_POR_TALLER);
        model.addAttribute("marcadoInterior", OpcionesHojaProduccion.MARCADO_INTERIOR);
        model.addAttribute("coloresMetal", OpcionesHojaProduccion.COLORES_METAL);
        model.addAttribute("fornitura", OpcionesHojaProduccion.FORNITURA);
        model.addAttribute("marcasHilo", OpcionesHojaProduccion.MARCAS_HILO);
        model.addAttribute("tamanosPorCliente", tamanosPorCliente());
        model.addAttribute("tamanoPorDefecto", tamanoPorDefecto());
        return "hojas-produccion";
    }

    /**
     * Lo que el formulario carga del catálogo de un cliente, o de una de sus
     * temporadas: los artículos del desplegable, con su texto ya tabulado como
     * el de la info de embalaje, y las pieles enlazadas a cada modelo.
     */
    @GetMapping(value = "/hojas-produccion/catalogo", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> catalogo(@RequestParam String cliente,
                                        @RequestParam(required = false) Long temporadaId) {
        CatalogoProduccion catalogo = articulos.de(cliente, temporadaId);
        List<ArticuloProduccion> lista = catalogo.articulos();
        List<String> textos = OpcionesArticuloEmbalaje.alinear(
                lista.stream().map(ArticuloProduccion::referenciaYColor).toList(),
                lista.stream().map(a -> a.tieneNombre() ? a.nombreBolso() : "").toList());
        List<Map<String, Object>> conTexto = new ArrayList<>();
        for (int i = 0; i < lista.size(); i++) {
            ArticuloProduccion a = lista.get(i);
            Map<String, Object> entrada = new LinkedHashMap<>();
            entrada.put("texto", textos.get(i));
            entrada.put("referencia", a.referencia());
            entrada.put("codigoColor", a.codigoColor());
            entrada.put("nombreBolso", a.nombreBolso());
            entrada.put("codigoModelo", a.codigoModelo());
            entrada.put("fotoId", a.fotoId());
            entrada.put("pielBase", a.pielBase());
            entrada.put("colorBase", a.colorBase());
            entrada.put("pielCombinacion", a.pielCombinacion());
            entrada.put("colorCombinacion", a.colorCombinacion());
            entrada.put("forro", a.forro());
            conTexto.add(entrada);
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("articulos", conTexto);
        respuesta.put("modelos", catalogo.modelos());
        return respuesta;
    }

    /**
     * Guarda la hoja y devuelve el excel. La pantalla lo pide con {@code fetch}
     * y descarga el blob sin recargar; dice con qué número se ha guardado
     * ({@code X-Numero}) y cuál es el siguiente libre de su taller
     * ({@code X-Siguiente-Numero}). Los rechazos van en texto plano, que la
     * pantalla enseña tal cual.
     */
    @PostMapping("/hojas-produccion/generar")
    public ResponseEntity<?> generar(@ModelAttribute HojaProduccionForm form,
                                     @RequestParam(name = "fotoSubida", required = false) MultipartFile fotoSubida)
            throws IOException {
        Optional<ClienteConfig> cliente = catalogoClientes.clientePara(form.getCliente());
        if (cliente.isEmpty()) {
            return rechazo("Elige el cliente: sin él no se ha guardado nada.");
        }
        if (form.getTaller() == null || form.getTaller().isBlank()) {
            return rechazo("Escribe el taller: cada taller lleva su numeración y sin él no se ha guardado nada.");
        }
        DatosHojaProduccion datos = form.aDatos(nombreDe(cliente.get(), form.getCliente()), archivo::siguienteNumero);
        if (datos.lineas().isEmpty()) {
            return rechazo("La hoja no tiene ninguna línea con datos: no se ha guardado nada.");
        }
        byte[] fotoPropia;
        try {
            fotoPropia = fotoPropia(form, fotoSubida);
        } catch (IOException e) {
            return rechazo("No se ha podido leer la foto subida: " + e.getMessage() + ". No se ha guardado nada.");
        }
        byte[] foto = fotoPropia != null ? fotoPropia : fotoDelCatalogo(form.getFotoId(), datos);
        // Primero el excel y luego guardar: una hoja que no se ha podido
        // escribir no debe gastar un número ni salir en las antiguas.
        byte[] contenido = excel.generar(datos, foto);
        HojaProduccion guardada = archivo.guardar(datos, fotoPropia);
        return descarga(datos, contenido)
                .header("X-Hoja-Id", String.valueOf(guardada.getId()))
                .header("X-Numero", String.valueOf(guardada.getNumero()))
                .header("X-Siguiente-Numero", String.valueOf(archivo.siguienteNumero(datos.taller())))
                .body(contenido);
    }

    // --- Hojas antiguas ---

    @GetMapping("/hojas-produccion/antiguas")
    public String antiguas(@RequestParam(required = false) Long id, Model model) {
        List<ResumenHojaProduccion> hojas = archivo.todas();
        Long elegidaId = id != null ? id : hojas.stream().findFirst().map(ResumenHojaProduccion::id).orElse(null);
        Optional<DatosHojaProduccion> elegida = archivo.datosDe(elegidaId);
        model.addAttribute("hojas", hojas);
        model.addAttribute("elegida", elegida.orElse(null));
        model.addAttribute("elegidaId", elegidaId);
        // La foto con la que se regeneraría: la suya o la principal del modelo en el catálogo.
        model.addAttribute("fotoUrl", elegida.flatMap(datos -> archivo.fotoDe(elegidaId).isPresent()
                ? Optional.of("/hojas-produccion/antiguas/" + elegidaId + "/foto")
                : fotos.principalDe(datos.cliente(), datos.modeloParaFoto()).map(foto -> "/catalogo/foto/" + foto))
                .orElse(null));
        return "hojas-produccion-antiguas";
    }

    /** Regenera el excel desde lo guardado: lo que se mandó, con su foto (la suya o la del catálogo). */
    @GetMapping("/hojas-produccion/antiguas/{id}/xlsx")
    public ResponseEntity<byte[]> descargarAntigua(@PathVariable Long id) throws IOException {
        Optional<DatosHojaProduccion> datos = archivo.datosDe(id);
        if (datos.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        byte[] foto = archivo.fotoDe(id).orElseGet(() -> fotoDelCatalogo(null, datos.get()));
        byte[] contenido = excel.generar(datos.get(), foto);
        return descarga(datos.get(), contenido).body(contenido);
    }

    /** La foto subida con una hoja, para enseñarla en el formulario al importarla. */
    @GetMapping("/hojas-produccion/antiguas/{id}/foto")
    public ResponseEntity<byte[]> fotoDeHoja(@PathVariable Long id) {
        return archivo.fotoDe(id)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.noCache())
                        .body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // --- Internos ---

    /**
     * La foto que se guarda con la hoja: la subida ahora (reducida a JPEG por
     * el conversor del corte, HEIC incluido) o la de la hoja importada. Null si
     * la hoja usa la del catálogo.
     */
    private byte[] fotoPropia(HojaProduccionForm form, MultipartFile subida) throws IOException {
        if (subida != null && !subida.isEmpty()) {
            return reducir(subida);
        }
        return archivo.fotoDe(form.getFotoHojaId()).orElse(null);
    }

    /** La foto del catálogo: la elegida con el artículo o, si no, la principal del modelo de la hoja. */
    private byte[] fotoDelCatalogo(Long fotoId, DatosHojaProduccion datos) {
        Optional<Long> id = Optional.ofNullable(fotoId)
                .or(() -> fotos.principalDe(datos.cliente(), datos.modeloParaFoto()));
        return id.flatMap(fotos::conBytes).map(FotoCatalogo::getJpeg).orElse(null);
    }

    private byte[] reducir(MultipartFile subida) throws IOException {
        Path directorio = Files.createTempDirectory("hoja-produccion-foto-");
        try {
            String original = subida.getOriginalFilename() == null ? "foto.jpg" : subida.getOriginalFilename();
            String nombre = Paths.get(original).getFileName().toString();
            String extension = nombre.contains(".") ? nombre.substring(nombre.lastIndexOf('.')) : ".jpg";
            Path copia = directorio.resolve("subida" + extension);
            subida.transferTo(copia);
            FotoModelo foto = new FotoModelo("hoja", nombre, copia);
            ConversionFotos conversion = conversor.convertir(List.of(foto), directorio);
            conversion.esperar();
            Optional<Path> reducida = conversion.reducida(foto);
            if (reducida.isEmpty()) {
                throw new IOException(conversion.avisos().isEmpty() ? "no es una imagen que se pueda leer"
                        : String.join(" ", conversion.avisos()));
            }
            return Files.readAllBytes(reducida.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("se ha interrumpido la conversión", e);
        } finally {
            CorteEnCurso.borrar(directorio);
        }
    }

    private static ResponseEntity<String> rechazo(String motivo) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(motivo);
    }

    private static ResponseEntity.BodyBuilder descarga(DatosHojaProduccion datos, byte[] contenido) {
        return ResponseEntity.ok()
                .contentType(TIPO_XLSX)
                .contentLength(contenido.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(HojaProduccionExcelBuilder.nombreFichero(datos), StandardCharsets.UTF_8)
                        .build().toString());
    }

    /** Los talleres que nombró el usuario más los ya usados, sin repetir y ordenados. */
    private List<String> talleres() {
        TreeSet<String> talleres = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        talleres.addAll(OpcionesHojaProduccion.TALLERES_CONOCIDOS);
        talleres.addAll(archivo.talleres());
        return List.copyOf(talleres);
    }

    private Map<String, String> nombresDeClientes() {
        Map<String, String> nombres = new LinkedHashMap<>();
        catalogoClientes.getClientes().forEach((clave, config) -> nombres.put(clave, nombreDe(config, clave)));
        return nombres;
    }

    private Map<String, List<String>> tamanosPorCliente() {
        Map<String, List<String>> tamanos = new LinkedHashMap<>();
        catalogoClientes.getClientes().keySet().forEach(clave -> tamanos.put(clave, OpcionesHojaProduccion.tamanosMarcado(clave)));
        return tamanos;
    }

    private Map<String, String> tamanoPorDefecto() {
        Map<String, String> porDefecto = new LinkedHashMap<>();
        catalogoClientes.getClientes().keySet().forEach(clave -> porDefecto.put(clave, OpcionesHojaProduccion.tamanoPorDefecto(clave)));
        return porDefecto;
    }

    private static String nombreDe(ClienteConfig config, String clave) {
        return config.getNombre() != null && !config.getNombre().isBlank() ? config.getNombre() : clave;
    }
}
