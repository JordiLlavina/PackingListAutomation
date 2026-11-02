package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import com.puntotres.packinglist.config.CatalogoTaras;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.Articulo;
import com.puntotres.packinglist.persistence.ArticuloColor;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.Piel;
import com.puntotres.packinglist.persistence.ResumenFoto;
import com.puntotres.packinglist.persistence.ResumenTemporada;
import com.puntotres.packinglist.persistence.TablaColores;
import com.puntotres.packinglist.persistence.TipoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoColor;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoModelo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.Propagacion;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.TextosConocidos;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;
import com.puntotres.packinglist.service.corte.DocumentosCorteService;

/**
 * Pantalla "Catálogo de modelos": todo lo que el programa sabe de cada modelo
 * de un cliente en una tabla, una fila por artículo (modelo + piel) agrupada
 * por modelo, con vistas por tarea (packaging, corte, fotos) y chips de
 * columnas para ver solo lo que toca.
 *
 * Cada celda se guarda por fetch y contesta JSON, como en /colores: con un
 * formulario por fila, guardar recargaba la página y se perdía lo tecleado en
 * las demás. Las altas y bajas sí son envíos normales, porque cambian la
 * forma de la tabla.
 *
 * Los colores del cliente (código → descripción) y los nombres de las pieles
 * siguen en /colores; las temporadas, en /temporadas. Aquí solo se ven.
 */
@Controller
public class CatalogoController {

    private final CatalogoModelos catalogo;
    private final FotosCatalogo fotos;
    private final ArchivoTemporadas temporadas;
    private final ClientesProperties clientesProperties;
    private final CatalogoTaras taras;
    private final TablaColores tablaColores;
    private final DocumentosCorteService reglas;
    private final com.puntotres.packinglist.persistence.MemoriaPieles pieles;
    private final com.puntotres.packinglist.service.corte.ConversorFotos conversor;

    public CatalogoController(CatalogoModelos catalogo, FotosCatalogo fotos, ArchivoTemporadas temporadas,
                              ClientesProperties clientesProperties, CatalogoTaras taras, TablaColores tablaColores,
                              DocumentosCorteService reglas, com.puntotres.packinglist.persistence.MemoriaPieles pieles,
                              com.puntotres.packinglist.service.corte.ConversorFotos conversor) {
        this.catalogo = catalogo;
        this.fotos = fotos;
        this.temporadas = temporadas;
        this.clientesProperties = clientesProperties;
        this.taras = taras;
        this.tablaColores = tablaColores;
        this.reglas = reglas;
        this.pieles = pieles;
        this.conversor = conversor;
    }

    @GetMapping("/catalogo")
    public String catalogo(@RequestParam(required = false) String cliente,
                           @RequestParam(required = false) Long temporada, Model model) {
        Map<String, String> clientes = new LinkedHashMap<>();
        clientesProperties.getClientes().forEach((clave, config) -> clientes.put(clave, config.getNombre()));
        String elegido = cliente == null || cliente.isBlank() ? null : cliente.trim().toUpperCase(Locale.ROOT);
        if (elegido != null && !clientes.containsKey(elegido)) {
            elegido = null;
        }
        model.addAttribute("clientes", clientes);
        model.addAttribute("cliente", elegido);
        model.addAttribute("nombreCliente", elegido == null ? "" : clientes.get(elegido));
        List<ResumenTemporada> delCliente = elegido == null ? List.of() : temporadas.de(elegido);
        // El selector de cliente envía el formulario entero, con la temporada del
        // cliente anterior: una que no sea de este no filtra nada.
        Long laTemporada = temporada != null && delCliente.stream().anyMatch(t -> t.id().equals(temporada))
                ? temporada : null;
        model.addAttribute("temporadas", delCliente);
        model.addAttribute("temporadaId", laTemporada);
        model.addAttribute("filas", elegido == null ? List.of() : filas(elegido, laTemporada));
        model.addAttribute("tamanos", taras.tamanosDeMayorAMenor());
        model.addAttribute("tipos", TipoArticulo.values());
        model.addAttribute("coloresCliente", elegido == null ? List.of() : tablaColores.de(elegido));
        TextosConocidos conocidos = elegido == null ? new TextosConocidos(List.of(), List.of())
                : catalogo.textosConocidos(elegido);
        model.addAttribute("forrosConocidos", conocidos.forros());
        model.addAttribute("combinacionesConocidas", conocidos.combinaciones());
        return "catalogo";
    }

    // --- Celdas, por fetch ---

    @PostMapping("/catalogo/articulo/celda")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> celdaArticulo(@RequestParam String cliente,
                                                             @RequestParam String referencia,
                                                             @RequestParam String campo,
                                                             @RequestParam(required = false) String valor,
                                                             @RequestParam(defaultValue = "true") boolean propagar) {
        try {
            Propagacion propagacion = catalogo.guardarCampo(cliente, referencia,
                    enumDe(CampoArticulo.class, campo), valor, propagar);
            List<Map<String, String>> propagadas = new ArrayList<>();
            propagacion.propagadas().forEach(p -> propagadas.add(Map.of(
                    "referencia", p.referencia(), "anterior", p.anterior())));
            return ResponseEntity.ok(Map.of("ok", true, "mensaje", "Guardado", "propagadas", propagadas));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", e.getMessage()));
        }
    }

    @PostMapping("/catalogo/modelo/celda")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> celdaModelo(@RequestParam String cliente, @RequestParam String codigo,
                                                           @RequestParam String campo,
                                                           @RequestParam(required = false) String valor) {
        try {
            catalogo.guardarCampoModelo(cliente, codigo, enumDe(CampoModelo.class, campo), valor);
            return ResponseEntity.ok(Map.of("ok", true, "mensaje", "Guardado"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", e.getMessage()));
        }
    }

    @PostMapping("/catalogo/color/celda")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> celdaColor(@RequestParam String cliente, @RequestParam String referencia,
                                                          @RequestParam String codigoColor, @RequestParam String campo,
                                                          @RequestParam(required = false) String valor) {
        try {
            catalogo.guardarCampoColor(cliente, referencia, codigoColor, enumDe(CampoColor.class, campo), valor);
            return ResponseEntity.ok(Map.of("ok", true, "mensaje", "Guardado"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", e.getMessage()));
        }
    }

    // --- Altas y bajas, con recarga ---

    @PostMapping("/catalogo/color/nuevo")
    public String nuevoColor(@RequestParam String cliente, @RequestParam String referencia,
                             @RequestParam String codigoColor, @RequestParam(required = false) Long temporada,
                             RedirectAttributes redirect) {
        try {
            catalogo.asegurarColor(cliente, referencia, codigoColor);
            redirect.addFlashAttribute("mensaje", "Añadido el color " + codigoColor.trim().toUpperCase(Locale.ROOT)
                    + " a " + referencia.trim().toUpperCase(Locale.ROOT) + ".");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
        }
        return volver(cliente, temporada);
    }

    @PostMapping("/catalogo/modelo/nuevo")
    public String nuevoModelo(@RequestParam String cliente, @RequestParam String codigo,
                              @RequestParam(required = false) String nombre, @RequestParam(required = false) String tipo,
                              @RequestParam(required = false) Long temporada, RedirectAttributes redirect) {
        if (codigo == null || codigo.isBlank()) {
            redirect.addFlashAttribute("mensajeError", "El modelo necesita su código (ULL027, F67008).");
            return volver(cliente, temporada);
        }
        Modelo modelo = catalogo.asegurarModelo(cliente, codigo, nombre);
        if (tipo != null && !tipo.isBlank()) {
            catalogo.guardarCampoModelo(cliente, codigo, CampoModelo.TIPO, tipo);
        }
        redirect.addFlashAttribute("mensaje", "Modelo " + modelo.getCodigo() + " guardado. Añade sus artículos "
                + "(modelo + piel) para poder darles cartón, unidades y pieles.");
        return volver(cliente, temporada);
    }

    /**
     * El modelo y la piel salen de partir la referencia con la regla del
     * cliente. Si la regla no la sabe partir, se guarda igual, sin piel, y se
     * avisa: un artículo sin piel no hereda ni recibe nada de sus hermanos.
     */
    @PostMapping("/catalogo/articulo/nuevo")
    public String nuevoArticulo(@RequestParam String cliente, @RequestParam String referencia,
                                @RequestParam(required = false) Long temporada, RedirectAttributes redirect) {
        if (referencia == null || referencia.isBlank()) {
            redirect.addFlashAttribute("mensajeError", "El artículo necesita su referencia (ULL027.AL0103).");
            return volver(cliente, temporada);
        }
        Articulo articulo = catalogo.asegurarArticulo(cliente, referencia);
        boolean elClienteParte = reglas.clientePara(cliente).isPresent();
        if (!articulo.tienePiel() && elClienteParte) {
            redirect.addFlashAttribute("aviso", "Artículo " + articulo.getReferencia() + " guardado sin piel: la "
                    + "regla de " + cliente + " no ha sabido separar el modelo de la piel, así que no hereda ni "
                    + "recibe nada de otros artículos del modelo.");
        } else {
            redirect.addFlashAttribute("mensaje", "Artículo " + articulo.getReferencia() + " guardado.");
        }
        return volver(cliente, temporada);
    }

    @PostMapping("/catalogo/modelo/borrar")
    public String borrarModelo(@RequestParam String cliente, @RequestParam String codigo,
                               @RequestParam(required = false) Long temporada, RedirectAttributes redirect) {
        catalogo.borrarModelo(cliente, codigo);
        redirect.addFlashAttribute("mensaje", "Borrado el modelo " + codigo.trim().toUpperCase(Locale.ROOT)
                + " con sus artículos, colores y fotos.");
        return volver(cliente, temporada);
    }

    @PostMapping("/catalogo/articulo/borrar")
    public String borrarArticulo(@RequestParam String cliente, @RequestParam String referencia,
                                 @RequestParam(required = false) Long temporada, RedirectAttributes redirect) {
        catalogo.borrarArticulo(cliente, referencia);
        redirect.addFlashAttribute("mensaje", "Borrado el artículo " + referencia.trim().toUpperCase(Locale.ROOT)
                + " con sus colores.");
        return volver(cliente, temporada);
    }

    // --- Fotos ---

    @GetMapping("/catalogo/foto/{id}")
    public ResponseEntity<byte[]> foto(@org.springframework.web.bind.annotation.PathVariable Long id) {
        return fotos.conBytes(id)
                .map(foto -> ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.IMAGE_JPEG)
                        .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(1))
                                .cachePrivate())
                        .body(foto.getJpeg()))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Varias fotos a la vez, HEIC incluido: pasan por el conversor del corte
     * (reduce a 1.600 px y endereza por el EXIF) en un directorio temporal
     * que se borra al acabar, y entran con su nombre original. La primera de
     * un modelo sin fotos queda como principal.
     */
    @PostMapping("/catalogo/foto/subir")
    public String subir(@RequestParam String cliente, @RequestParam String codigo,
                        @RequestParam("fotos") List<org.springframework.web.multipart.MultipartFile> subidas,
                        @RequestParam(required = false) Long temporada, RedirectAttributes redirect) {
        List<org.springframework.web.multipart.MultipartFile> conContenido = subidas.stream()
                .filter(fichero -> !fichero.isEmpty() && fichero.getOriginalFilename() != null).toList();
        if (conContenido.isEmpty()) {
            redirect.addFlashAttribute("mensajeError", "Elige al menos una foto.");
            return volver(cliente, temporada);
        }
        java.nio.file.Path directorio = null;
        try {
            directorio = java.nio.file.Files.createTempDirectory("catalogo-fotos-");
            List<com.puntotres.packinglist.service.corte.FotoModelo> originales = new ArrayList<>();
            int numero = 0;
            for (org.springframework.web.multipart.MultipartFile fichero : conContenido) {
                String nombre = java.nio.file.Paths.get(fichero.getOriginalFilename()).getFileName().toString();
                String extension = nombre.contains(".") ? nombre.substring(nombre.lastIndexOf('.')) : ".jpg";
                java.nio.file.Path copia = directorio.resolve(String.format("%05d%s", ++numero, extension));
                fichero.transferTo(copia);
                originales.add(new com.puntotres.packinglist.service.corte.FotoModelo(codigo, nombre, copia));
            }
            com.puntotres.packinglist.service.corte.ConversionFotos conversion = conversor.convertir(originales, directorio);
            conversion.esperar();
            int guardadas = 0;
            for (com.puntotres.packinglist.service.corte.FotoModelo foto : originales) {
                java.util.Optional<java.nio.file.Path> reducida = conversion.reducida(foto);
                if (reducida.isPresent()) {
                    fotos.guardar(cliente, codigo, foto.nombreOriginal(), java.nio.file.Files.readAllBytes(reducida.get()));
                    guardadas++;
                }
            }
            redirect.addFlashAttribute("mensaje", guardadas + (guardadas == 1 ? " foto guardada" : " fotos guardadas")
                    + " en " + codigo.trim().toUpperCase(Locale.ROOT) + ".");
            List<String> avisos = conversion.avisos();
            if (!avisos.isEmpty()) {
                redirect.addFlashAttribute("aviso", String.join(" ", avisos));
            }
        } catch (java.io.IOException | RuntimeException e) {
            redirect.addFlashAttribute("mensajeError", "No se pudieron guardar las fotos: "
                    + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            redirect.addFlashAttribute("mensajeError", "Se ha interrumpido la conversión de las fotos.");
        } finally {
            CorteEnCurso.borrar(directorio);
        }
        return volver(cliente, temporada);
    }

    @PostMapping("/catalogo/foto/principal")
    public String principal(@RequestParam String cliente, @RequestParam String codigo, @RequestParam Long id,
                            @RequestParam(required = false) Long temporada) {
        fotos.marcarPrincipal(cliente, codigo, id);
        return volver(cliente, temporada);
    }

    @PostMapping("/catalogo/foto/borrar")
    public String borrarFoto(@RequestParam String cliente, @RequestParam Long id,
                             @RequestParam(required = false) Long temporada) {
        fotos.borrar(id);
        return volver(cliente, temporada);
    }

    // --- Internos ---

    private List<FilaCatalogoVista> filas(String cliente, Long temporadaId) {
        Set<String> enLaTemporada = temporadaId == null ? null : catalogo.codigosDeModeloEn(cliente, temporadaId);
        Map<String, String> nombreDeTemporada = new LinkedHashMap<>();
        for (ResumenTemporada temporada : temporadas.de(cliente)) {
            nombreDeTemporada.put(String.valueOf(temporada.id()), temporada.temporada());
        }
        Map<String, String> nombreDePiel = new LinkedHashMap<>();
        for (Piel piel : pieles.pielesDe(cliente)) {
            nombreDePiel.put(piel.getRefPiel(), piel.getNombre());
        }
        Map<String, String> descripcionDeColor = new LinkedHashMap<>();
        tablaColores.de(cliente).forEach(color -> descripcionDeColor.put(color.getCodigo(), color.getDescripcion()));
        Set<String> conTara = Set.copyOf(taras.tamanosDeMayorAMenor());

        Map<String, List<Articulo>> porModelo = new LinkedHashMap<>();
        for (Articulo articulo : catalogo.articulosDe(cliente)) {
            porModelo.computeIfAbsent(articulo.getCodigoModelo(), modelo -> new ArrayList<>()).add(articulo);
        }
        List<FilaCatalogoVista> filas = new ArrayList<>();
        List<Modelo> modelos = catalogo.modelosDe(cliente);
        Map<String, List<ResumenFoto>> fotosDe = fotos.de(cliente,
                modelos.stream().map(Modelo::getCodigo).toList());
        for (Modelo modelo : modelos) {
            if (enLaTemporada != null && !enLaTemporada.contains(modelo.getCodigo())) {
                continue;
            }
            List<String> nombresTemporada = catalogo.temporadasDe(cliente, modelo.getCodigo()).stream()
                    .map(id -> nombreDeTemporada.getOrDefault(String.valueOf(id), "?"))
                    .toList();
            List<ResumenFoto> delModelo = fotosDe.getOrDefault(modelo.getCodigo(), List.of());
            Long principal = delModelo.stream().filter(ResumenFoto::principal).map(ResumenFoto::id)
                    .findFirst().orElse(delModelo.isEmpty() ? null : delModelo.get(0).id());
            List<FilaCatalogoVista.ArticuloVista> articulos = new ArrayList<>();
            for (Articulo articulo : porModelo.getOrDefault(modelo.getCodigo(), List.of())) {
                articulos.add(vistaDe(cliente, articulo, nombreDePiel, descripcionDeColor, conTara));
            }
            filas.add(new FilaCatalogoVista(modelo.getCodigo(), modelo.getNombre(), modelo.getTipo().name(),
                    nombresTemporada, delModelo, principal, articulos));
        }
        return filas;
    }

    private FilaCatalogoVista.ArticuloVista vistaDe(String cliente, Articulo articulo, Map<String, String> nombreDePiel,
                                                    Map<String, String> descripcionDeColor, Set<String> conTara) {
        List<FilaCatalogoVista.ColorVista> colores = new ArrayList<>();
        for (ArticuloColor color : catalogo.coloresDe(cliente, articulo.getReferencia())) {
            List<String> deCombinacion = new ArrayList<>(color.getColoresCombinacion());
            while (deCombinacion.size() < CatalogoModelos.MAXIMO_COMBINACIONES) {
                deCombinacion.add("");
            }
            colores.add(new FilaCatalogoVista.ColorVista(color.getCodigoColor(),
                    descripcionDeColor.getOrDefault(color.getCodigoColor(), ""),
                    color.getColorPiel(), color.getColorForro(), deCombinacion));
        }
        return new FilaCatalogoVista.ArticuloVista(articulo.getReferencia(), articulo.getRefPiel(),
                nombreDePiel.getOrDefault(articulo.getRefPiel(), ""), articulo.getForro(),
                articulo.getCasillasCombinacion(), articulo.getMedidaCaja(),
                articulo.getMedidaCaja().isEmpty() || conTara.contains(articulo.getMedidaCaja()),
                articulo.getUnidadesPorCaja(), articulo.getPesoNetoKg(), colores);
    }

    private static <E extends Enum<E>> E enumDe(Class<E> tipo, String nombre) {
        try {
            return Enum.valueOf(tipo, nombre == null ? "" : nombre.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Campo desconocido: " + nombre);
        }
    }

    private static String volver(String cliente, Long temporada) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/catalogo")
                .queryParam("cliente", cliente == null ? "" : cliente.trim().toUpperCase(Locale.ROOT));
        if (temporada != null) {
            uri.queryParam("temporada", temporada);
        }
        return "redirect:" + uri.build().toUriString();
    }
}
