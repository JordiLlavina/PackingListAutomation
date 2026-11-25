package com.puntotres.packinglist.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import org.springframework.web.util.UriComponentsBuilder;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.CatalogoClientes;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.Importacion;
import com.puntotres.packinglist.persistence.Incorporacion;
import com.puntotres.packinglist.persistence.MemoriaPieles;
import com.puntotres.packinglist.persistence.TablaColores;
import com.puntotres.packinglist.persistence.TemporadaGuardada;
import com.puntotres.packinglist.service.catalogo.ExcelColoresPieles;
import com.puntotres.packinglist.service.corte.ClienteCorte;
import com.puntotres.packinglist.service.corte.DocumentosCorteService;

/**
 * Pantalla "Colores y Pieles de Cliente", con un desplegable para elegir de
 * qué cliente se ven sus dos tablas:
 *
 * - COLORES: la descripción de cada código de color (TablaColores). Existe
 *   porque el pedido de APC solo trae el código ("LZZ") y en las órdenes de
 *   corte el cortador tiene que leer el color ("LZZ NOIR").
 * - PIELES: el nombre de cada referencia de piel (AL0201 en AMI, PXBHZ en
 *   APC). Es la MISMA memoria de nombres de piel que lee la tabla de los
 *   Documentos del Corte (MemoriaPieles), así que lo que se guarda aquí sale
 *   allí sin más.
 *
 * Las dos parejas no cambian nunca, así que se teclean una vez. Guardar o
 * borrar UNA fila va por JSON y no recarga la página: recargando se perdía lo
 * tecleado en las demás filas. "Guardar todo" sí es un envío normal, porque
 * lleva la tabla entera y no hay nada que perder.
 *
 * Desde el excel de pedido (subido o de una temporada guardada) se pueden
 * meter de una vez los colores, las pieles o las dos cosas. Solo para los
 * clientes cuyo pedido se sabe leer (AMI y APC): son los mismos que tienen
 * documentos del corte, así que el lector se pide a ese servicio.
 *
 * Y las dos tablas de un cliente se exportan a un excel (una hoja cada una)
 * para rellenarlas en Excel y volver a importarlas, de cualquier cliente.
 */
@Controller
public class ColoresController {

    private static final String PEDIDO_ILEGIBLE =
            "no tiene el formato esperado: revisa que sea el excel de pedido de ese cliente";
    private static final String COLORES = "colores";
    private static final String PIELES = "pieles";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Lo que llega de "Guardar todo": una fila por código, en el orden de la tabla. */
    public static class TablaForm {

        private String cliente;
        private List<Fila> filas = new ArrayList<>();

        public String getCliente() { return cliente; }
        public void setCliente(String cliente) { this.cliente = cliente; }
        public List<Fila> getFilas() { return filas; }
        public void setFilas(List<Fila> filas) { this.filas = filas; }

        public static class Fila {

            private String codigo;
            private String valor;

            public String getCodigo() { return codigo; }
            public void setCodigo(String codigo) { this.codigo = codigo; }
            public String getValor() { return valor; }
            public void setValor(String valor) { this.valor = valor; }
        }
    }

    /** Una fila de cualquiera de las dos tablas: el código (o la ref de piel) y su texto. */
    public record FilaVista(String codigo, String valor) {
    }

    private final TablaColores tabla;
    private final MemoriaPieles pieles;
    private final CatalogoClientes catalogoClientes;
    private final DocumentosCorteService lectores;
    private final ArchivoTemporadas archivoTemporadas;

    public ColoresController(TablaColores tabla, MemoriaPieles pieles, CatalogoClientes catalogoClientes,
                             DocumentosCorteService lectores, ArchivoTemporadas archivoTemporadas) {
        this.tabla = tabla;
        this.pieles = pieles;
        this.catalogoClientes = catalogoClientes;
        this.lectores = lectores;
        this.archivoTemporadas = archivoTemporadas;
    }

    @GetMapping("/colores")
    public String colores(@RequestParam(required = false) String cliente, Model model) {
        Map<String, String> clientes = clientes();
        String elegido = elegido(cliente, clientes);
        boolean admitePedido = lectores.clientePara(elegido).isPresent();
        model.addAttribute("clientes", clientes);
        model.addAttribute("cliente", elegido);
        model.addAttribute("nombreCliente", clientes.getOrDefault(elegido, ""));
        model.addAttribute("colores", tabla.de(elegido).stream()
                .map(color -> new FilaVista(color.getCodigo(), color.getDescripcion())).toList());
        model.addAttribute("pieles", pieles.pielesDe(elegido).stream()
                .map(piel -> new FilaVista(piel.getRefPiel(), piel.getNombre())).toList());
        model.addAttribute("admitePedido", admitePedido);
        // El cliente está fijado en la página, así que el desplegable de
        // temporadas se pinta en el servidor y no hace falta rehacerlo en el
        // navegador como en las pantallas de entrada.
        model.addAttribute("temporadas", admitePedido ? archivoTemporadas.dePedido(elegido) : List.of());
        return "colores";
    }

    // --- Desde el excel de pedido ---

    /**
     * Lee el excel de pedido y mete en las tablas lo que falte: los colores,
     * las pieles o las dos cosas ({@code que} = colores, pieles o todo). Lo que
     * ya estaba no se toca, salvo para rellenar la descripción vacía de un
     * color con la del pedido (AMI la trae; APC no; ninguno trae el nombre de
     * la piel).
     *
     * El excel es el de una temporada guardada o uno subido a mano; uno subido
     * manda sobre el guardado, como en las pantallas de entrada. La temporada
     * se usa solo si es de este cliente (ArchivoTemporadas.paraElEnvio).
     */
    @PostMapping("/colores/pedido")
    public String desdePedido(@RequestParam String cliente,
                              @RequestParam(required = false) Long temporadaGuardadaId,
                              @RequestParam(required = false) MultipartFile pedido,
                              @RequestParam(defaultValue = "todo") String que,
                              RedirectAttributes redirect) {
        Optional<ClienteCorte> lector = lectores.clientePara(cliente);
        if (catalogoClientes.clientePara(cliente).isEmpty() || lector.isEmpty()) {
            redirect.addFlashAttribute("mensajeError", "Del cliente " + clave(cliente)
                    + " todavía no se sabe leer el excel de pedido: solo de AMI y APC.");
            return volver(cliente);
        }
        boolean conExcel = pedido != null && !pedido.isEmpty();
        Optional<TemporadaGuardada> guardada = conExcel ? Optional.empty()
                : archivoTemporadas.conPedidoParaElEnvio(temporadaGuardadaId, lector.get().clave());
        if (!conExcel && guardada.isEmpty()) {
            redirect.addFlashAttribute("mensajeError", temporadaGuardadaId != null
                    ? "La temporada elegida ya no existe o no es de este cliente: vuelve a elegirla "
                            + "o sube el excel de pedido."
                    : "Elige una temporada guardada o sube el excel de pedido del cliente.");
            return volver(cliente);
        }
        boolean conColores = !PIELES.equals(que);
        boolean conPieles = !COLORES.equals(que);
        String origen = guardada.map(temporada -> "excel de pedido de la temporada " + temporada.getTemporada())
                .orElse("excel de pedido");
        Map<String, String> colores = Map.of();
        Set<String> refsPiel = Set.of();
        try {
            byte[] excel = conExcel ? pedido.getBytes() : guardada.get().getExcelPedido();
            if (conColores) {
                colores = lector.get().coloresDelPedido(excel);
            }
            if (conPieles) {
                refsPiel = lector.get().pielesDelPedido(excel);
            }
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("mensajeError", "No se pudo leer el excel de pedido: "
                    + (e.getMessage() != null ? e.getMessage() : PEDIDO_ILEGIBLE));
            return volver(cliente);
        } catch (IOException | RuntimeException e) {
            redirect.addFlashAttribute("mensajeError", "No se pudo leer el excel de pedido: " + PEDIDO_ILEGIBLE);
            return volver(cliente);
        }
        if (colores.isEmpty() && refsPiel.isEmpty()) {
            redirect.addFlashAttribute("mensajeError", "El excel de pedido no trae ningún "
                    + (conColores && conPieles ? "color ni ninguna piel." : conColores ? "color." : "piel."));
            return volver(cliente);
        }
        List<String> frases = new ArrayList<>();
        if (conColores) {
            frases.add(resumen(tabla.incorporar(cliente, colores), origen, "color", "colores", false));
        }
        if (conPieles) {
            frases.add(resumen(pieles.incorporarPieles(cliente, refsPiel), origen, "piel", "pieles", true));
        }
        redirect.addFlashAttribute("mensaje", String.join(" ", frases));
        return volver(cliente);
    }

    /**
     * "Del excel de pedido de la temporada H26 salen 13 colores. Añadidos (4):
     * A184, … Ya estaban: 9." {@code origen} es de dónde salen, en minúscula.
     */
    static String resumen(Incorporacion incorporacion, String origen, String uno, String varios,
                          boolean femenino) {
        int total = incorporacion.total();
        if (incorporacion.anadidos().isEmpty() && incorporacion.completados().isEmpty()) {
            String sujeto = total == 1 ? (femenino ? "La " : "El ") + uno
                    : (femenino ? "Las " : "Los ") + total + " " + varios;
            return sujeto + " del " + origen + (total == 1 ? " ya estaba" : " ya estaban")
                    + " en la tabla: no se ha añadido " + (femenino ? "ninguna." : "ninguno.");
        }
        StringBuilder resumen = new StringBuilder("Del ").append(origen).append(" salen ")
                .append(total).append(' ').append(total == 1 ? uno : varios).append('.');
        // Por código, como la tabla de debajo: en el orden del pedido no se encuentran.
        if (!incorporacion.anadidos().isEmpty()) {
            resumen.append(femenino ? " Añadidas (" : " Añadidos (").append(incorporacion.anadidos().size())
                    .append("): ").append(String.join(", ", incorporacion.anadidos().stream().sorted().toList()))
                    .append('.');
        }
        if (!incorporacion.completados().isEmpty()) {
            resumen.append(" Con la descripción del pedido (").append(incorporacion.completados().size())
                    .append("): ").append(String.join(", ", incorporacion.completados().stream().sorted().toList()))
                    .append('.');
        }
        if (!incorporacion.yaEstaban().isEmpty()) {
            resumen.append(" Ya estaban: ").append(incorporacion.yaEstaban().size()).append('.');
        }
        return resumen.toString();
    }

    // --- Exportar e importar el excel de las dos tablas ---

    /**
     * Las dos tablas del cliente en un excel, una por hoja, para rellenarlas
     * en Excel y volver a subirlas con {@link #importar}.
     */
    @GetMapping("/colores/excel")
    public ResponseEntity<byte[]> exportar(@RequestParam String cliente) throws IOException {
        String elCliente = clave(cliente);
        ClienteConfig config = catalogoClientes.clientePara(elCliente)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        byte[] excel = ExcelColoresPieles.escribir(elCliente,
                tabla.de(elCliente).stream()
                        .map(color -> new ExcelColoresPieles.Fila(color.getCodigo(), color.getDescripcion())).toList(),
                pieles.pielesDe(elCliente).stream()
                        .map(piel -> new ExcelColoresPieles.Fila(piel.getRefPiel(), piel.getNombre())).toList());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        // Sin el punto final de "A.P.C.": saldría "A.P.C..xlsx".
                        .filename("Colores y pieles " + nombreDe(config, elCliente).replaceAll("[.\\s]+$", "")
                                + ".xlsx", StandardCharsets.UTF_8)
                        .build().toString())
                .body(excel);
    }

    /**
     * Sube el excel exportado ya rellenado. Lo que trae manda sobre lo
     * guardado, pero una casilla vacía no borra y lo que no viene no se toca:
     * borrar se hace en la pantalla, fila a fila. Un excel de otro cliente no
     * se importa (lo dice su fila "Cliente"): pondría los colores de APC en
     * la tabla de AMI.
     */
    @PostMapping("/colores/importar")
    public String importar(@RequestParam String cliente, @RequestParam(required = false) MultipartFile excel,
                           RedirectAttributes redirect) {
        String elCliente = clave(cliente);
        if (catalogoClientes.clientePara(elCliente).isEmpty()) {
            redirect.addFlashAttribute("mensajeError", "Cliente desconocido: " + cliente + ".");
            return "redirect:/colores";
        }
        if (excel == null || excel.isEmpty()) {
            redirect.addFlashAttribute("mensajeError",
                    "Elige el excel de colores y pieles que se exporta desde esta pantalla.");
            return volver(elCliente);
        }
        ExcelColoresPieles.Leido leido;
        try {
            leido = ExcelColoresPieles.leer(excel.getBytes());
        } catch (IllegalArgumentException | IOException e) {
            redirect.addFlashAttribute("mensajeError", "No se pudo importar el excel: "
                    + (e instanceof IllegalArgumentException && e.getMessage() != null ? e.getMessage()
                            : "no se ha podido leer") + ".");
            return volver(elCliente);
        }
        if (!leido.cliente().isEmpty() && !leido.cliente().equals(elCliente)) {
            redirect.addFlashAttribute("mensajeError", "El excel es de los colores y pieles de "
                    + leido.cliente() + " y esta pantalla es de " + elCliente
                    + ": elige ese cliente arriba o exporta el de " + elCliente + ".");
            return volver(elCliente);
        }
        List<String> frases = new ArrayList<>();
        if (leido.colores() != null) {
            frases.add(resumenImportacion(tabla.importar(elCliente, leido.colores()), "Colores",
                    "añadidos", "cambiados", "ninguno"));
        }
        if (leido.pieles() != null) {
            frases.add(resumenImportacion(pieles.importarPieles(elCliente, leido.pieles()), "Pieles",
                    "añadidas", "cambiadas", "ninguna"));
        }
        String nombre = excel.getOriginalFilename() == null || excel.getOriginalFilename().isBlank()
                ? "el excel" : excel.getOriginalFilename();
        redirect.addFlashAttribute("mensaje", "Importado " + nombre + ". " + String.join(" ", frases));
        return volver(elCliente);
    }

    /** "Colores: añadidos (1): X5; cambiados (1): X6; sin cambios: 3." */
    static String resumenImportacion(Importacion importacion, String tablaImportada, String anadidos,
                                     String cambiados, String ninguno) {
        if (importacion.total() == 0) {
            return tablaImportada + ": la hoja no trae " + ninguno + ".";
        }
        List<String> partes = new ArrayList<>();
        if (!importacion.anadidos().isEmpty()) {
            partes.add(anadidos + " (" + importacion.anadidos().size() + "): "
                    + String.join(", ", importacion.anadidos()));
        }
        if (!importacion.cambiados().isEmpty()) {
            partes.add(cambiados + " (" + importacion.cambiados().size() + "): "
                    + String.join(", ", importacion.cambiados()));
        }
        if (!importacion.sinCambios().isEmpty()) {
            partes.add("sin cambios: " + importacion.sinCambios().size());
        }
        return tablaImportada + ": " + String.join("; ", partes) + ".";
    }

    // --- Una fila, sin recargar ---

    /** Guarda una fila y contesta en JSON: la pantalla no se recarga y lo tecleado en las demás se queda. */
    @PostMapping("/colores/{tablaElegida}/fila")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> guardarFila(@PathVariable String tablaElegida,
                                                           @RequestParam String cliente,
                                                           @RequestParam String codigo,
                                                           @RequestParam(required = false) String valor) {
        String error = comprobar(tablaElegida, cliente, codigo);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", error));
        }
        guardarEn(tablaElegida, cliente, codigo, valor);
        return ResponseEntity.ok(Map.of("ok", true, "mensaje", "Guardado"));
    }

    @PostMapping("/colores/{tablaElegida}/fila/borrar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> borrarFila(@PathVariable String tablaElegida,
                                                          @RequestParam String cliente,
                                                          @RequestParam String codigo) {
        String error = comprobar(tablaElegida, cliente, codigo);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", error));
        }
        boolean habia = PIELES.equals(tablaElegida) ? pieles.borrarPiel(cliente, codigo)
                : tabla.borrar(cliente, codigo);
        return ResponseEntity.ok(Map.of("ok", true, "borrado", habia));
    }

    // --- La tabla entera y las altas ---

    /** "Guardar todo": la tabla entera de una vez. Una fila que ya no llega (borrada) no se toca. */
    @PostMapping("/colores/{tablaElegida}/todo")
    public String guardarTodo(@PathVariable String tablaElegida, @ModelAttribute TablaForm form,
                              RedirectAttributes redirect) {
        validarTabla(tablaElegida);
        String cliente = form.getCliente() == null ? "" : form.getCliente();
        if (catalogoClientes.clientePara(cliente).isEmpty()) {
            redirect.addFlashAttribute("mensajeError", "Cliente desconocido: " + cliente + ".");
            return "redirect:/colores";
        }
        int guardadas = 0;
        for (TablaForm.Fila fila : form.getFilas()) {
            if (fila != null && fila.getCodigo() != null && !fila.getCodigo().isBlank()) {
                guardarEn(tablaElegida, cliente, fila.getCodigo(), fila.getValor());
                guardadas++;
            }
        }
        redirect.addFlashAttribute("mensaje", "Guardad" + (PIELES.equals(tablaElegida) ? "as " : "os ")
                + guardadas + " " + (PIELES.equals(tablaElegida) ? (guardadas == 1 ? "piel" : "pieles")
                : (guardadas == 1 ? "color" : "colores")) + ".");
        return volver(cliente);
    }

    /** Alta o corrección de una fila desde "Añadir": un código que ya está cambia su valor. */
    @PostMapping("/colores/{tablaElegida}/nuevo")
    public String anadir(@PathVariable String tablaElegida, @RequestParam String cliente,
                         @RequestParam String codigo, @RequestParam(required = false) String valor,
                         RedirectAttributes redirect) {
        validarTabla(tablaElegida);
        if (catalogoClientes.clientePara(cliente).isEmpty()) {
            redirect.addFlashAttribute("mensajeError", "Cliente desconocido: " + cliente + ".");
            return "redirect:/colores";
        }
        String error = comprobar(tablaElegida, cliente, codigo);
        if (error != null) {
            redirect.addFlashAttribute("mensajeError", error);
            return volver(cliente);
        }
        guardarEn(tablaElegida, cliente, codigo, valor);
        redirect.addFlashAttribute("mensaje", (PIELES.equals(tablaElegida) ? "Guardada la piel " : "Guardado el color ")
                + clave(codigo) + ".");
        return volver(cliente);
    }

    // --- Internos ---

    private void guardarEn(String tablaElegida, String cliente, String codigo, String valor) {
        if (PIELES.equals(tablaElegida)) {
            pieles.guardarPiel(cliente, codigo, valor);
        } else {
            tabla.guardar(cliente, codigo, valor);
        }
    }

    /** El motivo por el que no se puede guardar, o null si se puede. */
    private String comprobar(String tablaElegida, String cliente, String codigo) {
        validarTabla(tablaElegida);
        if (catalogoClientes.clientePara(cliente).isEmpty()) {
            return "Cliente desconocido: " + cliente + ".";
        }
        if (codigo == null || codigo.isBlank()) {
            return PIELES.equals(tablaElegida)
                    ? "La piel necesita su referencia: es la parte de la referencia del modelo que es la piel "
                            + "(AL0201, PXBHZ...)."
                    : "El color necesita un código: es lo que trae el pedido del cliente (LZZ, 001...).";
        }
        return null;
    }

    private static void validarTabla(String tablaElegida) {
        if (!COLORES.equals(tablaElegida) && !PIELES.equals(tablaElegida)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /** Clave → nombre de todos los clientes del catálogo, en su orden. */
    private Map<String, String> clientes() {
        Map<String, String> clientes = new LinkedHashMap<>();
        catalogoClientes.getClientes().forEach((clave, config) -> clientes.put(clave, nombreDe(config, clave)));
        return clientes;
    }

    /**
     * El cliente pedido si es del catálogo; si no, el primero que ya tenga
     * colores o pieles (al entrar desde el menú se ve una tabla, no una
     * vacía), y si ninguno tiene, el primero.
     */
    private String elegido(String pedido, Map<String, String> clientes) {
        String clave = pedido == null ? "" : clave(pedido);
        if (clientes.containsKey(clave)) {
            return clave;
        }
        return clientes.keySet().stream()
                .filter(candidato -> !tabla.de(candidato).isEmpty() || !pieles.pielesDe(candidato).isEmpty())
                .findFirst()
                .orElse(clientes.keySet().stream().findFirst().orElse(""));
    }

    private static String nombreDe(ClienteConfig config, String clave) {
        return config.getNombre() != null && !config.getNombre().isBlank() ? config.getNombre() : clave;
    }

    private static String volver(String cliente) {
        return "redirect:" + UriComponentsBuilder.fromPath("/colores")
                .queryParam("cliente", clave(cliente))
                .build().toUriString();
    }

    /** Clientes y códigos se teclean o se leen de un excel: "apc " y "APC" son el mismo. */
    private static String clave(String texto) {
        return texto.trim().toUpperCase(Locale.ROOT);
    }
}
