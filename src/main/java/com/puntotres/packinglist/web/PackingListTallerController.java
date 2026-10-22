package com.puntotres.packinglist.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.config.CatalogoTaras;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.TablaColores;
import com.puntotres.packinglist.persistence.TemporadaGuardada;
import com.puntotres.packinglist.service.taller.ConversionFormatoTallerService;
import com.puntotres.packinglist.service.taller.DigestionTaller;
import com.puntotres.packinglist.service.taller.PedidoParaConversion;
import com.puntotres.packinglist.service.taller.DigestionTallerService;
import com.puntotres.packinglist.service.taller.FilaDigerida;
import com.puntotres.packinglist.service.taller.GeneradorPackingTaller;
import com.puntotres.packinglist.service.taller.GrupoReferencia;
import com.puntotres.packinglist.service.taller.ResultadoPackingTaller;
import com.puntotres.packinglist.service.taller.TallerColisExcel;

import jakarta.validation.Valid;

/**
 * La cuarta vía de entrada: el taller manda su propio packing list en excel y
 * el programa lo regenera desde cero con las normas del cliente.
 *
 * Son dos sub-pasos dentro de la etapa de carga:
 * <ol>
 * <li><b>digerir</b>: se leen el packing del taller y el pedido del cliente, y
 * se cruzan con lo que el programa recuerda de esas referencias;</li>
 * <li><b>ajustar</b>: el usuario corrige cartones, unidades por caja y
 * cantidades objetivo, y genera.</li>
 * </ol>
 *
 * A partir de "Generar Packing" no hay nada especial: sale un
 * {@code EnvioInput} como el de cualquier otra entrada y sigue por
 * {@link PreparacionRevisionService} hasta la pantalla de revisión de siempre.
 *
 * Desde la revisión se puede volver al ajuste sin resubir nada: los dos
 * ficheros y la digestión viven en {@link TallerEnCurso}.
 */
@Controller
public class PackingListTallerController {

    private static final MediaType TIPO_XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DigestionTallerService digestionService;
    private final ConversionFormatoTallerService conversionService;
    private final GeneradorPackingTaller generadorTaller;
    private final PreparacionRevisionService preparacion;
    private final ClientesProperties clientesProperties;
    private final CatalogoTaras catalogoTaras;
    private final ArchivoTemporadas archivoTemporadas;
    private final TablaColores tablaColores;
    private final AtributosEntrada atributosEntrada;
    private final TallerEnCurso tallerEnCurso;
    private final EnvioEnCurso envioEnCurso;

    public PackingListTallerController(DigestionTallerService digestionService,
                                       ConversionFormatoTallerService conversionService,
                                       GeneradorPackingTaller generadorTaller,
                                       PreparacionRevisionService preparacion,
                                       ClientesProperties clientesProperties,
                                       CatalogoTaras catalogoTaras,
                                       ArchivoTemporadas archivoTemporadas,
                                       TablaColores tablaColores,
                                       AtributosEntrada atributosEntrada,
                                       TallerEnCurso tallerEnCurso,
                                       EnvioEnCurso envioEnCurso) {
        this.digestionService = digestionService;
        this.conversionService = conversionService;
        this.generadorTaller = generadorTaller;
        this.preparacion = preparacion;
        this.clientesProperties = clientesProperties;
        this.catalogoTaras = catalogoTaras;
        this.archivoTemporadas = archivoTemporadas;
        this.tablaColores = tablaColores;
        this.atributosEntrada = atributosEntrada;
        this.tallerEnCurso = tallerEnCurso;
        this.envioEnCurso = envioEnCurso;
    }

    // --- Paso 1a: carga y digestión ---

    @PostMapping("/packing-list/taller/digerir")
    public String digerir(@Valid @ModelAttribute EnvioForm envioForm,
                          BindingResult errores, Model model) {
        ClienteConfig cliente = clientesProperties.clientePara(envioForm.getCliente()).orElse(null);
        if (cliente == null) {
            errores.rejectValue("cliente", "cliente.desconocido",
                    "Cliente desconocido: " + envioForm.getCliente());
        }

        // Con "Excel Formato Taller" lo que se digiere es lo que ha devuelto
        // la conversión, nunca el excel original del taller: ese no lo sabe
        // leer el programa, y es justo lo que la conversión arregla.
        byte[] excelTaller;
        String nombreTaller;
        if (envioForm.esFormatoTaller()) {
            excelTaller = tallerEnCurso.getExcelConvertido();
            nombreTaller = tallerEnCurso.getNombreExcelConvertido();
            if (excelTaller == null) {
                errores.rejectValue("excelTallerOriginal", "excelTallerOriginal.sinConvertir",
                        "Sube el excel del taller y dale a \"Generar Formato con IA\" antes de "
                                + "digerirlo");
            }
        } else {
            // El excel del taller solo hace falta la primera vez: al elegir
            // hoja a mano se reenvía el formulario y el fichero ya está en sesión.
            excelTaller = bytesDe(envioForm.getExcelTaller());
            nombreTaller = excelTaller == null ? null
                    : envioForm.getExcelTaller().getOriginalFilename();
            if (excelTaller == null) {
                excelTaller = tallerEnCurso.getExcelTaller();
            }
            if (excelTaller == null) {
                errores.rejectValue("excelTaller", "excelTaller.obligatorio",
                        "Sube el packing list que ha mandado el taller");
            }
        }
        if (errores.hasErrors()) {
            return volverALaEntrada(envioForm, model, null);
        }

        // El pedido del cliente puede venir de tres sitios, y en este orden:
        // subido a mano en este envío, de la temporada guardada que se haya
        // elegido, o de la sesión si es una vuelta a la entrada. Lo subido a
        // mano manda sobre lo guardado: es lo que se acaba de elegir.
        byte[] excelPedido = bytesDe(envioForm.getPedidoCliente());
        String nombrePedido = excelPedido == null ? null
                : envioForm.getPedidoCliente().getOriginalFilename();
        if (excelPedido == null) {
            TemporadaGuardada guardada = archivoTemporadas
                    .paraElEnvio(envioForm.getTemporadaGuardadaId(), envioForm.getCliente())
                    .orElse(null);
            if (guardada != null) {
                excelPedido = guardada.getExcel();
                nombrePedido = guardada.getNombreFichero();
            }
        }
        if (excelPedido == null) {
            excelPedido = tallerEnCurso.getExcelPedido();
        }
        if (cliente.isPedidoCliente() && excelPedido == null) {
            errores.rejectValue("pedidoCliente", "pedidoCliente.obligatorio",
                    "Sube el excel de pedido de " + cliente.getNombre() + ": de ahí sale "
                            + "cuánto hay que enviar de cada cosa y a qué destinación");
            return volverALaEntrada(envioForm, model, null);
        }

        DigestionTaller digestion;
        try {
            digestion = digestionService.digerir(envioForm.getCliente(), excelTaller,
                    excelPedido, envioForm.getHojaTaller());
        } catch (TallerColisExcel.HojaNoEncontradaException e) {
            // Se guarda el fichero para que el usuario pueda elegir la hoja
            // sin volver a subirlo.
            guardarFicheros(excelTaller, nombreTaller, excelPedido, nombrePedido);
            return volverALaEntrada(envioForm, model, e.getMessage(), e.hojasEncontradas());
        } catch (TallerColisExcel.TallerExcelException | IOException e) {
            return volverALaEntrada(envioForm, model, e.getMessage());
        }

        guardarFicheros(excelTaller, nombreTaller, excelPedido, nombrePedido);
        tallerEnCurso.setClaveCliente(envioForm.getCliente());
        tallerEnCurso.setCabecera(cabeceraDe(envioForm));
        tallerEnCurso.setAlturaMaximaPaletCm(envioForm.getAlturaMaximaPaletCm());
        tallerEnCurso.setDigestion(digestion);

        return pintarAjuste(model, null);
    }

    // --- Paso 1a bis: un excel de taller con formato propio ---

    /**
     * "Generar Formato con IA": pasa el excel de un taller con formato propio
     * al formato Puntotres con Claude, lo guarda en la sesión y contesta en
     * JSON, sin recargar: la pantalla de entrada sigue siendo la misma etapa,
     * y lo único que cambia en ella es que aparece la descarga y se enciende
     * "Digerir Datos Taller".
     *
     * El excel de pedido, si lo hay, viaja como catálogo para que la
     * conversión escriba referencias, colores y códigos como el cliente.
     */
    @PostMapping(value = "/packing-list/taller/convertir", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> convertir(@ModelAttribute EnvioForm envioForm) {
        ClienteConfig cliente = clientesProperties.clientePara(envioForm.getCliente()).orElse(null);
        if (cliente == null) {
            return error("Elige primero el cliente: la conversión depende de cómo escribe "
                    + "cada cliente sus referencias y destinaciones");
        }
        byte[] original = bytesDe(envioForm.getExcelTallerOriginal());
        if (original == null) {
            return error("Sube el excel que ha mandado el taller");
        }
        // Una conversión vieja no puede quedarse a mano mientras se hace la
        // nueva: si esta falla, digerir no debe encontrar la anterior.
        tallerEnCurso.setExcelConvertido(null, null);

        byte[] excelPedido = bytesDe(envioForm.getPedidoCliente());
        if (excelPedido == null) {
            excelPedido = archivoTemporadas
                    .paraElEnvio(envioForm.getTemporadaGuardadaId(), envioForm.getCliente())
                    .map(TemporadaGuardada::getExcel)
                    .orElse(null);
        }
        // La tabla de colores del cliente (Colores y Pieles) es lo que permite
        // pasar "Negro" a LZZ; sin ella el pedido de APC no dice nombres.
        Map<String, String> coloresCliente = new LinkedHashMap<>();
        tablaColores.de(envioForm.getCliente())
                .forEach(color -> coloresCliente.put(color.getCodigo(), color.getDescripcion()));
        PedidoParaConversion pedido = PedidoParaConversion.cargar(cliente.getPlantilla(),
                excelPedido, coloresCliente, cliente);

        String nombreOriginal = envioForm.getExcelTallerOriginal().getOriginalFilename();
        ConversionFormatoTallerService.Resultado resultado;
        try {
            resultado = conversionService.convertir(original, nombreOriginal,
                    envioForm.getCliente(), pedido, envioForm.getHojaElegida());
        } catch (ConversionFormatoTallerService.ConversionException e) {
            return error(e.getMessage());
        }

        String nombre = nombreConvertido(nombreOriginal, resultado.packing().hoja(),
                resultado.packing().hojasNoConvertidas().isEmpty()
                        && envioForm.getHojaElegida() == null);
        tallerEnCurso.setExcelConvertido(resultado.excel(), nombre);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("ok", true);
        respuesta.put("nombreFichero", nombre);
        respuesta.put("hoja", resultado.packing().hoja());
        respuesta.put("hojasNoConvertidas", resultado.packing().hojasNoConvertidas());
        respuesta.put("filas", resultado.packing().filas().size());
        respuesta.put("notas", resultado.packing().notas().size());
        // Los avisos como los pinta la pantalla: los del fichero sueltos, los
        // de fila plegados por consecuencia, y de lo completado solo el recuento.
        AgrupadorAvisosConversion.Vista avisos = AgrupadorAvisosConversion.agrupar(resultado.packing());
        respuesta.put("avisos", avisos.generales());
        respuesta.put("bloques", avisos.bloques());
        respuesta.put("completados", avisos.completados());
        respuesta.put("tokens", Map.of("entrada", resultado.uso().tokensEntrada(),
                "salida", resultado.uso().tokensSalida()));
        return ResponseEntity.ok(respuesta);
    }

    /** La descarga del excel convertido: el botón de la impresora de la entrada. */
    @GetMapping("/packing-list/taller/formato-puntotres")
    public ResponseEntity<byte[]> descargarConvertido() {
        if (tallerEnCurso.getExcelConvertido() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(TIPO_XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(tallerEnCurso.getNombreExcelConvertido(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(tallerEnCurso.getExcelConvertido());
    }

    /**
     * "Packing Fez.xlsx" → "Packing Fez - Formato Puntotres.xlsx". Cuando el
     * libro tenía varios envíos, el nombre lleva la hoja convertida para que
     * dos descargas del mismo fichero no se confundan.
     */
    static String nombreConvertido(String nombreOriginal, String hoja, boolean hojaUnica) {
        String base = nombreOriginal == null || nombreOriginal.isBlank()
                ? "Packing taller" : nombreOriginal.trim();
        int punto = base.lastIndexOf('.');
        if (punto > 0) {
            base = base.substring(0, punto);
        }
        if (!hojaUnica && hoja != null && !hoja.isBlank()) {
            base += " (" + hoja.replaceAll("[\\\\/:*?\"<>|]", "-").trim() + ")";
        }
        return base + " - Formato Puntotres.xlsx";
    }

    private static ResponseEntity<Map<String, Object>> error(String mensaje) {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("ok", false);
        respuesta.put("error", mensaje);
        return ResponseEntity.badRequest().body(respuesta);
    }

    // --- Paso 1b: ajuste ---

    @GetMapping("/packing-list/taller/ajuste")
    public String ajuste(Model model, RedirectAttributes redirect) {
        if (tallerEnCurso.estaVacio()) {
            redirect.addFlashAttribute("mensaje",
                    "No hay ninguna entrega de taller en curso: empieza subiendo su packing list.");
            return "redirect:/packing-list";
        }
        return pintarAjuste(model, null);
    }

    @PostMapping("/packing-list/taller/previsualizar")
    public String previsualizar(@ModelAttribute AjusteTallerForm ajusteForm,
                                Model model, RedirectAttributes redirect) {
        if (tallerEnCurso.estaVacio()) {
            return sinEntrega(redirect);
        }
        aplicarAjustes(ajusteForm);
        return pintarAjuste(model, generar());
    }

    @PostMapping("/packing-list/taller/generar")
    public String generar(@ModelAttribute AjusteTallerForm ajusteForm,
                          Model model, RedirectAttributes redirect) {
        if (tallerEnCurso.estaVacio()) {
            return sinEntrega(redirect);
        }
        aplicarAjustes(ajusteForm);

        DigestionTaller digestion = tallerEnCurso.getDigestion();
        if (!digestion.sePuedeGenerar()) {
            return pintarAjuste(model, null);
        }

        ResultadoPackingTaller packing = generar();
        if (!packing.sePuedeGenerar()) {
            return pintarAjuste(model, packing);
        }

        // Se recuerda lo usado solo cuando el envío sale de verdad: memorizar
        // un ajuste que después no se ha generado daría por buena una prueba.
        digestionService.memorizar(tallerEnCurso.getClaveCliente(), digestion);

        ClienteConfig cliente = clientesProperties
                .clientePara(tallerEnCurso.getClaveCliente()).orElseThrow();
        List<String> avisosPrevios = new ArrayList<>();
        avisosPrevios.add("Packing generado a partir del fichero del taller '"
                + tallerEnCurso.getNombreExcelTaller() + "': las cajas y los palets los ha "
                + "repartido el programa, no el taller");
        avisosPrevios.addAll(digestion.getAvisos());
        avisosPrevios.addAll(packing.getAvisos());

        preparacion.preparar(packing.getEnvio(), tallerEnCurso.getCabecera(), cliente,
                tallerEnCurso.getExcelPedido(), tallerEnCurso.getNombreExcelPedido(),
                avisosPrevios, envioEnCurso);
        return "redirect:/revision";
    }

    // --- Piezas ---

    private ResultadoPackingTaller generar() {
        return generadorTaller.generar(tallerEnCurso.getClaveCliente(),
                tallerEnCurso.getDigestion().aFilasAjustadas(),
                tallerEnCurso.getAlturaMaximaPaletCm());
    }

    /**
     * Vuelca lo tecleado sobre la digestión guardada. Las filas se casan por
     * POSICIÓN: dos referencias pueden compartir cualquier dato visible, y
     * casarlas por nombre haría que un cambio acabara en la fila de al lado.
     */
    private void aplicarAjustes(AjusteTallerForm form) {
        List<GrupoReferencia> grupos = tallerEnCurso.getDigestion().getGrupos();
        for (int g = 0; g < grupos.size() && g < form.getGrupos().size(); g++) {
            AjusteTallerForm.GrupoEditado editado = form.getGrupos().get(g);
            grupos.get(g).corregir(editado.getMedidaCaja(), editado.getUnidadesPorCaja(),
                    editado.getPesoBrutoKg());

            List<FilaDigerida> filas = grupos.get(g).getFilas();
            for (int f = 0; f < filas.size() && f < editado.getFilas().size(); f++) {
                AjusteTallerForm.FilaEditada fila = editado.getFilas().get(f);
                Map<String, Integer> cantidades = fila.getObjetivos();
                Map<String, String> pedidos = fila.getPedidos();
                // La unión de las dos claves, no solo las cantidades: se puede
                // teclear el número de pedido de una destinación sin tocar su
                // cantidad, y perderlo sería el mismo fallo silencioso que
                // tenía antes la fila que el pedido no reconocía.
                for (String destino : destinosEditados(cantidades, pedidos)) {
                    filas.get(f).corregirObjetivo(destino,
                            cantidades == null ? null : cantidades.get(destino),
                            pedidos == null ? null : pedidos.get(destino));
                }
            }
        }
    }

    /** Las destinaciones que trae el formulario, por cantidad o por pedido. */
    private static java.util.Set<String> destinosEditados(Map<String, Integer> cantidades,
                                                          Map<String, String> pedidos) {
        java.util.Set<String> destinos = new java.util.LinkedHashSet<>();
        if (cantidades != null) {
            destinos.addAll(cantidades.keySet());
        }
        if (pedidos != null) {
            destinos.addAll(pedidos.keySet());
        }
        return destinos;
    }

    private String pintarAjuste(Model model, ResultadoPackingTaller packing) {
        DigestionTaller digestion = tallerEnCurso.getDigestion();
        model.addAttribute("digestion", digestion);
        model.addAttribute("cliente", clientesProperties
                .clientePara(tallerEnCurso.getClaveCliente())
                .map(ClienteConfig::getNombre).orElse(tallerEnCurso.getClaveCliente()));
        model.addAttribute("nombreExcelTaller", tallerEnCurso.getNombreExcelTaller());
        // Cada cliente llama a su número de pedido de otra forma, y quien
        // teclea tiene delante el documento del cliente, no el del programa.
        model.addAttribute("etiquetaPedido", clientesProperties
                .clientePara(tallerEnCurso.getClaveCliente())
                .map(ClienteConfig::getEtiquetaPedido).orElse("Nº pedido"));
        model.addAttribute("destinos", digestion.getDestinosActivos());
        List<String> tamanos = tamanosParaElDesplegable(digestion);
        model.addAttribute("tamanosCaja", tamanos);
        model.addAttribute("tamanosSinTara", tamanos.stream()
                .filter(tamano -> catalogoTaras.taraPara(tamano).isEmpty())
                .toList());

        // Los avisos que hablan de una referencia se pintan encima de su
        // tabla; los del envío o del fichero entero, en la lista de arriba.
        AvisosDelAjuste avisos = AvisosDelAjuste.repartir(digestion.getDetalle(),
                digestion.getGrupos().stream().map(GrupoReferencia::getReferencia).toList());
        model.addAttribute("avisos", avisos.getGenerales());
        model.addAttribute("avisosPorReferencia", avisos.getPorReferencia());

        List<String> bloqueos = new ArrayList<>(digestion.getBloqueos());
        for (String pendiente : digestion.referenciasPendientes()) {
            bloqueos.add("De " + pendiente + " falta decir cuántas unidades entran en una caja");
        }
        if (packing != null) {
            bloqueos.addAll(packing.getBloqueos());
            model.addAttribute("resumen", packing.getResumen());
            model.addAttribute("avisosPacking", packing.getAvisos());
        }

        // Repartir más de lo que ha llegado impide pasar a la revisión, pero
        // NO apaga el botón de generar: se corrige tecleando en esta misma
        // pantalla, y con el botón apagado el usuario tendría que descubrir
        // que hay que pasar por Previsualizar para volver a encenderlo. Por
        // eso se cuenta aparte, y por eso se recalcula al pintar: depende de
        // lo que se acaba de teclear y desaparece en cuanto se corrige.
        model.addAttribute("puedeIntentarGenerar", bloqueos.isEmpty());
        bloqueos.addAll(digestion.repartosImposibles());

        model.addAttribute("bloqueos", bloqueos);
        return "taller-ajuste";
    }

    /**
     * Las medidas del catálogo de taras más las que ya estén en uso. Una
     * medida sin tara conocida tiene que seguir apareciendo, o el desplegable
     * cambiaría el cartón de la referencia al guardar.
     */
    private List<String> tamanosParaElDesplegable(DigestionTaller digestion) {
        List<String> tamanos = new ArrayList<>(catalogoTaras.tamanosDeMayorAMenor());
        for (GrupoReferencia grupo : digestion.getGrupos()) {
            if (grupo.getMedidaCaja() != null && !tamanos.contains(grupo.getMedidaCaja())) {
                tamanos.add(grupo.getMedidaCaja());
            }
        }
        return tamanos;
    }

    /**
     * @param nombreTaller el nombre del excel que se acaba de subir o de
     *                     convertir; null si es el que ya estaba en sesión.
     */
    private void guardarFicheros(byte[] excelTaller, String nombreTaller, byte[] excelPedido,
                                 String nombrePedido) {
        if (nombreTaller != null) {
            tallerEnCurso.setExcelTaller(excelTaller, nombreTaller);
        } else if (tallerEnCurso.getExcelTaller() == null) {
            tallerEnCurso.setExcelTaller(excelTaller, "packing del taller");
        }
        // El nombre solo llega cuando el pedido es nuevo —subido o sacado de
        // una temporada guardada—; si viene de la sesión ya estaba puesto.
        if (excelPedido != null && nombrePedido != null) {
            tallerEnCurso.setExcelPedido(excelPedido, nombrePedido);
        }
    }

    private String volverALaEntrada(EnvioForm envioForm, Model model, String error) {
        return volverALaEntrada(envioForm, model, error, List.of());
    }

    private String volverALaEntrada(EnvioForm envioForm, Model model, String error,
                                    List<String> hojas) {
        envioForm.setModo("TALLER");
        if (error != null) {
            model.addAttribute("errorJson", error);
        }
        if (!hojas.isEmpty()) {
            model.addAttribute("hojasDelTaller", hojas);
        }
        // Al volver con un error (una fecha que falta, por ejemplo) la
        // conversión ya hecha sigue valiendo: se enseña su descarga y digerir
        // sigue encendido, en vez de obligar a esperar otra vez a Claude.
        if (envioForm.esFormatoTaller() && tallerEnCurso.getExcelConvertido() != null) {
            model.addAttribute("excelConvertido", tallerEnCurso.getNombreExcelConvertido());
        }
        anadirAtributosDeClientes(model);
        return "entrada";
    }

    private String sinEntrega(RedirectAttributes redirect) {
        redirect.addFlashAttribute("mensaje",
                "La sesión ha caducado: vuelve a subir el packing list del taller.");
        return "redirect:/packing-list";
    }

    /** Los mismos atributos que necesita la pantalla de entrada. */
    private void anadirAtributosDeClientes(Model model) {
        atributosEntrada.anadir(model);
    }

    private static DatosEnvio cabeceraDe(EnvioForm envioForm) {
        DatosEnvio cabecera = new DatosEnvio();
        cabecera.setTemporada(envioForm.getTemporada());
        cabecera.setNumeroFactura(envioForm.getNumeroFactura());
        cabecera.setFechaFactura(envioForm.getFechaFactura());
        cabecera.setFechaEnvio(envioForm.getFechaEnvio());
        cabecera.setClaveCliente(envioForm.getCliente());
        cabecera.setNumeroComanda(envioForm.getNumeroComanda());
        cabecera.setCiudadProveedor(envioForm.getCiudadProveedor());
        cabecera.setPaisProveedor(envioForm.getPaisProveedor());
        return cabecera;
    }

    private static byte[] bytesDe(MultipartFile fichero) {
        if (fichero == null || fichero.isEmpty()) {
            return null;
        }
        try {
            return fichero.getBytes();
        } catch (IOException e) {
            return null;
        }
    }
}
