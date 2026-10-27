package com.puntotres.packinglist.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.MemoriaPieles;
import com.puntotres.packinglist.persistence.TablaColores;
import com.puntotres.packinglist.persistence.TemporadaGuardada;
import com.puntotres.packinglist.service.corte.ClienteCorte;
import com.puntotres.packinglist.service.corte.ColorCorte;
import com.puntotres.packinglist.service.corte.ConversionFotos;
import com.puntotres.packinglist.service.corte.ConversorFotos;
import com.puntotres.packinglist.service.corte.DocumentoCorte;
import com.puntotres.packinglist.service.corte.DocumentosCorteService;
import com.puntotres.packinglist.service.corte.FilaCorte;
import com.puntotres.packinglist.service.corte.FotoModelo;
import com.puntotres.packinglist.service.corte.FotosTemporada;
import com.puntotres.packinglist.service.corte.LectorFotos;
import com.puntotres.packinglist.service.corte.OrigenCorte;
import com.puntotres.packinglist.service.corte.PedidoCorte;
import com.puntotres.packinglist.service.corte.PielCorte;
import com.puntotres.packinglist.service.corte.PielesArticulo;
import com.puntotres.packinglist.service.corte.ProduccionCorte;
import com.puntotres.packinglist.service.corte.ProduccionPuntotresExcel;
import com.puntotres.packinglist.service.corte.ResultadoCorte;

/**
 * Documentos del Corte, en tres pantallas: entrada (cliente, temporada y fotos
 * de fotos) → tabla de pieles → descargas.
 *
 * La conversión de las fotos arranca al cargar y trabaja en segundo plano
 * mientras se rellena la tabla (un HEIC de 24 MP tarda segundos); generar
 * espera a que acabe. Lo único que bloquea es lo que impide empezar: sin
 * pedido legible no hay nada que hacer. Todo lo demás son avisos, la carpeta
 * de fotos incluida: sin ella salen las órdenes de corte sin imágenes, y se
 * dice en la tabla y en las descargas.
 */
@Controller
public class DocumentosCorteController {

    private static final MediaType TIPO_DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final MediaType TIPO_XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final String PEDIDO_ILEGIBLE =
            "no tiene el formato esperado: revisa que sea el excel de pedido de ese cliente";
    private static final String PRODUCCION_ILEGIBLE =
            "no tiene el formato esperado: revisa que sea el excel de producción de Puntotres (.xlsx)";
    /**
     * Un envío de la tabla sin nada cargado es una sesión caducada (o el
     * servidor reiniciado): se dice, porque volver a la entrada sin más
     * parecía un fallo y lo tecleado se había perdido sin explicación.
     */
    private static final String SESION_CADUCADA = "La sesión ha caducado (o se ha reiniciado la "
            + "aplicación) y se ha perdido lo cargado: vuelve a subir el pedido y las fotos. Los "
            + "nombres de piel solo se guardan al generar";

    /**
     * Un cliente del desplegable. Con el excel de producción valen todos; con
     * el de pedido, solo los que tienen lector ({@code conPedido}), y los
     * demás se apagan en la pantalla. {@code conTemporadas}: los de
     * {@code pedido-cliente: true}, los únicos con temporadas guardadas.
     */
    public record OpcionCliente(String nombre, boolean conPedido, boolean conTemporadas) {
    }

    private final DocumentosCorteService servicio;
    private final ConversorFotos conversor;
    private final ClientesProperties clientesProperties;
    private final ArchivoTemporadas archivoTemporadas;
    private final MemoriaPieles memoria;
    private final TablaColores tablaColores;
    private final CorteEnCurso enCurso;

    public DocumentosCorteController(DocumentosCorteService servicio, ConversorFotos conversor,
                                     ClientesProperties clientesProperties,
                                     ArchivoTemporadas archivoTemporadas, MemoriaPieles memoria,
                                     TablaColores tablaColores, CorteEnCurso enCurso) {
        this.servicio = servicio;
        this.conversor = conversor;
        this.clientesProperties = clientesProperties;
        this.archivoTemporadas = archivoTemporadas;
        this.memoria = memoria;
        this.tablaColores = tablaColores;
        this.enCurso = enCurso;
    }

    // --- Paso 1: entrada ---

    @GetMapping("/documentos-corte")
    public String entrada(Model model) {
        model.addAttribute("clientes", opcionesDeCliente());
        model.addAttribute("temporadasJs", archivoTemporadas.paraElDesplegable());
        return "documentos-corte";
    }

    /**
     * {@code origen} sin enviar es el excel de pedido, el camino de siempre,
     * para no cambiarle el sentido a quien ya llamaba sin él; la pantalla lo
     * manda siempre, y arranca en el excel de producción.
     */
    @PostMapping("/documentos-corte/cargar")
    public String cargar(@RequestParam String cliente,
                         @RequestParam(defaultValue = "PEDIDO") String origen,
                         @RequestParam(required = false) Long temporadaGuardadaId,
                         @RequestParam(required = false) String temporada,
                         @RequestParam(required = false) MultipartFile pedido,
                         @RequestParam(required = false) MultipartFile produccion,
                         @RequestParam(required = false) MultipartFile fotos,
                         @RequestParam(required = false) List<MultipartFile> carpeta,
                         RedirectAttributes redirect) {
        boolean deProduccion = OrigenCorte.PRODUCCION.name().equalsIgnoreCase(origen.trim());
        // Si algo falla se vuelve a la entrada con el mismo origen elegido, no al de por defecto.
        redirect.addFlashAttribute("origen", deProduccion ? OrigenCorte.PRODUCCION.name()
                : OrigenCorte.PEDIDO.name());
        ClienteCorte clienteCorte;
        String nombreTemporada;
        PedidoCorte pedidoCorte;
        ProduccionCorte deLaProduccion = null;
        List<String> avisosLectura = new ArrayList<>();
        if (deProduccion) {
            // El excel de producción vale para todos los clientes del catálogo.
            if (clientesProperties.clientePara(cliente).isEmpty()) {
                return error(redirect, "El cliente '" + cliente + "' no está en el catálogo");
            }
            clienteCorte = servicio.reglasDeProduccion(cliente);
            // La temporada guardada solo pone el nombre: su excel de pedido no se usa.
            Optional<TemporadaGuardada> guardada =
                    archivoTemporadas.paraElEnvio(temporadaGuardadaId, clienteCorte.clave());
            if (temporadaGuardadaId != null && guardada.isEmpty()) {
                return error(redirect, "La temporada elegida ya no existe o no es de este cliente: "
                        + "vuelve a elegirla o escribe su nombre");
            }
            nombreTemporada = guardada.map(TemporadaGuardada::getTemporada)
                    .orElse(temporada == null ? "" : temporada.trim());
            if (nombreTemporada.isBlank()) {
                return error(redirect, "Falta el nombre de la temporada");
            }
            if (produccion == null || produccion.isEmpty()) {
                return error(redirect, "Sube el excel de producción de Puntotres");
            }
            try {
                deLaProduccion = ProduccionPuntotresExcel.leer(produccion.getBytes(), clienteCorte);
            } catch (IllegalArgumentException e) {
                return error(redirect, "No se pudo leer el excel de producción: " + e.getMessage());
            } catch (IOException | RuntimeException e) {
                return error(redirect, "No se pudo leer el excel de producción: " + PRODUCCION_ILEGIBLE);
            }
            pedidoCorte = deLaProduccion.pedido();
            if (pedidoCorte.articulos().isEmpty()) {
                return error(redirect, "El excel de producción no trae ninguna referencia con color y "
                        + "cantidad: no hay nada que cortar");
            }
            avisosLectura.addAll(pedidoCorte.avisos());
            avisosLectura.addAll(deLaProduccion.avisos());
        } else {
            clienteCorte = servicio.clientePara(cliente).orElse(null);
            if (clienteCorte == null) {
                return error(redirect, "El cliente '" + cliente
                        + "' todavía no tiene documentos del corte con el excel de pedido: usa el excel "
                        + "de producción");
            }
            boolean conExcel = pedido != null && !pedido.isEmpty();
            // Solo si es de este cliente: el desplegable se rellena en el navegador
            // y cambiar de cliente después de elegir dejaría enviada la de otro.
            Optional<TemporadaGuardada> guardada = conExcel ? Optional.empty()
                    : archivoTemporadas.paraElEnvio(temporadaGuardadaId, clienteCorte.clave());
            if (!conExcel && guardada.isEmpty()) {
                return error(redirect, temporadaGuardadaId != null
                        ? "La temporada elegida ya no existe o no es de este cliente: vuelve a elegirla "
                                + "o sube el excel de pedido"
                        : "Elige una temporada guardada o sube el excel de pedido");
            }
            nombreTemporada = temporada != null && !temporada.isBlank() ? temporada.trim()
                    : guardada.map(TemporadaGuardada::getTemporada).orElse("");
            if (nombreTemporada.isBlank()) {
                return error(redirect, "Falta el nombre de la temporada");
            }
            try {
                byte[] excel = conExcel ? pedido.getBytes() : guardada.get().getExcel();
                pedidoCorte = clienteCorte.leerPedido(excel);
            } catch (IllegalArgumentException e) {
                return error(redirect, "No se pudo leer el excel de pedido: "
                        + (e.getMessage() != null ? e.getMessage() : PEDIDO_ILEGIBLE));
            } catch (IOException | RuntimeException e) {
                return error(redirect, "No se pudo leer el excel de pedido: " + PEDIDO_ILEGIBLE);
            }
            if (pedidoCorte.articulos().isEmpty()) {
                return error(redirect, "El excel de pedido no trae ninguna referencia: no hay nada que cortar");
            }
            avisosLectura.addAll(pedidoCorte.avisos());
            pedidoCorte = describirColores(clienteCorte.clave(), pedidoCorte, avisosLectura);
        }

        boolean conZip = fotos != null && !fotos.isEmpty();
        // Un selector de carpeta sin elegir manda igual una parte sin nombre.
        List<MultipartFile> ficheros = carpeta == null ? List.of() : carpeta.stream()
                .filter(fichero -> fichero.getOriginalFilename() != null
                        && !fichero.getOriginalFilename().isBlank())
                .toList();
        // Las fotos son OPCIONALES: sin ellas salen las órdenes de corte con el
        // hueco de la foto en blanco, que es un documento que se puede llevar al
        // puesto de corte. Se dice en la pantalla siguiente y en las descargas,
        // porque una orden sin foto teniendo fotos del bolso sería un descuido.
        boolean sinFotos = !conZip && ficheros.isEmpty();
        if (conZip && !ficheros.isEmpty()) {
            return error(redirect, "Sube la carpeta de fotos o su zip, no los dos");
        }

        enCurso.reiniciar();
        Path directorio = null;
        try {
            directorio = Files.createTempDirectory(CorteEnCurso.PREFIJO_DIRECTORIO);
            FotosTemporada leidas;
            if (sinFotos) {
                leidas = FotosTemporada.vacia();
            } else if (conZip) {
                leidas = leerZip(fotos, pedidoCorte, clienteCorte, directorio);
            } else {
                leidas = new LectorFotos().leer(ficheros.stream()
                        .map(fichero -> new LectorFotos.Fichero(fichero.getOriginalFilename(),
                                fichero::getInputStream))
                        .toList(), pedidoCorte.modelos(), clienteCorte::partir, directorio);
            }
            ConversionFotos conversion = conversor.convertir(leidas.todas(), directorio);
            List<String> avisos = new ArrayList<>(avisosLectura);
            avisos.addAll(leidas.avisos());
            enCurso.cargar(clienteCorte.clave(), nombreDe(clienteCorte.clave()), nombreTemporada,
                    directorio, pedidoCorte, leidas, conversion, sinFotos, avisos,
                    deProduccion ? OrigenCorte.PRODUCCION : OrigenCorte.PEDIDO);
        } catch (IllegalArgumentException e) {
            CorteEnCurso.borrar(directorio);
            return error(redirect, "No se pudo usar la carpeta de fotos: " + e.getMessage());
        } catch (IOException e) {
            CorteEnCurso.borrar(directorio);
            return error(redirect, conZip ? "No se pudo abrir el zip de fotos: ¿es un fichero .zip?"
                    : "No se pudieron leer las fotos de la carpeta: vuelve a elegirla");
        }

        if (deLaProduccion != null) {
            // El excel de producción ya dice la piel y su color: la memoria no se consulta.
            deLaProduccion.rellenar(enCurso.getFilas());
        } else {
            recordarPieles(clienteCorte.clave());
        }
        enCurso.ajustarColumnasALasFilas();
        return "redirect:/documentos-corte/pieles";
    }

    /** Lo que la memoria de pieles recuerda de cada fila, para no teclearlo cada temporada. */
    private void recordarPieles(String cliente) {
        for (FilaCorte fila : enCurso.getFilas()) {
            MemoriaPieles.Recordadas recordadas = memoria.buscar(cliente,
                    fila.referencia().piel(), fila.referencia().referencia());
            fila.setNombrePiel(recordadas.nombrePiel());
            fila.setForro(recordadas.forro());
            fila.setCombinaciones(recordadas.combinaciones());
            List<ColorCorte> colores = fila.colores();
            for (int indice = 0; indice < colores.size(); indice++) {
                MemoriaPieles.ColoresRecordados recordados = memoria.buscarColores(cliente,
                        fila.referencia().referencia(), colores.get(indice).codigo());
                // Sin color recordado, la piel principal se queda con el del bolso.
                if (!recordados.colorPiel().isEmpty()) {
                    fila.setColorPiel(indice, recordados.colorPiel());
                }
                fila.setColorForro(indice, recordados.colorForro());
                fila.setColoresCombinacion(indice, recordados.coloresCombinacion());
            }
        }
    }

    // --- Paso 2: pieles ---

    @GetMapping("/documentos-corte/pieles")
    public String pieles(Model model, RedirectAttributes redirect) {
        if (!enCurso.tieneCarga()) {
            // Se llega aquí desde "volver a las pieles" o un enlace guardado: sin
            // decirlo, volver a la entrada parecía que el botón llevaba a otro sitio.
            return error(redirect, SESION_CADUCADA);
        }
        ConversionFotos conversion = enCurso.getConversion();
        model.addAttribute("cliente", enCurso.getNombreCliente());
        model.addAttribute("temporada", enCurso.getTemporada());
        model.addAttribute("filas", vistaDeFilas());
        model.addAttribute("columnas", IntStream.rangeClosed(1, enCurso.getColumnasCombinacion())
                .boxed().toList());
        model.addAttribute("puedeAnadirColumna",
                enCurso.getColumnasCombinacion() < MemoriaPieles.MAXIMO_COMBINACIONES);
        model.addAttribute("fotosTotal", conversion.total());
        model.addAttribute("fotosHechas", conversion.hechas());
        model.addAttribute("fotosTerminadas", conversion.terminada());
        // A la vista y no en los avisos plegables: de las fotos que faltan se
        // entera quien está a punto de generar, no quien despliega la lista.
        model.addAttribute("sinFotos", enCurso.isSinFotos());
        model.addAttribute("deProduccion", enCurso.isDeProduccion());
        model.addAttribute("avisos", enCurso.getAvisosCarga());
        return "documentos-corte-pieles";
    }

    @PostMapping("/documentos-corte/pieles/combinacion")
    public String anadirCombinacion(@ModelAttribute PielesForm form, RedirectAttributes redirect) {
        if (!enCurso.tieneCarga()) {
            return error(redirect, SESION_CADUCADA);
        }
        List<String> errores = aplicar(form);
        if (!errores.isEmpty()) {
            redirect.addFlashAttribute("errores", errores);
        }
        enCurso.anadirColumnaCombinacion();
        return "redirect:/documentos-corte/pieles";
    }

    @GetMapping("/documentos-corte/progreso")
    @ResponseBody
    public Map<String, Object> progreso() {
        ConversionFotos conversion = enCurso.getConversion();
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("hechas", conversion == null ? 0 : conversion.hechas());
        estado.put("total", conversion == null ? 0 : conversion.total());
        estado.put("terminada", conversion == null || conversion.terminada());
        return estado;
    }

    @PostMapping("/documentos-corte/generar")
    public String generar(@ModelAttribute PielesForm form, RedirectAttributes redirect) {
        if (!enCurso.tieneCarga()) {
            return error(redirect, SESION_CADUCADA);
        }
        List<String> errores = aplicar(form);
        if (!errores.isEmpty()) {
            // Generar con el número de antes sería dar por bueno algo que se
            // ha tecleado mal: se vuelve a la tabla a corregirlo.
            redirect.addFlashAttribute("errores", errores);
            return "redirect:/documentos-corte/pieles";
        }
        ConversionFotos conversion = enCurso.getConversion();
        if (!conversion.terminada()) {
            // No se espera dentro de la petición: en el camino Java una
            // temporada de HEIC puede ser media hora, más de lo que aguantan
            // un proxy o el navegador. Lo tecleado ya está aplicado.
            redirect.addFlashAttribute("error", "Las fotos aún se están preparando ("
                    + conversion.hechas() + " de " + conversion.total()
                    + "): el botón de generar se activa en cuanto acaben");
            return "redirect:/documentos-corte/pieles";
        }
        if (!enCurso.isDeProduccion()) {
            guardarPieles();
        }
        try {
            enCurso.getConversion().esperar();
            Path salida = enCurso.getDirectorio().resolve("documentos");
            CorteEnCurso.borrar(salida);
            ResultadoCorte resultado = servicio.generar(enCurso.getNombreCliente(),
                    enCurso.getTemporada(), enCurso.getFilas(), enCurso.getFotos(),
                    enCurso.getConversion()::reducida, salida);
            enCurso.setResultado(resultado);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            redirect.addFlashAttribute("error", "Se ha interrumpido la generación: vuelve a darle");
            return "redirect:/documentos-corte/pieles";
        } catch (IOException | RuntimeException e) {
            redirect.addFlashAttribute("error", "No se pudieron generar los documentos: "
                    + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            return "redirect:/documentos-corte/pieles";
        }
        return "redirect:/documentos-corte/resultados";
    }

    /**
     * Lo tecleado pasa a la memoria de pieles. Solo con el excel de pedido:
     * el de producción ya trae las pieles escritas y no la usa.
     */
    private void guardarPieles() {
        for (FilaCorte fila : enCurso.getFilas()) {
            memoria.recordar(enCurso.getClaveCliente(), fila.referencia().piel(),
                    fila.referencia().referencia(), fila.getNombrePiel(), fila.getForro(),
                    fila.getCombinaciones());
            List<ColorCorte> colores = fila.colores();
            for (int indice = 0; indice < colores.size(); indice++) {
                // Las de las combinaciones, emparejadas con las que tienen nombre:
                // es como se guardan los nombres y como se vuelven a leer.
                PielesArticulo pieles = fila.pieles(indice);
                memoria.recordarColores(enCurso.getClaveCliente(), fila.referencia().referencia(),
                        colores.get(indice).codigo(), fila.getNombrePiel(), pieles.principal().color(),
                        pieles.forro().color(),
                        pieles.combinaciones().stream().map(PielCorte::color).toList());
            }
        }
    }

    // --- Paso 3: resultados ---

    @GetMapping("/documentos-corte/resultados")
    public String resultados(Model model) {
        if (enCurso.getResultado() == null) {
            return enCurso.tieneCarga() ? "redirect:/documentos-corte/pieles" : "redirect:/documentos-corte";
        }
        List<String> avisos = new ArrayList<>(enCurso.getAvisosCarga());
        avisos.addAll(enCurso.getConversion().avisos());
        avisos.addAll(enCurso.getResultado().avisos());
        model.addAttribute("cliente", enCurso.getNombreCliente());
        model.addAttribute("temporada", enCurso.getTemporada());
        model.addAttribute("ordenes", enCurso.getResultado().ordenes());
        // Un solo Word con todos los modelos, o ninguno.
        model.addAttribute("fotos", enCurso.getResultado().fotos().stream().findFirst().orElse(null));
        model.addAttribute("sinFotos", enCurso.isSinFotos());
        model.addAttribute("avisos", avisos);
        return "documentos-corte-resultados";
    }

    @GetMapping("/documentos-corte/descargar/{nombreFichero}")
    public ResponseEntity<byte[]> descargar(@PathVariable String nombreFichero) throws IOException {
        if (enCurso.getResultado() == null) {
            return ResponseEntity.notFound().build();
        }
        Optional<DocumentoCorte> documento = enCurso.getResultado().documentos().stream()
                .filter(candidato -> candidato.nombreFichero().equals(nombreFichero))
                .findFirst();
        if (documento.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(nombreFichero.endsWith(".xlsx") ? TIPO_XLSX : TIPO_DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        // UTF-8: el Word de fotos se llama "... - Fotos Artículos.docx".
                        .filename(documento.get().nombreFichero(), StandardCharsets.UTF_8).build().toString())
                .body(Files.readAllBytes(documento.get().fichero()));
    }

    @GetMapping("/documentos-corte/nuevo")
    public String nuevo() {
        enCurso.reiniciar();
        return "redirect:/documentos-corte";
    }

    // --- Internos ---

    /**
     * La descripción de los colores que el pedido no describe (APC), sacada
     * de la Tabla colores Clientes. Los códigos que no estaban se añaden a la
     * tabla vacíos, para que salgan en su pantalla listos para rellenar, y de
     * todos los que siguen sin descripción se avisa: salen solo con el código.
     */
    private PedidoCorte describirColores(String cliente, PedidoCorte pedido, List<String> avisos) {
        if (pedido.codigosSinDescripcion().isEmpty()) {
            return pedido;
        }
        TablaColores.Consulta consulta = tablaColores.consultar(cliente, pedido.codigosSinDescripcion());
        List<String> sinDescripcion = consulta.sinDescripcion();
        if (!sinDescripcion.isEmpty()) {
            String aviso = "Colores sin descripción en la Tabla colores Clientes, que salen solo con "
                    + "el código: " + String.join(", ", sinDescripcion);
            if (!consulta.anadidos().isEmpty()) {
                aviso += ". Se han añadido a la tabla los que no estaban ("
                        + String.join(", ", consulta.anadidos()) + ")";
            }
            avisos.add(aviso + ". Se rellenan desde el menú, en «Tabla colores Clientes»");
        }
        return pedido.conDescripciones(consulta.descripciones());
    }

    /**
     * Pasa lo tecleado a las filas de la sesión y devuelve los bolsos que no
     * se entienden. Un bolso vacío es 0 (esa orden no sale); uno ilegible deja
     * el número que había y se dice. Un color de piel que no ha llegado (null)
     * se deja como estaba.
     */
    private List<String> aplicar(PielesForm form) {
        List<String> errores = new ArrayList<>();
        List<FilaCorte> filas = enCurso.getFilas();
        for (int i = 0; i < Math.min(filas.size(), form.getFilas().size()); i++) {
            PielesForm.Fila datos = form.getFilas().get(i);
            if (datos == null) {
                continue;
            }
            FilaCorte fila = filas.get(i);
            fila.setNombrePiel(datos.getNombrePiel());
            fila.setForro(datos.getForro());
            fila.setCombinaciones(datos.getCombinaciones());
            if (datos.getFotoPrincipal() != null) {
                fila.setFotoPrincipal(datos.getFotoPrincipal());
            }
            if (datos.getIncluir() != null) {
                fila.setIncluida(datos.getIncluir());
            }
            List<String> bolsos = datos.getBolsos();
            for (int color = 0; color < Math.min(bolsos.size(), fila.colores().size()); color++) {
                String valor = bolsos.get(color) == null ? "" : bolsos.get(color).trim();
                try {
                    int numero = valor.isEmpty() ? 0 : Integer.parseInt(valor);
                    if (numero < 0) {
                        throw new NumberFormatException();
                    }
                    fila.setBolsos(color, numero);
                } catch (NumberFormatException e) {
                    errores.add("Los bolsos '" + valor + "' de " + fila.referencia().referencia()
                            + " en " + fila.colores().get(color).nombre()
                            + " no son un número: se deja " + fila.colores().get(color).bolsos());
                }
            }
            List<PielesForm.ColoresDeUnColor> colores = datos.getColores();
            for (int color = 0; color < Math.min(colores.size(), fila.colores().size()); color++) {
                PielesForm.ColoresDeUnColor deEseColor = colores.get(color);
                if (deEseColor == null) {
                    continue;
                }
                if (deEseColor.getPiel() != null) {
                    fila.setColorPiel(color, deEseColor.getPiel());
                }
                if (deEseColor.getForro() != null) {
                    fila.setColorForro(color, deEseColor.getForro());
                }
                if (!deEseColor.getCombinaciones().isEmpty()) {
                    fila.setColoresCombinacion(color, deEseColor.getCombinaciones());
                }
            }
        }
        return errores;
    }

    private List<FilaPielesVista> vistaDeFilas() {
        List<FilaPielesVista> vista = new ArrayList<>();
        List<FilaCorte> filas = enCurso.getFilas();
        int columnas = enCurso.getColumnasCombinacion();
        for (int i = 0; i < filas.size(); i++) {
            FilaCorte fila = filas.get(i);
            List<ColorCorte> colores = fila.colores();
            List<FilaPielesVista.ColorVista> coloresVista = new ArrayList<>();
            for (int color = 0; color < colores.size(); color++) {
                coloresVista.add(new FilaPielesVista.ColorVista(color, colores.get(color).codigo(),
                        colores.get(color).nombre(), colores.get(color).bolsos(), fila.getColorPiel(color),
                        fila.getColorForro(color), unaPorColumna(fila.getColoresCombinacion(color), columnas),
                        colores.get(color).prensa()));
            }
            List<String> fotos = enCurso.getFotos().de(fila.referencia().modelo()).stream()
                    .map(FotoModelo::nombreOriginal).toList();
            vista.add(new FilaPielesVista(i, fila.isIncluida(), fila.referencia().referencia(),
                    fila.referencia().modelo(), fila.referencia().piel(), coloresVista,
                    fila.getNombrePiel(), fila.getForro(), unaPorColumna(fila.getCombinaciones(), columnas),
                    fotos, Math.min(fila.getFotoPrincipal(), Math.max(0, fotos.size() - 1))));
        }
        return vista;
    }

    /** Una casilla por columna de combinación: las que falten, vacías; las que sobren, fuera. */
    private static List<String> unaPorColumna(List<String> valores, int columnas) {
        List<String> casillas = new ArrayList<>(valores);
        while (casillas.size() < columnas) {
            casillas.add("");
        }
        return casillas.subList(0, columnas);
    }

    private static FotosTemporada leerZip(MultipartFile fotos, PedidoCorte pedido, ClienteCorte cliente,
                                          Path directorio) throws IOException {
        Path zip = directorio.resolve("temporada.zip");
        fotos.transferTo(zip);
        try {
            return new LectorFotos().leer(zip, pedido.modelos(), cliente::partir, directorio);
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    private Map<String, OpcionCliente> opcionesDeCliente() {
        Map<String, OpcionCliente> opciones = new LinkedHashMap<>();
        clientesProperties.getClientes().forEach((clave, config) -> opciones.put(clave,
                new OpcionCliente(nombreDe(config, clave), servicio.clientePara(clave).isPresent(),
                        config.isPedidoCliente())));
        return opciones;
    }

    private String nombreDe(String clave) {
        return clientesProperties.clientePara(clave)
                .map(config -> nombreDe(config, clave))
                .orElse(clave);
    }

    private static String nombreDe(ClienteConfig config, String clave) {
        return config.getNombre() != null && !config.getNombre().isBlank() ? config.getNombre() : clave;
    }

    private static String error(RedirectAttributes redirect, String mensaje) {
        redirect.addFlashAttribute("error", mensaje);
        return "redirect:/documentos-corte";
    }
}
