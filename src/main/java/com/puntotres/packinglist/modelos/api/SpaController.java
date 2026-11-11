package com.puntotres.packinglist.modelos.api;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Sirve la PWA de modelos, que vive en {@code /app} y enruta en el navegador
 * ({@code /app/modelos/12}, {@code /app/ordenes}...). Lo que hace:
 *
 * <ul>
 * <li>Una ruta <b>sin extensión</b> es una pantalla de la app: se reenvía al
 * {@code index.html}, para que abrir un marcador, leer un QR o recargar la
 * página no dé un 404.</li>
 * <li>Una ruta <b>con extensión</b> ({@code /app/assets/index-1a2b.js},
 * {@code /app/manifest.webmanifest}) es un fichero del build: se sirve desde
 * {@code classpath:static/app/} o es un 404, nunca el índice. Un {@code .js}
 * que no existe contestado con HTML rompería la app con un error de sintaxis
 * que no dice nada.</li>
 * </ul>
 *
 * Los ficheros los sirve este controlador y no el manejador de recursos
 * estáticos de Spring porque {@code /app/{*ruta}} los reclama primero: el
 * patrón de rutas no sabe decir «sin extensión».
 */
@Controller
public class SpaController {

    private static final String INDICE = "forward:/app/index.html";
    private static final String CARPETA = "static/app/";

    /** El último tramo de la ruta acaba en punto + extensión: es un fichero, no una pantalla. */
    private static final Pattern CON_EXTENSION = Pattern.compile("\\.[A-Za-z0-9]{1,12}$");

    /** Los que {@link MediaTypeFactory} no conoce. */
    private static final Map<String, MediaType> TIPOS = Map.of(
            "webmanifest", MediaType.parseMediaType("application/manifest+json"),
            "js", MediaType.parseMediaType("text/javascript"),
            "map", MediaType.APPLICATION_JSON,
            "woff2", MediaType.parseMediaType("font/woff2"),
            "woff", MediaType.parseMediaType("font/woff"));

    @GetMapping("/app")
    public String raiz() {
        return INDICE;
    }

    @GetMapping("/app/{*ruta}")
    public Object ruta(@PathVariable String ruta) {
        if (!CON_EXTENSION.matcher(ruta).find()) {
            return INDICE;
        }
        return fichero(ruta.substring(1));
    }

    private static ResponseEntity<Resource> fichero(String ruta) {
        // La ruta llega ya decodificada: un ".." o una barra invertida podrían salir de la carpeta.
        if (ruta.contains("..") || ruta.contains("\\") || ruta.startsWith("/")) {
            return ResponseEntity.badRequest().build();
        }
        Resource recurso = new ClassPathResource(CARPETA + ruta);
        if (!recurso.isReadable()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(tipoDe(ruta))
                .cacheControl(cacheDe(ruta))
                .body(recurso);
    }

    private static MediaType tipoDe(String ruta) {
        String extension = ruta.substring(ruta.lastIndexOf('.') + 1).toLowerCase();
        return Optional.ofNullable(TIPOS.get(extension))
                .or(() -> MediaTypeFactory.getMediaType(ruta))
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    /**
     * Lo de {@code assets/} lleva el hash del contenido en el nombre y no
     * cambia nunca: se guarda un año. Lo demás (el índice, el service worker,
     * el manifiesto) se revalida siempre, o una tablet se quedaría con la
     * versión vieja de la app.
     */
    private static CacheControl cacheDe(String ruta) {
        return ruta.startsWith("assets/")
                ? CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable()
                : CacheControl.noCache();
    }
}
