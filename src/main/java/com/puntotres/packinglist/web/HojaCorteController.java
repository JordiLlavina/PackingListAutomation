package com.puntotres.packinglist.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

import com.puntotres.packinglist.config.CatalogoClientes;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.model.DatosHojaCorte;
import com.puntotres.packinglist.persistence.ArchivoHojasCorte;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.Articulo;
import com.puntotres.packinglist.persistence.ArticuloColor;
import com.puntotres.packinglist.persistence.FotoCatalogo;
import com.puntotres.packinglist.persistence.HojaCorte;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;
import com.puntotres.packinglist.service.corte.ConversorFotos;
import com.puntotres.packinglist.service.corte.HojaCorteService;
import com.puntotres.packinglist.service.corte.Imagen;
import com.puntotres.packinglist.service.produccion.ArticuloProduccion;
import com.puntotres.packinglist.service.produccion.ArticulosProduccion;

/**
 * «Hoja de corte» (tercera tarjeta de los Documentos del Corte): la orden de
 * corte de UN modelo, con una línea por color, sin pasar por un excel de
 * pedido ni de producción. Cliente, temporada y artículo se eligen arriba
 * del formulario, como en la Hoja de Producción, y el artículo rellena la
 * cabecera y la línea de su color con lo que sabe el catálogo; o se empieza
 * desde cero, para un modelo que aún no está en el sistema.
 *
 * Cada hoja generada se guarda (histórico, decisión del usuario) para bajarla
 * otra vez o importarla al formulario; no escribe en el catálogo ni se
 * numera. Todo en una página y sin sesión.
 */
@Controller
public class HojaCorteController {

    private static final MediaType TIPO_XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final HojaCorteService servicio;
    private final ArchivoHojasCorte archivo;
    private final ArticulosProduccion articulos;
    private final CatalogoModelos catalogo;
    private final CatalogoClientes catalogoClientes;
    private final ArchivoTemporadas temporadas;
    private final FotosCatalogo fotos;
    private final ConversorFotos conversor;

    public HojaCorteController(HojaCorteService servicio, ArchivoHojasCorte archivo, ArticulosProduccion articulos,
                               CatalogoModelos catalogo, CatalogoClientes catalogoClientes,
                               ArchivoTemporadas temporadas, FotosCatalogo fotos, ConversorFotos conversor) {
        this.servicio = servicio;
        this.archivo = archivo;
        this.articulos = articulos;
        this.catalogo = catalogo;
        this.catalogoClientes = catalogoClientes;
        this.temporadas = temporadas;
        this.fotos = fotos;
        this.conversor = conversor;
    }

    @GetMapping("/documentos-corte/hoja")
    public String formulario(@RequestParam(required = false) Long importar, Model model) {
        Optional<DatosHojaCorte> importada = archivo.datosDe(importar);
        if (importar != null && importada.isEmpty()) {
            model.addAttribute("error", "La hoja elegida ya no existe: se abre el formulario vacío");
        }
        HojaCorteForm formulario = importada.map(HojaCorteForm::desde).orElseGet(HojaCorteForm::new);
        if (formulario.getLineas().isEmpty()) {
            formulario.getLineas().add(new HojaCorteForm.Linea());
        }
        importada.ifPresent(datos -> {
            if (archivo.fotoDe(importar).isPresent()) {
                formulario.setFotoHojaId(importar);
            } else {
                fotos.principalDe(datos.cliente(), datos.modeloParaFoto()).ifPresent(formulario::setFotoId);
            }
        });
        Map<String, String> clientes = new LinkedHashMap<>();
        catalogoClientes.getClientes().forEach((clave, config) -> clientes.put(clave, nombreDe(config, clave)));
        model.addAttribute("formulario", formulario);
        model.addAttribute("importada", importada.isPresent());
        model.addAttribute("clientes", clientes);
        model.addAttribute("temporadasJs", temporadas.paraElDesplegableDelCorte());
        return "hoja-corte";
    }

    /**
     * Los artículos del catálogo del cliente (o de una temporada suya) con su
     * texto ya tabulado como en la info de embalaje, y lo que la hoja de corte
     * rellena de cada uno: la piel, el forro y las cuatro combinaciones, cada
     * una con su color en ese color del bolso.
     */
    @GetMapping(value = "/documentos-corte/hoja/catalogo", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> catalogo(@RequestParam String cliente, @RequestParam(required = false) Long temporadaId) {
        List<ArticuloProduccion> lista = articulos.de(cliente, temporadaId).articulos();
        List<String> textos = OpcionesArticuloEmbalaje.alinear(
                lista.stream().map(ArticuloProduccion::referenciaYColor).toList(),
                lista.stream().map(a -> a.tieneNombre() ? a.nombreBolso() : "").toList());
        List<Map<String, Object>> salida = new ArrayList<>();
        for (int i = 0; i < lista.size(); i++) {
            ArticuloProduccion a = lista.get(i);
            Optional<Articulo> articulo = catalogo.articulo(cliente, a.referencia());
            Optional<ArticuloColor> color = catalogo.coloresDe(cliente, a.referencia()).stream()
                    .filter(c -> c.getCodigoColor().equalsIgnoreCase(a.codigoColor())).findFirst();
            List<String> nombres = articulo.map(Articulo::getCombinaciones).orElse(List.of());
            List<String> colores = color.map(ArticuloColor::getColoresCombinacion).orElse(List.of());
            List<Map<String, String>> combinaciones = new ArrayList<>();
            for (int c = 0; c < nombres.size() && c < 4; c++) {
                combinaciones.add(Map.of("nombre", nombres.get(c), "color", c < colores.size() ? colores.get(c) : ""));
            }
            Map<String, Object> entrada = new LinkedHashMap<>();
            entrada.put("texto", textos.get(i));
            entrada.put("referencia", a.referencia());
            entrada.put("codigoColor", a.codigoColor());
            entrada.put("descripcionColor", a.descripcionColor());
            entrada.put("nombreBolso", a.nombreBolso());
            entrada.put("codigoModelo", a.codigoModelo());
            entrada.put("fotoId", a.fotoId());
            entrada.put("piel", a.pielBase());
            entrada.put("colorPiel", a.colorBase());
            entrada.put("forro", articulo.map(Articulo::getForro).orElse(a.forro()));
            entrada.put("colorForro", color.map(ArticuloColor::getColorForro).orElse(""));
            entrada.put("combinaciones", combinaciones);
            salida.add(entrada);
        }
        return Map.of("articulos", salida);
    }

    /**
     * Genera el excel y guarda la hoja. La pantalla lo pide por {@code fetch}
     * y descarga sin recargar; los rechazos son un 400 en texto plano.
     */
    @PostMapping("/documentos-corte/hoja/generar")
    public ResponseEntity<?> generar(@ModelAttribute HojaCorteForm form,
                                     @RequestParam(name = "fotoSubida", required = false) MultipartFile fotoSubida)
            throws IOException {
        Optional<ClienteConfig> cliente = catalogoClientes.clientePara(form.getCliente());
        if (cliente.isEmpty()) {
            return rechazo("Elige el cliente: sin él no se ha guardado nada.");
        }
        DatosHojaCorte datos = form.aDatos(nombreDe(cliente.get(), form.getCliente()));
        if (datos.modelo().isEmpty() && datos.referencia().isEmpty()) {
            return rechazo("Escribe el modelo o la referencia: es el título de la hoja. No se ha guardado nada.");
        }
        if (datos.lineasConCantidad().isEmpty()) {
            return rechazo("Ninguna línea tiene cantidad: no hay nada que cortar y no se ha guardado nada.");
        }
        byte[] fotoPropia;
        try {
            fotoPropia = fotoSubida != null && !fotoSubida.isEmpty() ? FotoSubida.reducir(fotoSubida, conversor)
                    : archivo.fotoDe(form.getFotoHojaId()).orElse(null);
        } catch (IOException e) {
            return rechazo("No se ha podido leer la foto subida: " + e.getMessage() + ". No se ha guardado nada.");
        }
        byte[] foto = fotoPropia != null ? fotoPropia : fotoDelCatalogo(form.getFotoId(), datos);
        // Primero el excel y luego guardar: una hoja que no se ha podido escribir no sale en las antiguas.
        byte[] contenido = servicio.excel(datos, imagen(foto));
        HojaCorte guardada = archivo.guardar(datos, fotoPropia);
        return descarga(datos, contenido).header("X-Hoja-Id", String.valueOf(guardada.getId())).body(contenido);
    }

    // --- Hojas antiguas ---

    @GetMapping("/documentos-corte/hoja/antiguas")
    public String antiguas(Model model) {
        model.addAttribute("hojas", archivo.todas());
        return "hoja-corte-antiguas";
    }

    /** Regenera el excel con lo guardado: lo que se imprimió, con su foto (la suya o la del catálogo de hoy). */
    @GetMapping("/documentos-corte/hoja/antiguas/{id}/xlsx")
    public ResponseEntity<byte[]> descargarAntigua(@PathVariable Long id) throws IOException {
        Optional<DatosHojaCorte> datos = archivo.datosDe(id);
        if (datos.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        byte[] foto = archivo.fotoDe(id).orElseGet(() -> fotoDelCatalogo(null, datos.get()));
        byte[] contenido = servicio.excel(datos.get(), imagen(foto));
        return descarga(datos.get(), contenido).body(contenido);
    }

    @GetMapping("/documentos-corte/hoja/antiguas/{id}/foto")
    public ResponseEntity<byte[]> fotoDeHoja(@PathVariable Long id) {
        return archivo.fotoDe(id)
                .map(bytes -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.noCache()).body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // --- Internos ---

    private byte[] fotoDelCatalogo(Long fotoId, DatosHojaCorte datos) {
        Optional<Long> id = Optional.ofNullable(fotoId).or(() -> fotos.principalDe(datos.cliente(),
                datos.modeloParaFoto()));
        return id.flatMap(fotos::conBytes).map(FotoCatalogo::getJpeg).orElse(null);
    }

    /** La foto como la quiere la ficha; una que no se deja leer se queda fuera (la hoja sale sin foto). */
    private static Imagen imagen(byte[] jpeg) {
        if (jpeg == null) {
            return null;
        }
        try {
            return Imagen.de(jpeg, "foto");
        } catch (IOException e) {
            return null;
        }
    }

    private ResponseEntity.BodyBuilder descarga(DatosHojaCorte datos, byte[] contenido) {
        return ResponseEntity.ok()
                .contentType(TIPO_XLSX)
                .contentLength(contenido.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(servicio.nombreFichero(datos), StandardCharsets.UTF_8).build().toString());
    }

    private static ResponseEntity<String> rechazo(String motivo) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(motivo);
    }

    private static String nombreDe(ClienteConfig config, String clave) {
        return config.getNombre() != null && !config.getNombre().isBlank() ? config.getNombre() : clave;
    }
}
