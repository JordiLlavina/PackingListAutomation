package com.puntotres.packinglist.service.cargamodelos;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.modelos.api.dto.CandidataImagen;
import com.puntotres.packinglist.modelos.service.ImagenesDePdf;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.Articulo;
import com.puntotres.packinglist.persistence.ArticuloColor;
import com.puntotres.packinglist.persistence.ColorCliente;
import com.puntotres.packinglist.persistence.Etapa;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloRepository;
import com.puntotres.packinglist.persistence.Piel;
import com.puntotres.packinglist.persistence.PielId;
import com.puntotres.packinglist.persistence.PielRepository;
import com.puntotres.packinglist.persistence.ResumenFoto;
import com.puntotres.packinglist.persistence.ResumenTemporada;
import com.puntotres.packinglist.persistence.TablaColores;
import com.puntotres.packinglist.persistence.TipoArticulo;
import com.puntotres.packinglist.service.cargamodelos.CarpetaModelos.FicheroModelo;
import com.puntotres.packinglist.service.cargamodelos.EdicionModelo.EdicionColor;
import com.puntotres.packinglist.service.cargamodelos.EdicionModelo.EdicionFoto;
import com.puntotres.packinglist.service.cargamodelos.EdicionModelo.EdicionPiel;
import com.puntotres.packinglist.service.cargamodelos.LecturaOrigen.ArticuloLeido;
import com.puntotres.packinglist.service.cargamodelos.LecturaOrigen.ColorLeido;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.ColorEnCarga;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.EstadoModelo;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.FichaEnCarga;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.FotoEnCarga;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.PielEnCarga;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;
import com.puntotres.packinglist.service.corte.ClienteCorte;
import com.puntotres.packinglist.service.corte.ConversionFotos;
import com.puntotres.packinglist.service.corte.ConversorFotos;
import com.puntotres.packinglist.service.corte.FotoModelo;
import com.puntotres.packinglist.service.corte.Imagen;
import com.puntotres.packinglist.service.corte.LectorFotos;
import com.puntotres.packinglist.service.corte.ReductorImagen;
import com.puntotres.packinglist.service.corte.ReferenciaCorte;

/**
 * Monta el lote de una carga de modelos y aplica lo que el usuario hace en la
 * batería. Solo LEE el catálogo —para saber qué modelos existen ya y qué
 * dicen— y no escribe nada en él: eso es de {@link CargadorModelos}.
 *
 * La regla que gobierna un modelo que ya existe (casar): lo que el catálogo
 * tiene guardado MANDA sobre lo que trae el origen, que solo rellena huecos;
 * y lo que una persona cambia en el panel manda sobre las dos cosas, porque
 * «todos los campos son editables». Por eso cada cambio del panel se apunta
 * en {@code tocados}.
 */
@Service
public class ConstructorLote {

    /** Los nombres con los que una foto de la carpeta pasa a ir la primera, de más a menos prioridad. */
    private static final List<String> NOMBRES_PRINCIPAL = List.of("principal", "main", "0");
    private static final int LARGO_REFERENCIA = 60;

    private final CatalogoModelos catalogo;
    private final FotosCatalogo fotosCatalogo;
    private final ModeloRepository modelos;
    private final PielRepository pieles;
    private final TablaColores tablaColores;
    private final ArchivoTemporadas temporadas;
    private final ClientesProperties clientes;
    private final LectorOrigenes lectores;
    private final ConversorFotos conversor;
    private final ImagenesDePdf imagenesDePdf;

    public ConstructorLote(CatalogoModelos catalogo, FotosCatalogo fotosCatalogo, ModeloRepository modelos,
                           PielRepository pieles, TablaColores tablaColores, ArchivoTemporadas temporadas,
                           ClientesProperties clientes, LectorOrigenes lectores, ConversorFotos conversor,
                           ImagenesDePdf imagenesDePdf) {
        this.catalogo = catalogo;
        this.fotosCatalogo = fotosCatalogo;
        this.modelos = modelos;
        this.pieles = pieles;
        this.tablaColores = tablaColores;
        this.temporadas = temporadas;
        this.clientes = clientes;
        this.lectores = lectores;
        this.conversor = conversor;
        this.imagenesDePdf = imagenesDePdf;
    }

    // --- Montar el lote ---

    /**
     * El lote de un origen con excel: un modelo por cada código del excel,
     * con sus pieles y colores, y las fotos y fichas de las subcarpetas que
     * casan con alguno. Una subcarpeta que no es de ningún modelo del excel
     * se avisa y no crea modelo: para eso está «Añadir en grupo».
     */
    public LoteCarga deExcel(OrigenCarga origen, String cliente, Long temporadaId, LecturaOrigen lectura,
                             CarpetaModelos.Lectura carpeta, Path directorio) throws IOException {
        LoteCarga lote = new LoteCarga(origen, cliente, temporadaId, directorio);
        lote.avisos.addAll(lectura.avisos());
        Map<String, ModeloEnCarga> porCodigo = new LinkedHashMap<>();
        for (ArticuloLeido articulo : lectura.articulos()) {
            ModeloEnCarga modelo = porCodigo.computeIfAbsent(articulo.modelo(),
                    codigo -> nuevoModelo(lote, cliente, codigo, origen.etapaDePartida()));
            if (modelo.nombre.isEmpty()) {
                modelo.nombre = ModeloEnCarga.limpio(articulo.nombreModelo());
            }
            anadirPiel(modelo, articulo);
        }
        for (Map.Entry<String, List<Imagen>> delModelo : lectura.croquis().entrySet()) {
            ModeloEnCarga modelo = porCodigo.get(delModelo.getKey());
            if (modelo != null) {
                anadirCroquis(lote, modelo, delModelo.getValue());
            }
        }
        repartirCarpeta(lote, carpeta, porCodigo, false);
        lote.modelos.forEach(this::casar);
        return lote;
    }

    /**
     * «Añadir en grupo»: los modelos salen de la propia carpeta, uno por
     * subcarpeta, y la carpeta raíz es el nombre de la temporada. Si coincide
     * con una temporada guardada del cliente, los modelos se vinculan a ella;
     * si no, se avisa y quedan sin temporada (aquí no se crea ninguna: una
     * temporada necesita su excel).
     */
    public LoteCarga deGrupo(String cliente, CarpetaModelos.Lectura carpeta, Path directorio) throws IOException {
        Long temporadaId = null;
        List<String> avisos = new ArrayList<>();
        if (!carpeta.raiz().isBlank()) {
            String raiz = ModeloEnCarga.limpio(carpeta.raiz());
            Optional<ResumenTemporada> guardada = temporadas.de(cliente).stream()
                    .filter(temporada -> temporada.temporada().trim().equalsIgnoreCase(raiz))
                    .findFirst();
            if (guardada.isPresent()) {
                temporadaId = guardada.get().id();
            } else {
                avisos.add("La carpeta se llama «" + raiz + "», pero no hay ninguna temporada guardada de "
                        + nombreDe(cliente) + " con ese nombre: los modelos quedan sin temporada. Guárdala en "
                        + "Temporadas y ficheros, o elige otra en cada modelo.");
            }
        }
        LoteCarga lote = new LoteCarga(OrigenCarga.GRUPO, cliente, temporadaId, directorio);
        lote.avisos.addAll(avisos);
        repartirCarpeta(lote, carpeta, new LinkedHashMap<>(), true);
        lote.modelos.forEach(this::casar);
        return lote;
    }

    /** «Añadir modelo»: un lote con un solo modelo en blanco. */
    public LoteCarga manual(String cliente, Path directorio) {
        LoteCarga lote = new LoteCarga(OrigenCarga.MANUAL, cliente, null, directorio);
        anadirModelo(lote, cliente);
        return lote;
    }

    /** «Añadir otro»: un modelo en blanco más, con ese cliente. */
    public ModeloEnCarga anadirModelo(LoteCarga lote, String cliente) {
        String elCliente = clientes.clientePara(cliente).isPresent() ? cliente : lote.cliente;
        ModeloEnCarga modelo = nuevoModelo(lote, elCliente, "", lote.origen.etapaDePartida());
        modelo.identidadLibre = true;
        return modelo;
    }

    private ModeloEnCarga nuevoModelo(LoteCarga lote, String cliente, String codigo, Etapa etapa) {
        ModeloEnCarga modelo = new ModeloEnCarga(lote.siguiente(), cliente);
        modelo.codigo = ReferenciaCorte.normalizar(codigo);
        modelo.temporadaId = lote.temporadaId;
        modelo.etapa = etapa;
        modelo.tipo = modelo.codigo.isEmpty() ? TipoArticulo.BOLSO : TipoArticulo.porReferencia(modelo.codigo);
        lote.modelos.add(modelo);
        return modelo;
    }

    private static void anadirPiel(ModeloEnCarga modelo, ArticuloLeido articulo) {
        PielEnCarga piel = modelo.piel(articulo.referencia()).orElseGet(() -> {
            PielEnCarga nueva = new PielEnCarga(articulo.referencia(), articulo.refPiel());
            modelo.pieles.add(nueva);
            return nueva;
        });
        piel.nombrePiel = ModeloEnCarga.mayusculas(articulo.nombrePiel());
        piel.forro = ModeloEnCarga.mayusculas(articulo.forro());
        for (int i = 0; i < articulo.combinaciones().size() && i < piel.combinaciones.size(); i++) {
            piel.combinaciones.set(i, ModeloEnCarga.mayusculas(articulo.combinaciones().get(i)));
        }
        for (ColorLeido leido : articulo.colores()) {
            ColorEnCarga color = new ColorEnCarga(leido.codigo(), leido.descripcion());
            if (color.clave.isEmpty() || piel.color(color.clave).isPresent()) {
                continue;
            }
            color.colorPiel = ModeloEnCarga.mayusculas(leido.colorPiel());
            color.colorForro = ModeloEnCarga.mayusculas(leido.colorForro());
            for (int i = 0; i < leido.coloresCombinacion().size() && i < color.coloresCombinacion.size(); i++) {
                color.coloresCombinacion.set(i, ModeloEnCarga.mayusculas(leido.coloresCombinacion().get(i)));
            }
            piel.colores.add(color);
        }
    }

    private static void anadirCroquis(LoteCarga lote, ModeloEnCarga modelo, List<Imagen> croquis)
            throws IOException {
        Path carpeta = Files.createDirectories(lote.directorio.resolve("croquis"));
        int numero = 0;
        for (Imagen imagen : croquis) {
            int secuencia = lote.siguiente();
            Path fichero = carpeta.resolve(String.format("%05d.jpg", secuencia));
            Files.write(fichero, imagen.jpeg());
            String nombre = ++numero == 1 ? "croquis.jpg" : "croquis " + numero + ".jpg";
            modelo.fotos.add(FotoEnCarga.directa("f" + secuencia, nombre, FotoEnCarga.Origen.CROQUIS, fichero, true));
        }
    }

    /**
     * Reparte las subcarpetas entre los modelos. La que nombra el modelo es
     * la primera palabra de la carpeta, partida con la regla del cliente
     * ("ULL027.AL103 candy" es el modelo ULL027), como en las fotos del
     * corte; el resto del nombre es, en «Añadir en grupo», el nombre del
     * bolso de partida.
     */
    private void repartirCarpeta(LoteCarga lote, CarpetaModelos.Lectura carpeta,
                                 Map<String, ModeloEnCarga> porCodigo, boolean crear) throws IOException {
        lote.avisos.addAll(carpeta.avisos());
        ClienteCorte reglas = lectores.reglasDe(lote.cliente);
        List<FotoModelo> paraConvertir = new ArrayList<>();
        Map<FotoModelo, ModeloEnCarga> duenos = new LinkedHashMap<>();
        for (Map.Entry<String, List<FicheroModelo>> subcarpeta : carpeta.porCarpeta().entrySet()) {
            String nombreCarpeta = ModeloEnCarga.limpio(subcarpeta.getKey());
            int espacio = nombreCarpeta.indexOf(' ');
            String primera = ReferenciaCorte.normalizar(espacio < 0 ? nombreCarpeta : nombreCarpeta.substring(0, espacio));
            String resto = espacio < 0 ? "" : nombreCarpeta.substring(espacio + 1).trim();
            ReferenciaCorte partida = reglas.partir(primera);
            String codigo = partida.tienePiel() ? partida.modelo() : primera;
            ModeloEnCarga modelo = porCodigo.get(codigo);
            if (modelo == null && crear) {
                modelo = nuevoModelo(lote, lote.cliente, codigo, lote.origen.etapaDePartida());
                modelo.nombre = resto;
                porCodigo.put(codigo, modelo);
            }
            if (modelo == null) {
                int cuantos = subcarpeta.getValue().size();
                lote.avisos.add("La carpeta «" + nombreCarpeta + "» no es ningún modelo del excel: "
                        + (cuantos == 1 ? "su fichero no se usa" : "sus " + cuantos + " ficheros no se usan"));
                continue;
            }
            if (crear && partida.tienePiel() && modelo.piel(partida.referencia()).isEmpty()) {
                modelo.pieles.add(new PielEnCarga(partida.referencia(), partida.piel()));
            }
            List<FicheroModelo> fotos = new ArrayList<>();
            for (FicheroModelo fichero : subcarpeta.getValue()) {
                if (fichero.pdf()) {
                    modelo.fichas.add(new FichaEnCarga("d" + lote.siguiente(), fichero.nombre(), fichero.copia()));
                } else {
                    fotos.add(fichero);
                }
            }
            for (FicheroModelo fichero : principalDelante(fotos)) {
                FotoModelo foto = new FotoModelo(codigo, fichero.nombre(), fichero.copia());
                paraConvertir.add(foto);
                duenos.put(foto, modelo);
            }
        }
        if (paraConvertir.isEmpty()) {
            return;
        }
        ConversionFotos conversion = conversor.convertir(paraConvertir, lote.directorio);
        lote.conversiones.add(conversion);
        for (FotoModelo foto : paraConvertir) {
            duenos.get(foto).fotos.add(FotoEnCarga.subida("f" + lote.siguiente(), foto, conversion));
        }
    }

    /** Por nombre, con la que se llame principal, main o 0 delante: será la principal si nadie la mueve. */
    private static List<FicheroModelo> principalDelante(List<FicheroModelo> fotos) {
        for (String preferido : NOMBRES_PRINCIPAL) {
            for (FicheroModelo foto : fotos) {
                String nombre = foto.nombre();
                int punto = nombre.lastIndexOf('.');
                if ((punto < 0 ? nombre : nombre.substring(0, punto)).trim().equalsIgnoreCase(preferido)) {
                    List<FicheroModelo> ordenadas = new ArrayList<>(fotos);
                    ordenadas.remove(foto);
                    ordenadas.add(0, foto);
                    return ordenadas;
                }
            }
        }
        return fotos;
    }

    // --- Casar un modelo con el catálogo ---

    /**
     * Mira si el modelo ya existe en el catálogo y, si existe, mete en el
     * panel lo que tiene guardado: lo guardado manda sobre lo leído del
     * origen (que solo rellena huecos), salvo en los campos que el usuario ya
     * ha tocado. Sus fotos guardadas pasan a la tira, delante. Si no existe,
     * deshace lo que una llamada anterior hubiera traído (el usuario ha
     * cambiado la referencia de un modelo nuevo).
     */
    void casar(ModeloEnCarga modelo) {
        Optional<Modelo> guardado = buscar(modelo);
        if (guardado.isEmpty()) {
            desligar(modelo);
            asegurarArticuloUnico(modelo);
            return;
        }
        Modelo delCatalogo = guardado.get();
        modelo.modeloId = delCatalogo.getId();
        modelo.huecosRellenados = 0;
        if (delCatalogo.getCodigo() != null) {
            modelo.codigo = delCatalogo.getCodigo();
        }
        if (delCatalogo.getNombrePrototipo() != null && modelo.nombrePrototipo.isEmpty()) {
            modelo.nombrePrototipo = delCatalogo.getNombrePrototipo();
        }
        modelo.nombre = fundir(modelo, "nombre", modelo.nombre, delCatalogo.getNombre());
        if (!modelo.tocado("tipo")) {
            modelo.tipo = delCatalogo.getTipo();
        }
        if (!modelo.tocado("etapa")) {
            modelo.etapa = delCatalogo.getEtapa();
        }
        modelo.yaVinculado = modelo.temporadaId == null
                || catalogo.temporadasDeModelo(delCatalogo.getId()).contains(modelo.temporadaId);
        Map<String, String> descripciones = new LinkedHashMap<>();
        for (ColorCliente color : tablaColores.de(modelo.cliente)) {
            descripciones.put(color.getCodigo(), color.getDescripcion());
        }
        for (Articulo articulo : catalogo.articulosDelModelo(delCatalogo.getId())) {
            casarPiel(modelo, articulo, descripciones);
        }
        // Las pieles que no están en el catálogo siguen siendo nuevas, pero su
        // referencia de piel puede tener ya nombre (la usa otro modelo).
        for (PielEnCarga piel : modelo.pieles) {
            if (!piel.existe) {
                casarNombreDePiel(modelo, piel);
            }
        }
        casarFotos(modelo);
    }

    private void casarPiel(ModeloEnCarga modelo, Articulo articulo, Map<String, String> descripciones) {
        PielEnCarga piel = modelo.piel(articulo.getReferencia()).orElseGet(() -> {
            PielEnCarga nueva = new PielEnCarga(articulo.getReferencia(), articulo.getRefPiel());
            modelo.pieles.add(nueva);
            return nueva;
        });
        piel.existe = true;
        piel.forro = fundir(modelo, piel.claveCampo("forro"), piel.forro, articulo.getForro());
        List<String> guardadas = articulo.getCasillasCombinacion();
        for (int i = 0; i < piel.combinaciones.size(); i++) {
            piel.combinaciones.set(i, fundir(modelo, piel.claveCampo("combinacion" + i),
                    piel.combinaciones.get(i), i < guardadas.size() ? guardadas.get(i) : ""));
        }
        piel.medidaCaja = fundir(modelo, piel.claveCampo("medidaCaja"), piel.medidaCaja, articulo.getMedidaCaja());
        piel.unidadesPorCaja = fundir(modelo, piel.claveCampo("unidadesPorCaja"), piel.unidadesPorCaja,
                articulo.getUnidadesPorCaja() == null ? "" : String.valueOf(articulo.getUnidadesPorCaja()));
        piel.pesoNetoKg = fundir(modelo, piel.claveCampo("pesoNetoKg"), piel.pesoNetoKg,
                articulo.getPesoNetoKg() == null ? "" : String.valueOf(articulo.getPesoNetoKg()));
        casarNombreDePiel(modelo, piel);
        for (ArticuloColor guardado : catalogo.coloresDe(modelo.cliente, articulo.getReferencia())) {
            String clave = guardado.getCodigoColor();
            ColorEnCarga color = piel.color(clave).orElseGet(() -> {
                // En la tabla de colores solo están los códigos: lo que no está es una descripción entera.
                boolean esCodigo = descripciones.containsKey(clave);
                ColorEnCarga nuevo = new ColorEnCarga(esCodigo ? clave : "", esCodigo ? "" : clave);
                piel.colores.add(nuevo);
                return nuevo;
            });
            color.existe = true;
            if (!color.codigo.isEmpty()) {
                color.descripcion = fundir(modelo, color.claveCampo(piel, "descripcion"), color.descripcion,
                        descripciones.getOrDefault(color.codigo, ""));
            }
            color.colorPiel = fundir(modelo, color.claveCampo(piel, "colorPiel"), color.colorPiel,
                    guardado.getColorPiel());
            color.colorForro = fundir(modelo, color.claveCampo(piel, "colorForro"), color.colorForro,
                    guardado.getColorForro());
            List<String> deCombinacion = guardado.getColoresCombinacion();
            for (int i = 0; i < color.coloresCombinacion.size(); i++) {
                color.coloresCombinacion.set(i, fundir(modelo, color.claveCampo(piel, "colorCombinacion" + i),
                        color.coloresCombinacion.get(i), i < deCombinacion.size() ? deCombinacion.get(i) : ""));
            }
        }
    }

    private void casarNombreDePiel(ModeloEnCarga modelo, PielEnCarga piel) {
        if (piel.refPiel.isEmpty()) {
            return;
        }
        String guardado = pieles.findById(new PielId(modelo.cliente, piel.refPiel)).map(Piel::getNombre).orElse("");
        piel.nombrePiel = fundir(modelo, piel.claveCampo("nombrePiel"), piel.nombrePiel, guardado);
    }

    private void casarFotos(ModeloEnCarga modelo) {
        Set<Long> yaEnLaTira = new HashSet<>();
        modelo.fotos.stream().filter(foto -> foto.fotoCatalogoId != null)
                .forEach(foto -> yaEnLaTira.add(foto.fotoCatalogoId));
        List<FotoEnCarga> guardadas = new ArrayList<>();
        for (ResumenFoto foto : fotosCatalogo.de(modelo.modeloId)) {
            if (!yaEnLaTira.contains(foto.id())) {
                guardadas.add(FotoEnCarga.guardada("g" + foto.id(), foto.nombreFichero(), foto.id()));
            }
        }
        // Delante: sin tocar el orden, las nuevas van detrás de las que ya tenía.
        modelo.fotos.addAll(0, guardadas);
    }

    /**
     * Lo guardado manda si lo hay y el campo no se ha tocado; si no hay nada
     * guardado y el origen trae algo, es un hueco que se rellena (cuenta como
     * novedad del modelo).
     */
    private static String fundir(ModeloEnCarga modelo, String campo, String leido, String guardado) {
        String elGuardado = guardado == null ? "" : guardado;
        if (!elGuardado.isEmpty() && !modelo.tocado(campo)) {
            return elGuardado;
        }
        if (elGuardado.isEmpty() && !leido.isEmpty() && !modelo.tocado(campo)) {
            modelo.huecosRellenados++;
        }
        return leido;
    }

    private Optional<Modelo> buscar(ModeloEnCarga modelo) {
        if (!modelo.codigo.isEmpty()) {
            return modelos.findByClienteAndCodigo(modelo.cliente, modelo.codigo);
        }
        if (!modelo.nombrePrototipo.isEmpty() && modelo.temporadaId != null) {
            return modelos.findByClienteAndNombrePrototipoAndTemporadaAltaId(modelo.cliente, modelo.nombrePrototipo,
                    modelo.temporadaId);
        }
        return Optional.empty();
    }

    private static void desligar(ModeloEnCarga modelo) {
        if (modelo.modeloId != null) {
            // Estaba casado con un modelo del catálogo y ya no: lo que se trajo de él se va con él.
            modelo.pieles.removeIf(piel -> piel.existe);
            if (!modelo.tocado("nombre")) {
                modelo.nombre = "";
            }
        }
        modelo.modeloId = null;
        modelo.yaVinculado = false;
        modelo.huecosRellenados = 0;
        modelo.fotos.removeIf(foto -> foto.origen == FotoEnCarga.Origen.GUARDADA);
        for (PielEnCarga piel : modelo.pieles) {
            piel.existe = false;
            piel.colores.forEach(color -> color.existe = false);
        }
    }

    /**
     * En un cliente cuya referencia no se parte (un genérico), el modelo ES su
     * artículo: se le da uno con su misma referencia para que tenga dónde
     * poner el embalaje y los colores. Si un modelo nuevo cambia de
     * referencia, su artículo la sigue.
     */
    private void asegurarArticuloUnico(ModeloEnCarga modelo) {
        if (parteReferencias(modelo.cliente) || modelo.existe()) {
            return;
        }
        if (modelo.codigo.isEmpty()) {
            modelo.pieles.clear();
            return;
        }
        if (modelo.pieles.size() == 1 && modelo.pieles.get(0).referencia.equals(modelo.codigo)) {
            return;
        }
        PielEnCarga nueva = new PielEnCarga(modelo.codigo, "");
        if (!modelo.pieles.isEmpty()) {
            PielEnCarga vieja = modelo.pieles.get(0);
            nueva.forro = vieja.forro;
            for (int i = 0; i < nueva.combinaciones.size(); i++) {
                nueva.combinaciones.set(i, vieja.combinaciones.get(i));
            }
            nueva.medidaCaja = vieja.medidaCaja;
            nueva.unidadesPorCaja = vieja.unidadesPorCaja;
            nueva.pesoNetoKg = vieja.pesoNetoKg;
            nueva.colores.addAll(vieja.colores);
        }
        modelo.pieles.clear();
        modelo.pieles.add(nueva);
    }

    /** ¿Lleva la referencia de ese cliente la piel dentro (AMI, APC)? Un genérico no la parte. */
    public boolean parteReferencias(String cliente) {
        return lectores.admite(OrigenCarga.TEMPORADA, cliente);
    }

    // --- Lo que el usuario hace en el panel ---

    /**
     * Aplica el panel entero tal como llega. Cada campo que cambia se apunta
     * como tocado. Un modelo aceptado que deja de estar completo vuelve a
     * pendiente: no puede quedarse en verde algo que ya no se puede cargar.
     */
    public void aplicar(LoteCarga lote, ModeloEnCarga modelo, EdicionModelo edicion) {
        boolean cambiaIdentidad = false;
        if (!modelo.existe() || modelo.identidadLibre) {
            // La identidad de un modelo del catálogo no se cambia aquí: renombrar o juntar es de la app de
            // modelos. Uno escrito a mano sí se corrige, case o no con uno que existe.
            if (lote.origen == OrigenCarga.MANUAL && edicion.cliente() != null
                    && clientes.clientePara(edicion.cliente()).isPresent()
                    && !edicion.cliente().equals(modelo.cliente)) {
                modelo.cliente = edicion.cliente();
                modelo.temporadaId = null;
                cambiaIdentidad = true;
            }
            String codigo = recortado(ReferenciaCorte.normalizar(edicion.codigo()), LARGO_REFERENCIA);
            if (edicion.codigo() != null && !codigo.equals(modelo.codigo)) {
                modelo.codigo = codigo;
                modelo.tocados.add("codigo");
                cambiaIdentidad = true;
                if (!modelo.tocado("tipo") && !codigo.isEmpty()) {
                    modelo.tipo = TipoArticulo.porReferencia(codigo);
                }
            }
            String prototipo = recortado(ReferenciaCorte.normalizar(edicion.nombrePrototipo()), LARGO_REFERENCIA);
            if (edicion.nombrePrototipo() != null && !prototipo.equals(modelo.nombrePrototipo)) {
                modelo.nombrePrototipo = prototipo;
                cambiaIdentidad = true;
            }
        }
        Long temporada = edicion.temporadaId() != null && temporadas.de(modelo.cliente).stream()
                .anyMatch(guardada -> guardada.id().equals(edicion.temporadaId())) ? edicion.temporadaId() : null;
        if (!Objects.equals(temporada, modelo.temporadaId)) {
                modelo.temporadaId = temporada;
                modelo.tocados.add("temporadaId");
                cambiaIdentidad = cambiaIdentidad || modelo.codigo.isEmpty();
                if (modelo.existe()) {
                    modelo.yaVinculado = temporada == null
                            || catalogo.temporadasDeModelo(modelo.modeloId).contains(temporada);
                }
        }
        if (edicion.nombre() != null) {
            String nombre = ModeloEnCarga.limpio(edicion.nombre());
            if (!nombre.equals(modelo.nombre)) {
                modelo.nombre = nombre;
                modelo.tocados.add("nombre");
            }
        }
        TipoArticulo tipo = enumerado(TipoArticulo.class, edicion.tipo());
        if (tipo != null && tipo != modelo.tipo) {
            modelo.tipo = tipo;
            modelo.tocados.add("tipo");
        }
        Etapa etapa = enumerado(Etapa.class, edicion.etapa());
        if (etapa != null && etapa != modelo.etapa) {
            modelo.etapa = etapa;
            modelo.tocados.add("etapa");
        }
        if (edicion.pieles() != null) {
            aplicarPieles(modelo, edicion.pieles());
        }
        if (edicion.fotos() != null) {
            aplicarFotos(modelo, edicion.fotos());
        }
        if (cambiaIdentidad) {
            casar(modelo);
        }
        if (modelo.estado == EstadoModelo.ACEPTADO && !faltan(lote, modelo).isEmpty()) {
            modelo.estado = EstadoModelo.PENDIENTE;
        }
    }

    private void aplicarPieles(ModeloEnCarga modelo, List<EdicionPiel> editadas) {
        Set<String> vistas = new HashSet<>();
        for (EdicionPiel editada : editadas) {
            String referencia = recortado(ReferenciaCorte.normalizar(editada.referencia()), LARGO_REFERENCIA);
            if (referencia.isEmpty() || !vistas.add(referencia)) {
                continue;
            }
            PielEnCarga piel = modelo.piel(referencia).orElseGet(() -> {
                ReferenciaCorte partida = lectores.reglasDe(modelo.cliente).partir(referencia);
                PielEnCarga nueva = new PielEnCarga(referencia, partida.tienePiel() ? partida.piel() : "");
                modelo.pieles.add(nueva);
                // Una referencia de piel que ya usa otro modelo trae su nombre.
                casarNombreDePiel(modelo, nueva);
                return nueva;
            });
            piel.nombrePiel = cambiar(modelo, piel.claveCampo("nombrePiel"), piel.nombrePiel,
                    mayusculasONull(editada.nombrePiel()));
            piel.forro = cambiar(modelo, piel.claveCampo("forro"), piel.forro, mayusculasONull(editada.forro()));
            if (editada.combinaciones() != null) {
                for (int i = 0; i < piel.combinaciones.size(); i++) {
                    String valor = i < editada.combinaciones().size()
                            ? ModeloEnCarga.mayusculas(editada.combinaciones().get(i)) : "";
                    piel.combinaciones.set(i, cambiar(modelo, piel.claveCampo("combinacion" + i),
                            piel.combinaciones.get(i), valor));
                }
            }
            piel.medidaCaja = cambiar(modelo, piel.claveCampo("medidaCaja"), piel.medidaCaja,
                    editada.medidaCaja() == null ? null
                            : ModeloEnCarga.limpio(editada.medidaCaja()).toLowerCase(Locale.ROOT));
            piel.unidadesPorCaja = cambiar(modelo, piel.claveCampo("unidadesPorCaja"), piel.unidadesPorCaja,
                    editada.unidadesPorCaja() == null ? null : ModeloEnCarga.limpio(editada.unidadesPorCaja()));
            piel.pesoNetoKg = cambiar(modelo, piel.claveCampo("pesoNetoKg"), piel.pesoNetoKg,
                    editada.pesoNetoKg() == null ? null
                            : ModeloEnCarga.limpio(editada.pesoNetoKg()).replace(',', '.'));
            if (editada.colores() != null) {
                aplicarColores(modelo, piel, editada.colores());
            }
            compactar(piel);
        }
        // Una piel que ya no viene se ha quitado en el panel; las del catálogo no se quitan desde aquí.
        modelo.pieles.removeIf(piel -> !piel.existe && !vistas.contains(piel.referencia));
    }

    private static void aplicarColores(ModeloEnCarga modelo, PielEnCarga piel, List<EdicionColor> editados) {
        Set<String> vistos = new HashSet<>();
        for (EdicionColor editado : editados) {
            ColorEnCarga candidato = new ColorEnCarga(editado.codigo(), editado.descripcion());
            if (candidato.clave.isEmpty() || !vistos.add(candidato.clave)) {
                continue;
            }
            ColorEnCarga color = piel.color(candidato.clave).orElseGet(() -> {
                piel.colores.add(candidato);
                if (editado.colorPiel() == null) {
                    // La piel principal arranca con el color del bolso, como en el corte.
                    candidato.colorPiel = ModeloEnCarga.mayusculas(editado.descripcion());
                }
                return candidato;
            });
            if (!color.codigo.isEmpty()) {
                color.descripcion = cambiar(modelo, color.claveCampo(piel, "descripcion"), color.descripcion,
                        editado.descripcion() == null ? null : ModeloEnCarga.limpio(editado.descripcion()));
            }
            color.colorPiel = cambiar(modelo, color.claveCampo(piel, "colorPiel"), color.colorPiel,
                    mayusculasONull(editado.colorPiel()));
            color.colorForro = cambiar(modelo, color.claveCampo(piel, "colorForro"), color.colorForro,
                    mayusculasONull(editado.colorForro()));
            if (editado.coloresCombinacion() != null) {
                for (int i = 0; i < color.coloresCombinacion.size(); i++) {
                    String valor = i < editado.coloresCombinacion().size()
                            ? ModeloEnCarga.mayusculas(editado.coloresCombinacion().get(i)) : "";
                    color.coloresCombinacion.set(i, cambiar(modelo, color.claveCampo(piel, "colorCombinacion" + i),
                            color.coloresCombinacion.get(i), valor));
                }
            }
        }
        piel.colores.removeIf(color -> !color.existe && !vistos.contains(color.clave));
    }

    /**
     * Quita los huecos de las combinaciones de una piel y corre con ellas sus
     * colores, como hace el catálogo al guardar: el corte las empareja por
     * posición, y un hueco pondría a cada combinación el color de otra.
     */
    private static void compactar(PielEnCarga piel) {
        List<Integer> conNombre = new ArrayList<>();
        for (int i = 0; i < piel.combinaciones.size(); i++) {
            if (!piel.combinaciones.get(i).isEmpty()) {
                conNombre.add(i);
            }
        }
        boolean compactas = true;
        for (int i = 0; i < conNombre.size(); i++) {
            compactas = compactas && conNombre.get(i) == i;
        }
        if (compactas) {
            return;
        }
        recolocar(piel.combinaciones, conNombre);
        piel.colores.forEach(color -> recolocar(color.coloresCombinacion, conNombre));
    }

    private static void recolocar(List<String> casillas, List<Integer> posiciones) {
        List<String> nuevas = ModeloEnCarga.casillas();
        for (int i = 0; i < posiciones.size(); i++) {
            nuevas.set(i, casillas.get(posiciones.get(i)));
        }
        for (int i = 0; i < casillas.size(); i++) {
            casillas.set(i, nuevas.get(i));
        }
    }

    private static void aplicarFotos(ModeloEnCarga modelo, List<EdicionFoto> editadas) {
        List<FotoEnCarga> antes = new ArrayList<>(modelo.fotos);
        List<FotoEnCarga> ordenadas = new ArrayList<>();
        for (EdicionFoto editada : editadas) {
            modelo.foto(editada.id()).filter(foto -> !ordenadas.contains(foto)).ifPresent(foto -> {
                // Una foto ya guardada no se quita desde aquí: sale siempre seleccionada.
                foto.seleccionada = foto.origen == FotoEnCarga.Origen.GUARDADA || editada.seleccionada();
                ordenadas.add(foto);
            });
        }
        // Las que la pantalla aún no conocía (subidas mientras tanto) van detrás.
        antes.stream().filter(foto -> !ordenadas.contains(foto)).forEach(ordenadas::add);
        if (!ordenadas.equals(antes)) {
            modelo.ordenTocado = true;
        }
        modelo.fotos.clear();
        modelo.fotos.addAll(ordenadas);
    }

    /** {@code nuevo} nulo es «no tocar». Un valor distinto se apunta como tocado. */
    private static String cambiar(ModeloEnCarga modelo, String campo, String actual, String nuevo) {
        if (nuevo == null || nuevo.equals(actual)) {
            return actual;
        }
        modelo.tocados.add(campo);
        return nuevo;
    }

    // --- Fotos y fichas técnicas subidas desde el panel ---

    /**
     * Añade al modelo los ficheros subidos en su panel: las imágenes, como
     * fotos (seleccionadas) y los PDF, como fichas técnicas. Espera a que las
     * fotos estén convertidas: son pocas, y así el panel las enseña al volver.
     *
     * @return lo que no se ha podido usar, para decírselo al usuario
     */
    public List<String> anadirFicheros(LoteCarga lote, ModeloEnCarga modelo, List<LectorFotos.Fichero> ficheros)
            throws IOException {
        Path carpeta = Files.createDirectories(lote.directorio.resolve("subida-" + lote.siguiente()));
        List<String> avisos = new ArrayList<>();
        List<FotoModelo> fotos = new ArrayList<>();
        for (LectorFotos.Fichero fichero : ficheros) {
            String nombre = soloNombre(fichero.ruta());
            String extension = CarpetaModelos.extensionDe(nombre);
            boolean pdf = "pdf".equals(extension);
            if (!pdf && !Set.of("heic", "heif", "jpg", "jpeg", "png").contains(extension)) {
                avisos.add(nombre + " no es una foto (HEIC, JPG, PNG) ni un PDF");
                continue;
            }
            int secuencia = lote.siguiente();
            Path copia = carpeta.resolve(String.format("%05d.%s", secuencia, extension));
            try (InputStream entrada = fichero.contenido().abrir()) {
                Files.copy(entrada, copia);
            }
            if (pdf) {
                modelo.fichas.add(new FichaEnCarga("d" + secuencia, nombre, copia));
            } else {
                fotos.add(new FotoModelo(modelo.nombreVisible(), nombre, copia));
            }
        }
        if (!fotos.isEmpty()) {
            ConversionFotos conversion = conversor.convertir(fotos, carpeta);
            lote.conversiones.add(conversion);
            try {
                conversion.esperar();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            for (FotoModelo foto : fotos) {
                if (conversion.reducida(foto).isPresent()) {
                    modelo.fotos.add(FotoEnCarga.subida("f" + lote.siguiente(), foto, conversion));
                } else {
                    avisos.add("La foto " + foto.nombreOriginal() + " no se ha podido leer");
                }
            }
            avisos.addAll(conversion.avisos());
        }
        return avisos;
    }

    /** Las imágenes de una ficha técnica entre las que elegir, y las que ya están elegidas. */
    public record Candidatas(String loteId, List<CandidataImagen> candidatas, List<String> elegidas) {
    }

    /**
     * Saca las imágenes del PDF para el emergente. Se extraen cada vez que se
     * abre —viven media hora en un directorio temporal de la app de modelos—
     * y lo que se elige se copia al lote en el momento, así que no importa
     * que caduquen después.
     *
     * @throws IllegalArgumentException si el PDF no se deja leer
     */
    public Candidatas candidatas(ModeloEnCarga modelo, String fichaId) throws IOException {
        FichaEnCarga ficha = modelo.ficha(fichaId).orElseThrow(() -> new IllegalArgumentException(
                "Esa ficha técnica ya no está en el modelo"));
        String loteId = UUID.randomUUID().toString();
        List<CandidataImagen> candidatas = imagenesDePdf.extraer(loteId, Files.readAllBytes(ficha.pdf));
        List<String> elegidas = modelo.fotos.stream()
                .filter(foto -> fichaId.equals(foto.fichaId))
                .map(foto -> foto.claveFicha)
                .toList();
        return new Candidatas(loteId, candidatas, elegidas);
    }

    /**
     * Deja como fotos del modelo exactamente las imágenes elegidas de esa
     * ficha: las nuevas entran seleccionadas al final de la tira, y las que
     * se han desmarcado en el emergente salen de ella.
     */
    public void elegirCandidatas(LoteCarga lote, ModeloEnCarga modelo, String fichaId, String loteId,
                                 List<String> claves) throws IOException {
        FichaEnCarga ficha = modelo.ficha(fichaId).orElseThrow(() -> new IllegalArgumentException(
                "Esa ficha técnica ya no está en el modelo"));
        Set<String> elegidas = new java.util.LinkedHashSet<>(claves == null ? List.of() : claves);
        modelo.fotos.removeIf(foto -> fichaId.equals(foto.fichaId) && !elegidas.contains(foto.claveFicha));
        Path carpeta = Files.createDirectories(lote.directorio.resolve("fichas"));
        String base = ficha.nombre.replaceAll("(?i)\\.pdf$", "");
        for (String clave : elegidas) {
            if (modelo.fotos.stream().anyMatch(foto -> fichaId.equals(foto.fichaId)
                    && clave.equals(foto.claveFicha))) {
                continue;
            }
            byte[] png = imagenesDePdf.candidata(loteId, clave).orElseThrow(() -> new IllegalArgumentException(
                    "Las imágenes de la ficha han caducado: vuelve a abrirla"));
            BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png));
            if (imagen == null) {
                throw new IllegalArgumentException("Una de las imágenes de la ficha no se puede leer");
            }
            int secuencia = lote.siguiente();
            Path jpeg = carpeta.resolve(String.format("%05d.jpg", secuencia));
            Files.write(jpeg, ReductorImagen.jpeg(ReductorImagen.reducir(imagen, ReductorImagen.LADO_MAXIMO)));
            FotoEnCarga foto = FotoEnCarga.directa("f" + secuencia, base + " - " + clave + ".jpg",
                    FotoEnCarga.Origen.FICHA, jpeg, true);
            foto.fichaId = fichaId;
            foto.claveFicha = clave;
            modelo.fotos.add(foto);
        }
    }

    /** El JPEG reducido de una foto del lote, si ya está convertida; las ya guardadas se piden al catálogo. */
    public Optional<Path> jpegDe(ModeloEnCarga modelo, String fotoId) {
        return modelo.foto(fotoId).flatMap(FotoEnCarga::jpeg);
    }

    /** El PDF de una ficha técnica del lote. */
    public Optional<Path> pdfDe(ModeloEnCarga modelo, String fichaId) {
        return modelo.ficha(fichaId).map(ficha -> ficha.pdf);
    }

    /** Quita una ficha técnica del modelo, con las fotos que se hubieran elegido de ella. */
    public void quitarFicha(ModeloEnCarga modelo, String fichaId) {
        modelo.fichas.removeIf(ficha -> ficha.id.equals(fichaId));
        modelo.fotos.removeIf(foto -> fichaId.equals(foto.fichaId));
    }

    // --- Qué le falta a un modelo y qué trae de nuevo ---

    /**
     * Lo que impide aceptar el modelo: los obligatorios que faltan y lo que
     * está mal escrito. Vacío = se puede aceptar.
     */
    public List<String> faltan(LoteCarga lote, ModeloEnCarga modelo) {
        List<String> faltan = new ArrayList<>();
        if (clientes.clientePara(modelo.cliente).isEmpty()) {
            faltan.add("Falta el cliente");
        }
        if (modelo.codigo.isEmpty()) {
            if (modelo.nombrePrototipo.isEmpty()) {
                faltan.add("Falta la referencia (o la referencia de prototipo)");
            } else if (modelo.temporadaId == null) {
                faltan.add("Un prototipo sin referencia necesita su temporada");
            }
        } else if (modelo.codigo.startsWith("#")) {
            faltan.add("La referencia no puede empezar por #");
        }
        if (modelo.nombre.isEmpty()) {
            faltan.add("Falta el nombre del modelo");
        }
        for (ModeloEnCarga otro : lote.modelos) {
            if (otro == modelo) {
                break;
            }
            if (otro.estado != EstadoModelo.CANCELADO && esElMismo(otro, modelo)) {
                faltan.add("Ya hay otro " + modelo.nombreVisible() + " en esta carga");
                break;
            }
        }
        if (!modelo.codigo.isEmpty()) {
            ClienteCorte reglas = lectores.reglasDe(modelo.cliente);
            for (PielEnCarga piel : modelo.pieles) {
                ReferenciaCorte partida = reglas.partir(piel.referencia);
                String suModelo = partida.tienePiel() ? partida.modelo() : partida.referencia();
                if (!suModelo.equals(modelo.codigo)) {
                    faltan.add("La referencia " + piel.referencia + " no es del modelo " + modelo.codigo);
                }
                if (!piel.unidadesPorCaja.isEmpty() && !esEnteroPositivo(piel.unidadesPorCaja)) {
                    faltan.add("Las unidades por caja de " + piel.referencia + " son un número entero mayor que cero");
                }
                if (!piel.pesoNetoKg.isEmpty() && !esNumeroPositivo(piel.pesoNetoKg)) {
                    faltan.add("El peso de " + piel.referencia + " es un número de kilos mayor que cero");
                }
            }
        }
        return faltan;
    }

    private static boolean esElMismo(ModeloEnCarga uno, ModeloEnCarga otro) {
        if (!uno.cliente.equals(otro.cliente)) {
            return false;
        }
        if (!uno.codigo.isEmpty() || !otro.codigo.isEmpty()) {
            return uno.codigo.equals(otro.codigo);
        }
        return !uno.nombrePrototipo.isEmpty() && uno.nombrePrototipo.equals(otro.nombrePrototipo)
                && Objects.equals(uno.temporadaId, otro.temporadaId);
    }

    /**
     * Lo que un modelo que YA EXISTE gana con esta carga. Vacío = no trae
     * nada nuevo: sale en gris y al final de la lista.
     */
    public List<String> novedades(ModeloEnCarga modelo) {
        List<String> novedades = new ArrayList<>();
        if (!modelo.existe()) {
            return novedades;
        }
        long pielesNuevas = modelo.pieles.stream().filter(piel -> !piel.existe).count();
        long coloresNuevos = modelo.pieles.stream().flatMap(piel -> piel.colores.stream())
                .filter(color -> !color.existe).count();
        long fotos = modelo.fotosNuevas();
        int datos = modelo.huecosRellenados + modelo.tocados.size();
        if (pielesNuevas > 0) {
            novedades.add(pielesNuevas == 1 ? "1 piel nueva" : pielesNuevas + " pieles nuevas");
        }
        if (coloresNuevos > 0) {
            novedades.add(coloresNuevos == 1 ? "1 color nuevo" : coloresNuevos + " colores nuevos");
        }
        if (fotos > 0) {
            novedades.add(fotos == 1 ? "1 foto" : fotos + " fotos");
        }
        if (!modelo.fichas.isEmpty()) {
            novedades.add(modelo.fichas.size() == 1 ? "1 ficha técnica" : modelo.fichas.size() + " fichas técnicas");
        }
        if (!modelo.yaVinculado) {
            novedades.add("se vincula a la temporada");
        }
        if (datos > 0) {
            novedades.add(datos == 1 ? "1 dato nuevo" : datos + " datos nuevos");
        }
        if (modelo.ordenTocado && fotos == 0) {
            novedades.add("orden de las fotos");
        }
        return novedades;
    }

    /** Acepta, cancela o devuelve a pendiente. Aceptar exige los obligatorios; devuelve si se ha podido. */
    public boolean marcar(LoteCarga lote, ModeloEnCarga modelo, EstadoModelo estado) {
        if (estado == EstadoModelo.ACEPTADO && !faltan(lote, modelo).isEmpty()) {
            return false;
        }
        modelo.estado = estado;
        return true;
    }

    /**
     * Marca en verde todos los pendientes que tienen los obligatorios completos; devuelve cuántos. Los que
     * ya existen y no traen nada nuevo se quedan como están: no hay nada suyo que cargar.
     */
    public int aceptarCompletos(LoteCarga lote) {
        int aceptados = 0;
        for (ModeloEnCarga modelo : lote.modelos) {
            boolean sinNada = modelo.existe() && novedades(modelo).isEmpty();
            if (modelo.estado == EstadoModelo.PENDIENTE && !sinNada && marcar(lote, modelo, EstadoModelo.ACEPTADO)) {
                aceptados++;
            }
        }
        return aceptados;
    }

    String nombreDe(String cliente) {
        return clientes.clientePara(cliente).map(config -> config.getNombre()).orElse(cliente);
    }

    static boolean esEnteroPositivo(String texto) {
        try {
            return Integer.parseInt(texto.trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static boolean esNumeroPositivo(String texto) {
        try {
            return Double.parseDouble(texto.trim().replace(',', '.')) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String mayusculasONull(String texto) {
        return texto == null ? null : ModeloEnCarga.mayusculas(texto);
    }

    private static String recortado(String texto, int largo) {
        return texto.length() <= largo ? texto : texto.substring(0, largo);
    }

    private static <E extends Enum<E>> E enumerado(Class<E> tipo, String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(tipo, texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** El nombre sin la ruta: el navegador manda a veces la ruta entera. Recortado a mano, no con Paths. */
    private static String soloNombre(String ruta) {
        String limpia = ruta == null ? "" : ruta.replace('\\', '/');
        int barra = limpia.lastIndexOf('/');
        String nombre = (barra < 0 ? limpia : limpia.substring(barra + 1)).trim();
        return nombre.isEmpty() ? "fichero" : nombre;
    }
}
