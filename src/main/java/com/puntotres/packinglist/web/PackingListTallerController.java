package com.puntotres.packinglist.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.config.CatalogoTaras;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.service.taller.DigestionTaller;
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

    private final DigestionTallerService digestionService;
    private final GeneradorPackingTaller generadorTaller;
    private final PreparacionRevisionService preparacion;
    private final ClientesProperties clientesProperties;
    private final CatalogoTaras catalogoTaras;
    private final TallerEnCurso tallerEnCurso;
    private final EnvioEnCurso envioEnCurso;

    public PackingListTallerController(DigestionTallerService digestionService,
                                       GeneradorPackingTaller generadorTaller,
                                       PreparacionRevisionService preparacion,
                                       ClientesProperties clientesProperties,
                                       CatalogoTaras catalogoTaras,
                                       TallerEnCurso tallerEnCurso,
                                       EnvioEnCurso envioEnCurso) {
        this.digestionService = digestionService;
        this.generadorTaller = generadorTaller;
        this.preparacion = preparacion;
        this.clientesProperties = clientesProperties;
        this.catalogoTaras = catalogoTaras;
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

        // El excel del taller solo hace falta la primera vez: al elegir hoja a
        // mano se reenvía el formulario y el fichero ya está en sesión.
        byte[] excelTaller = bytesDe(envioForm.getExcelTaller());
        if (excelTaller == null) {
            excelTaller = tallerEnCurso.getExcelTaller();
        }
        if (excelTaller == null) {
            errores.rejectValue("excelTaller", "excelTaller.obligatorio",
                    "Sube el packing list que ha mandado el taller");
        }
        if (errores.hasErrors()) {
            return volverALaEntrada(envioForm, model, null);
        }

        byte[] excelPedido = bytesDe(envioForm.getPedidoCliente());
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
            guardarFicheros(envioForm, excelTaller, excelPedido);
            return volverALaEntrada(envioForm, model, e.getMessage(), e.hojasEncontradas());
        } catch (TallerColisExcel.TallerExcelException | IOException e) {
            return volverALaEntrada(envioForm, model, e.getMessage());
        }

        guardarFicheros(envioForm, excelTaller, excelPedido);
        tallerEnCurso.setClaveCliente(envioForm.getCliente());
        tallerEnCurso.setCabecera(cabeceraDe(envioForm));
        tallerEnCurso.setAlturaMaximaPaletCm(envioForm.getAlturaMaximaPaletCm());
        tallerEnCurso.setDigestion(digestion);

        return pintarAjuste(model, null);
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
        model.addAttribute("tamanosCaja", tamanosParaElDesplegable(digestion));
        model.addAttribute("avisos", digestion.getAvisos());

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

    private void guardarFicheros(EnvioForm envioForm, byte[] excelTaller, byte[] excelPedido) {
        if (envioForm.getExcelTaller() != null && !envioForm.getExcelTaller().isEmpty()) {
            tallerEnCurso.setExcelTaller(excelTaller,
                    envioForm.getExcelTaller().getOriginalFilename());
        } else if (tallerEnCurso.getExcelTaller() == null) {
            tallerEnCurso.setExcelTaller(excelTaller, "packing del taller");
        }
        if (excelPedido != null && envioForm.getPedidoCliente() != null
                && !envioForm.getPedidoCliente().isEmpty()) {
            tallerEnCurso.setExcelPedido(excelPedido,
                    envioForm.getPedidoCliente().getOriginalFilename());
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
        model.addAttribute("clientes", clientesProperties.getClientes());
        model.addAttribute("tamanosCaja", catalogoTaras.tamanosDeMayorAMenor());
        Map<String, Map<String, String>> clientesJs = new LinkedHashMap<>();
        clientesProperties.getClientes().forEach((clave, config) -> {
            Map<String, String> datos = new LinkedHashMap<>();
            datos.put("plantilla", config.getPlantilla().name());
            datos.put("placeholderTemporada",
                    config.getPlaceholderTemporada() != null ? config.getPlaceholderTemporada() : "");
            datos.put("pedidoCliente", String.valueOf(config.isPedidoCliente()));
            clientesJs.put(clave, datos);
        });
        model.addAttribute("clientesJs", clientesJs);
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
