package com.puntotres.packinglist.service.pieles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.config.BibliotecaPielesProperties;
import com.puntotres.packinglist.persistence.Articulo;
import com.puntotres.packinglist.persistence.ArticuloRepository;
import com.puntotres.packinglist.persistence.DocumentoPiel;
import com.puntotres.packinglist.persistence.DocumentoPielRepository;
import com.puntotres.packinglist.persistence.EventoPiel;
import com.puntotres.packinglist.persistence.EventoPielRepository;
import com.puntotres.packinglist.persistence.FichaPiel;
import com.puntotres.packinglist.persistence.FichaPielRepository;
import com.puntotres.packinglist.persistence.FotoCatalogo;
import com.puntotres.packinglist.persistence.FotoPiel;
import com.puntotres.packinglist.persistence.FotoPielRepository;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloRepository;
import com.puntotres.packinglist.persistence.ModeloTemporada;
import com.puntotres.packinglist.persistence.ModeloTemporadaRepository;
import com.puntotres.packinglist.persistence.Piel;
import com.puntotres.packinglist.persistence.PielId;
import com.puntotres.packinglist.persistence.PielRepository;
import com.puntotres.packinglist.persistence.PrecioPiel;
import com.puntotres.packinglist.persistence.PrecioPielRepository;
import com.puntotres.packinglist.persistence.Proveedor;
import com.puntotres.packinglist.persistence.ProveedorRepository;
import com.puntotres.packinglist.persistence.ResumenDocumentoPiel;
import com.puntotres.packinglist.persistence.ResumenFotoPiel;
import com.puntotres.packinglist.persistence.ResumenTemporada;
import com.puntotres.packinglist.persistence.TemporadaGuardadaRepository;
import com.puntotres.packinglist.persistence.TipoEventoPiel;

/**
 * La biblioteca de pieles: lo que se sabe de cada piel y de su proveedor
 * (precios según el mínimo, grueso, superficie, stock service, calidad,
 * fotos, fichas técnicas) y su recorrido entre clientes.
 *
 * Hay DOS cosas que se llaman piel y no son lo mismo. {@link Piel} es cómo
 * llama un cliente a una piel (AL0103 en AMI, PXCBC en APC): la usan el
 * corte, el catálogo y la pantalla de colores, y entra sola desde los excels
 * con poco más que la referencia. {@link FichaPiel} es QUÉ piel es
 * (proveedor + nombre) y es de esta biblioteca. La misma ficha se usa para
 * varios clientes, así que una {@link Piel} APUNTA a su ficha y el enlace es
 * opcional: una piel sin ficha es una piel incompleta, no un error, y las
 * automatizaciones no miran nunca el enlace.
 *
 * Reglas que no se deducen del código:
 *
 * - El enlace NUNCA se hace solo. Si una piel de cliente se llama igual que
 *   una ficha, se PROPONE ({@link #sinFicha()}) y lo acepta una persona
 *   (decisión del usuario): dos proveedores pueden llamar igual a dos pieles
 *   distintas.
 * - Al enlazar una piel de cliente que no tiene nombre, se le pone el de la
 *   ficha. Es rellenar un hueco; un nombre que ya estaba no se pisa.
 * - No hay un campo MOQ: el mínimo es el tramo de precio más bajo. Los
 *   tramos son los VIGENTES y se sustituyen enteros al guardar.
 * - Animal y tipo son listas cerradas del yml. Un valor que ya está guardado
 *   y se ha quitado de la lista se respeta; uno nuevo que no está, se rechaza.
 * - Los nombres de proveedor y de piel se guardan en mayúsculas y sin
 *   espacios de sobra, para que "Alpaca  touch" y "ALPACA TOUCH" sean la
 *   misma. Los comentarios y las notas se guardan como se escriben.
 * - El recorrido junta lo apuntado a mano ({@link EventoPiel}, con su día) y
 *   lo que ya sabe el catálogo (los artículos que llevan una piel enlazada,
 *   con su temporada). Del catálogo solo hay temporada y no día, así que
 *   esos pasos llevan una fecha APROXIMADA (cuándo se guardó la temporada)
 *   que sirve para ordenar y no se enseña como si fuera exacta.
 */
@Service
public class BibliotecaPieles {

    /** Lo que admite un documento: una ficha técnica escaneada pesa unos pocos megas. */
    public static final int MAX_BYTES_DOCUMENTO = 25 * 1024 * 1024;

    private static final int VALORACION_MINIMA = 1;
    private static final int VALORACION_MAXIMA = 5;

    /** Una ficha con lo que hace falta para pintarla en la tabla. */
    public record Fila(FichaPiel ficha, Proveedor proveedor, List<PrecioPiel> precios, Long fotoId,
                       List<Piel> enlazadas) {

        public String grueso() {
            return new Rango(ficha.getGruesoMinMm(), ficha.getGruesoMaxMm()).texto();
        }

        public String superficie() {
            return new Rango(ficha.getSuperficieMinM2(), ficha.getSuperficieMaxM2()).texto();
        }

        /** Cada tramo como se lee: "50 m² → 42 €", "sin mínimo → 48 €". El primero es el mínimo de pedido. */
        public List<String> tramosTexto() {
            return precios.stream().map(BibliotecaPieles::tramoTexto).toList();
        }
    }

    /** Lo que se busca en la tabla; un campo vacío o nulo no filtra. */
    public record Filtro(String texto, Long proveedorId, String cliente, String animal, String tipo,
                         Double gruesoDesde, Double gruesoHasta, Boolean stockService) {

        public static final Filtro NINGUNO = new Filtro(null, null, null, null, null, null, null, null);

        public boolean vacio() {
            return blanco(texto) && proveedorId == null && blanco(cliente) && blanco(animal) && blanco(tipo)
                    && gruesoDesde == null && gruesoHasta == null && stockService == null;
        }
    }

    /** El resultado de un alta: la ficha y si ya existía (mismo proveedor y mismo nombre). */
    public record Alta(FichaPiel ficha, boolean yaExistia) {
    }

    /** Un tramo de precio tal como llega de la pantalla. */
    public record Tramo(Double moqM2, Double precioEurM2) {
    }

    /** Una ficha a la que una piel de cliente sin ficha podría pertenecer, porque se llaman igual. */
    public record Propuesta(Long fichaId, String nombre, String proveedor) {
    }

    /** Una piel de cliente sin ficha, con las fichas que se le proponen (las que se llaman como ella). */
    public record SinFicha(Piel piel, List<Propuesta> propuestas) {
    }

    /**
     * Un paso del recorrido de una piel. {@code fechaExacta} distingue lo
     * apuntado a mano (con su día) de lo que sale del catálogo (solo se sabe
     * la temporada, y la fecha es la de guardado de esa temporada, que sirve
     * para ordenar). {@code eventoId} es nulo en los pasos del catálogo, que
     * no se pueden borrar desde aquí.
     */
    public record Paso(LocalDate fecha, boolean fechaExacta, String cliente, String que, String detalle,
                       String nota, Long eventoId) {
    }

    private final ProveedorRepository proveedores;
    private final FichaPielRepository fichas;
    private final PrecioPielRepository precios;
    private final FotoPielRepository fotos;
    private final DocumentoPielRepository documentos;
    private final EventoPielRepository eventos;
    private final PielRepository pieles;
    private final ArticuloRepository articulos;
    private final ModeloRepository modelos;
    private final ModeloTemporadaRepository modelosTemporada;
    private final TemporadaGuardadaRepository temporadas;
    private final BibliotecaPielesProperties listas;

    public BibliotecaPieles(ProveedorRepository proveedores, FichaPielRepository fichas,
                            PrecioPielRepository precios, FotoPielRepository fotos,
                            DocumentoPielRepository documentos, EventoPielRepository eventos,
                            PielRepository pieles, ArticuloRepository articulos, ModeloRepository modelos,
                            ModeloTemporadaRepository modelosTemporada, TemporadaGuardadaRepository temporadas,
                            BibliotecaPielesProperties listas) {
        this.proveedores = proveedores;
        this.fichas = fichas;
        this.precios = precios;
        this.fotos = fotos;
        this.documentos = documentos;
        this.eventos = eventos;
        this.pieles = pieles;
        this.articulos = articulos;
        this.modelos = modelos;
        this.modelosTemporada = modelosTemporada;
        this.temporadas = temporadas;
        this.listas = listas;
    }

    // --- Proveedores ---

    public List<Proveedor> proveedores() {
        return proveedores.findAllByOrderByNombre();
    }

    public Optional<Proveedor> proveedor(Long id) {
        return id == null ? Optional.empty() : proveedores.findById(id);
    }

    /** Cuántas pieles tiene cada proveedor: id → número, solo los que tienen alguna. */
    public Map<Long, Long> pielesPorProveedor() {
        return fichas.findAll().stream()
                .collect(Collectors.groupingBy(FichaPiel::getProveedorId, Collectors.counting()));
    }

    /** El proveedor con ese nombre, dándolo de alta si no existía. */
    @Transactional
    public Proveedor asegurarProveedor(String nombre) {
        String elNombre = clave(nombre);
        if (elNombre.isEmpty()) {
            throw new IllegalArgumentException("El proveedor necesita un nombre.");
        }
        return proveedores.findByNombre(elNombre).orElseGet(() -> proveedores.save(new Proveedor(elNombre)));
    }

    /**
     * Cambia UN dato de un proveedor. Los campos son los de la pantalla:
     * nombre, contacto, email, telefono, pais, condiciones, responsabilidad,
     * entregaATiempo y notaValoracion. Un valor vacío borra el dato (menos el
     * nombre, que es obligatorio).
     */
    @Transactional
    public Proveedor guardarCampoProveedor(Long id, String campo, String valor) {
        Proveedor proveedor = proveedor(id)
                .orElseThrow(() -> new IllegalArgumentException("Ese proveedor ya no existe."));
        switch (campo == null ? "" : campo) {
            case "nombre" -> {
                String nombre = clave(valor);
                if (nombre.isEmpty()) {
                    throw new IllegalArgumentException("El proveedor necesita un nombre.");
                }
                Optional<Proveedor> otro = proveedores.findByNombre(nombre);
                if (otro.isPresent() && !otro.get().getId().equals(id)) {
                    throw new IllegalArgumentException("Ya hay un proveedor que se llama " + nombre + ".");
                }
                proveedor.setNombre(nombre);
            }
            case "contacto" -> proveedor.setContacto(libre(valor, 200));
            case "email" -> proveedor.setEmail(libre(valor, 200));
            case "telefono" -> proveedor.setTelefono(libre(valor, 60));
            case "pais" -> proveedor.setPais(libre(valor, 80));
            case "condiciones" -> proveedor.setCondiciones(libre(valor, 1000));
            case "responsabilidad" -> proveedor.setResponsabilidad(valoracion(valor));
            case "entregaATiempo" -> proveedor.setEntregaATiempo(valoracion(valor));
            case "notaValoracion" -> proveedor.setNotaValoracion(libre(valor, 2000));
            default -> throw new IllegalArgumentException("Campo desconocido: " + campo + ".");
        }
        return proveedor;
    }

    /** Un proveedor con pieles no se borra: habría que decidir qué pasa con ellas. */
    @Transactional
    public void borrarProveedor(Long id) {
        Proveedor proveedor = proveedor(id).orElse(null);
        if (proveedor == null) {
            return;
        }
        long suyas = fichas.countByProveedorId(id);
        if (suyas > 0) {
            throw new IllegalStateException(proveedor.getNombre() + " tiene " + suyas
                    + (suyas == 1 ? " piel" : " pieles") + " en la biblioteca: bórralas o pásalas a otro "
                    + "proveedor antes de borrarlo.");
        }
        proveedores.delete(proveedor);
    }

    // --- Fichas ---

    public Optional<FichaPiel> ficha(Long id) {
        return id == null ? Optional.empty() : fichas.findById(id);
    }

    /** Todas las fichas que pasan el filtro, por proveedor y nombre, con lo que pinta la tabla. */
    public List<Fila> buscar(Filtro filtro) {
        Filtro elFiltro = filtro == null ? Filtro.NINGUNO : filtro;
        Map<Long, Proveedor> porId = proveedores.findAll().stream()
                .collect(Collectors.toMap(Proveedor::getId, proveedor -> proveedor));
        Map<Long, List<Piel>> enlazadas = pieles.findByFichaPielIdIsNotNull().stream()
                .sorted(Comparator.comparing(Piel::getCliente).thenComparing(Piel::getRefPiel))
                .collect(Collectors.groupingBy(Piel::getFichaPielId));
        List<FichaPiel> pasan = fichas.findAll().stream()
                .filter(ficha -> pasa(ficha, porId.get(ficha.getProveedorId()),
                        enlazadas.getOrDefault(ficha.getId(), List.of()), elFiltro))
                .sorted(Comparator.comparing((FichaPiel ficha) -> nombreDe(porId.get(ficha.getProveedorId())))
                        .thenComparing(FichaPiel::getNombre))
                .toList();
        List<Long> ids = pasan.stream().map(FichaPiel::getId).toList();
        Map<Long, List<PrecioPiel>> tramos = ids.isEmpty() ? Map.of() : precios.findByFichaPielIdIn(ids).stream()
                .sorted(ORDEN_TRAMOS)
                .collect(Collectors.groupingBy(PrecioPiel::getFichaPielId));
        Map<Long, Long> principales = principalesDe(ids);
        return pasan.stream()
                .map(ficha -> new Fila(ficha, porId.get(ficha.getProveedorId()),
                        tramos.getOrDefault(ficha.getId(), List.of()), principales.get(ficha.getId()),
                        enlazadas.getOrDefault(ficha.getId(), List.of())))
                .toList();
    }

    private static boolean pasa(FichaPiel ficha, Proveedor proveedor, List<Piel> enlazadas, Filtro filtro) {
        if (filtro.proveedorId() != null && !filtro.proveedorId().equals(ficha.getProveedorId())) {
            return false;
        }
        if (!blanco(filtro.animal()) && !filtro.animal().trim().equalsIgnoreCase(ficha.getAnimal())) {
            return false;
        }
        if (!blanco(filtro.tipo()) && !filtro.tipo().trim().equalsIgnoreCase(ficha.getTipo())) {
            return false;
        }
        if (filtro.stockService() != null && !filtro.stockService().equals(ficha.getStockService())) {
            return false;
        }
        if ((filtro.gruesoDesde() != null || filtro.gruesoHasta() != null)
                && !new Rango(ficha.getGruesoMinMm(), ficha.getGruesoMaxMm())
                        .solapa(filtro.gruesoDesde(), filtro.gruesoHasta())) {
            return false;
        }
        if (!blanco(filtro.cliente())
                && enlazadas.stream().noneMatch(piel -> piel.getCliente().equals(clave(filtro.cliente())))) {
            return false;
        }
        if (blanco(filtro.texto())) {
            return true;
        }
        String buscado = clave(filtro.texto());
        return ficha.getNombre().contains(buscado)
                || ficha.getRefProveedor().toUpperCase(Locale.ROOT).contains(buscado)
                || nombreDe(proveedor).contains(buscado)
                || enlazadas.stream().anyMatch(piel -> piel.getRefPiel().contains(buscado));
    }

    /**
     * Da de alta una piel. Lo único obligatorio es el proveedor (que se crea
     * si no existía) y el nombre; el animal, si no se dice, es el primero de
     * la lista. Si ese proveedor ya tiene una piel con ese nombre, devuelve
     * esa y no crea otra ni le cambia nada.
     */
    @Transactional
    public Alta crear(String nombreProveedor, String nombre, String animal, String tipo) {
        String elNombre = clave(nombre);
        if (elNombre.isEmpty()) {
            throw new IllegalArgumentException("La piel necesita un nombre.");
        }
        Proveedor proveedor = asegurarProveedor(nombreProveedor);
        Optional<FichaPiel> existente = fichas.findByProveedorIdAndNombre(proveedor.getId(), elNombre);
        if (existente.isPresent()) {
            return new Alta(existente.get(), true);
        }
        FichaPiel ficha = new FichaPiel(proveedor.getId(), elNombre);
        ficha.setAnimal(blanco(animal) ? listas.animalPorDefecto() : deLista(listas.getAnimales(),
                listas.animal(animal), "", "animal"));
        ficha.setTipo(deLista(listas.getTipos(), listas.tipo(tipo), "", "tipo"));
        return new Alta(fichas.save(ficha), false);
    }

    /**
     * Cambia UN dato de una ficha, tal como llega de una casilla: proveedor,
     * nombre, refProveedor, animal, tipo, grueso y superficie (un valor o un
     * rango, ver {@link Rango}), stockService y moqPorColor ("true", "false"
     * o vacío = no se sabe), plazoEntregaDias, fechaPrecios (aaaa-mm-dd),
     * calidad (1-5), notaCalidad y comentarios. Vacío borra el dato, menos
     * el proveedor y el nombre, que son obligatorios. Un valor que no vale
     * lanza {@link IllegalArgumentException} con el motivo, y no cambia nada.
     */
    @Transactional
    public FichaPiel guardarCampo(Long id, String campo, String valor) {
        FichaPiel ficha = ficha(id).orElseThrow(() -> new IllegalArgumentException("Esa piel ya no existe."));
        aplicar(ficha, campo, valor);
        return ficha;
    }

    /** Varios datos de una vez (el formulario de la ficha); o se guardan todos o ninguno. */
    @Transactional
    public FichaPiel guardar(Long id, Map<String, String> campos) {
        FichaPiel ficha = ficha(id).orElseThrow(() -> new IllegalArgumentException("Esa piel ya no existe."));
        campos.forEach((campo, valor) -> aplicar(ficha, campo, valor));
        return ficha;
    }

    private void aplicar(FichaPiel ficha, String campo, String valor) {
        switch (campo == null ? "" : campo) {
            case "proveedor" -> {
                Proveedor proveedor = asegurarProveedor(valor);
                comprobarLibre(ficha, proveedor.getId(), ficha.getNombre());
                ficha.setProveedorId(proveedor.getId());
            }
            case "nombre" -> {
                String nombre = clave(valor);
                if (nombre.isEmpty()) {
                    throw new IllegalArgumentException("La piel necesita un nombre.");
                }
                comprobarLibre(ficha, ficha.getProveedorId(), nombre);
                ficha.setNombre(nombre);
            }
            case "refProveedor" -> ficha.setRefProveedor(clave(valor).isEmpty() ? null : recortar(clave(valor), 80));
            case "animal" -> ficha.setAnimal(
                    deLista(listas.getAnimales(), listas.animal(valor), ficha.getAnimal(), "animal"));
            case "tipo" -> ficha.setTipo(deLista(listas.getTipos(), listas.tipo(valor), ficha.getTipo(), "tipo"));
            case "grueso" -> {
                Rango rango = Rango.de(valor);
                ficha.setGruesoMinMm(rango.minimo());
                ficha.setGruesoMaxMm(rango.maximo());
            }
            case "superficie" -> {
                Rango rango = Rango.de(valor);
                ficha.setSuperficieMinM2(rango.minimo());
                ficha.setSuperficieMaxM2(rango.maximo());
            }
            case "stockService" -> ficha.setStockService(siNo(valor));
            case "moqPorColor" -> ficha.setMoqPorColor(siNo(valor));
            case "plazoEntregaDias" -> ficha.setPlazoEntregaDias(entero(valor, "El plazo de entrega"));
            case "fechaPrecios" -> ficha.setFechaPrecios(fecha(valor));
            case "calidad" -> ficha.setCalidad(valoracion(valor));
            case "notaCalidad" -> ficha.setNotaCalidad(libre(valor, 1000));
            case "comentarios" -> ficha.setComentarios(libre(valor, 4000));
            default -> throw new IllegalArgumentException("Campo desconocido: " + campo + ".");
        }
    }

    private void comprobarLibre(FichaPiel ficha, Long proveedorId, String nombre) {
        Optional<FichaPiel> otra = fichas.findByProveedorIdAndNombre(proveedorId, nombre);
        if (otra.isPresent() && !otra.get().getId().equals(ficha.getId())) {
            throw new IllegalArgumentException("Ese proveedor ya tiene una piel que se llama " + nombre + ".");
        }
    }

    /**
     * Una ficha nueva del mismo proveedor con otro nombre y los mismos datos
     * de partida (animal, tipo, medidas, stock service, plazo, tramos de
     * precio): las pieles de un proveedor comparten casi todo. No se copian
     * la calidad, los comentarios, las fotos, los documentos ni el recorrido,
     * que son de ESA piel.
     */
    @Transactional
    public FichaPiel duplicar(Long id, String nombreNuevo) {
        FichaPiel original = ficha(id).orElseThrow(() -> new IllegalArgumentException("Esa piel ya no existe."));
        String nombre = clave(nombreNuevo);
        if (nombre.isEmpty()) {
            throw new IllegalArgumentException("La piel nueva necesita un nombre.");
        }
        if (fichas.findByProveedorIdAndNombre(original.getProveedorId(), nombre).isPresent()) {
            throw new IllegalArgumentException("Ese proveedor ya tiene una piel que se llama " + nombre + ".");
        }
        FichaPiel copia = new FichaPiel(original.getProveedorId(), nombre);
        copia.setAnimal(original.getAnimal());
        copia.setTipo(original.getTipo());
        copia.setGruesoMinMm(original.getGruesoMinMm());
        copia.setGruesoMaxMm(original.getGruesoMaxMm());
        copia.setSuperficieMinM2(original.getSuperficieMinM2());
        copia.setSuperficieMaxM2(original.getSuperficieMaxM2());
        copia.setStockService(original.getStockService());
        copia.setPlazoEntregaDias(original.getPlazoEntregaDias());
        copia.setMoqPorColor(original.getMoqPorColor());
        copia.setFechaPrecios(original.getFechaPrecios());
        FichaPiel guardada = fichas.save(copia);
        for (PrecioPiel tramo : precios.findByFichaPielId(id)) {
            precios.save(new PrecioPiel(guardada.getId(), tramo.getMoqM2(), tramo.getPrecioEurM2()));
        }
        return guardada;
    }

    /**
     * Borra la ficha con sus precios, fotos, documentos y recorrido. Las
     * pieles de cliente que apuntaban a ella NO se borran: se quedan sin
     * ficha, como estaban antes de enlazarlas.
     */
    @Transactional
    public void borrar(Long id) {
        if (id == null || !fichas.existsById(id)) {
            return;
        }
        // Se sueltan aquí y no solo por el ON DELETE SET NULL de la base, para
        // que las entidades ya cargadas en esta transacción no se queden con
        // el id de una ficha que ya no existe.
        pieles.findByFichaPielIdOrderByClienteAscRefPielAsc(id).forEach(piel -> piel.setFichaPielId(null));
        pieles.flush();
        fichas.deleteById(id);
    }

    // --- Precios ---

    /** Los tramos de una ficha, del mínimo más bajo al más alto (el que no tiene mínimo, primero). */
    public List<PrecioPiel> preciosDe(Long fichaId) {
        return precios.findByFichaPielId(fichaId).stream().sorted(ORDEN_TRAMOS).toList();
    }

    /**
     * Sustituye TODOS los tramos de la ficha por estos. Un tramo sin mínimo
     * y sin precio es una fila que no se rellenó y se descarta; uno con solo
     * el mínimo o solo el precio se guarda.
     */
    @Transactional
    public void guardarPrecios(Long fichaId, List<Tramo> tramos) {
        if (!fichas.existsById(fichaId)) {
            throw new IllegalArgumentException("Esa piel ya no existe.");
        }
        precios.deleteByFichaPielId(fichaId);
        precios.flush();
        if (tramos == null) {
            return;
        }
        for (Tramo tramo : tramos) {
            if (tramo == null || (tramo.moqM2() == null && tramo.precioEurM2() == null)) {
                continue;
            }
            precios.save(new PrecioPiel(fichaId, tramo.moqM2(), tramo.precioEurM2()));
        }
    }

    private static final Comparator<PrecioPiel> ORDEN_TRAMOS = Comparator
            .comparing((PrecioPiel tramo) -> tramo.getMoqM2() == null ? -1.0 : tramo.getMoqM2())
            .thenComparing(PrecioPiel::getId);

    static String tramoTexto(PrecioPiel tramo) {
        String minimo = tramo.getMoqM2() == null ? "sin mínimo" : Rango.formato(tramo.getMoqM2()) + " m²";
        String precio = tramo.getPrecioEurM2() == null ? "?" : Rango.formato(tramo.getPrecioEurM2()) + " €";
        return minimo + " → " + precio;
    }

    // --- Fotos ---

    public List<ResumenFotoPiel> fotosDe(Long fichaId) {
        return fichaId == null ? List.of() : fotos.resumenes(List.of(fichaId));
    }

    public Optional<FotoPiel> fotoConBytes(Long id) {
        return id == null ? Optional.empty() : fotos.findById(id);
    }

    /** Id de la ficha → id de la foto que la representa (la principal o, si no hay, la primera). */
    public Map<Long, Long> principalesDe(Collection<Long> fichaIds) {
        Map<Long, Long> principales = new HashMap<>();
        if (fichaIds == null || fichaIds.isEmpty()) {
            return principales;
        }
        for (ResumenFotoPiel foto : fotos.resumenes(fichaIds)) {
            if (foto.principal() || !principales.containsKey(foto.fichaPielId())) {
                principales.put(foto.fichaPielId(), foto.id());
            }
        }
        return principales;
    }

    /**
     * Guarda un JPEG ya reducido. El nombre se hace único dentro de la ficha
     * (la cámara del móvil llama image.jpg a todas) y la primera foto de una
     * ficha nace principal.
     */
    @Transactional
    public ResumenFotoPiel guardarFoto(Long fichaId, String nombreFichero, byte[] jpeg) {
        if (fichaId == null || !fichas.existsById(fichaId)) {
            throw new IllegalArgumentException("Esa piel ya no existe.");
        }
        if (jpeg == null || jpeg.length == 0) {
            throw new IllegalArgumentException("La foto está vacía.");
        }
        List<FotoPiel> suyas = fotos.findByFichaPielIdOrderByOrdenAscIdAsc(fichaId);
        Set<String> usados = suyas.stream().map(foto -> foto.getNombreFichero().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        String nombre = FotoCatalogo.nombreLibre(blanco(nombreFichero) ? "foto.jpg" : nombreFichero.trim(), usados);
        int orden = suyas.isEmpty() ? 0 : suyas.get(suyas.size() - 1).getOrden() + 1;
        FotoPiel nueva = new FotoPiel(fichaId, nombre, orden, jpeg);
        nueva.setPrincipal(suyas.stream().noneMatch(FotoPiel::isPrincipal));
        FotoPiel guardada = fotos.save(nueva);
        return new ResumenFotoPiel(guardada.getId(), fichaId, nombre, guardada.isPrincipal(), orden);
    }

    @Transactional
    public void marcarPrincipal(Long fotoId) {
        fotoConBytes(fotoId).ifPresent(elegida -> {
            for (FotoPiel foto : fotos.findByFichaPielIdOrderByOrdenAscIdAsc(elegida.getFichaPielId())) {
                foto.setPrincipal(foto.getId().equals(fotoId));
            }
        });
    }

    /** Si era la principal, la siguiente pasa a serlo. Devuelve la ficha de la foto, para volver a ella. */
    @Transactional
    public Optional<Long> borrarFoto(Long fotoId) {
        FotoPiel foto = fotoConBytes(fotoId).orElse(null);
        if (foto == null) {
            return Optional.empty();
        }
        fotos.delete(foto);
        fotos.flush();
        if (foto.isPrincipal()) {
            fotos.findByFichaPielIdOrderByOrdenAscIdAsc(foto.getFichaPielId()).stream().findFirst()
                    .ifPresent(siguiente -> siguiente.setPrincipal(true));
        }
        return Optional.of(foto.getFichaPielId());
    }

    // --- Documentos ---

    public List<ResumenDocumentoPiel> documentosDe(Long fichaId) {
        return fichaId == null ? List.of() : documentos.resumenes(fichaId);
    }

    public Optional<DocumentoPiel> documento(Long id) {
        return id == null ? Optional.empty() : documentos.findById(id);
    }

    @Transactional
    public DocumentoPiel guardarDocumento(Long fichaId, String nombreFichero, String mime, byte[] bytes) {
        if (fichaId == null || !fichas.existsById(fichaId)) {
            throw new IllegalArgumentException("Esa piel ya no existe.");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("El documento está vacío.");
        }
        if (bytes.length > MAX_BYTES_DOCUMENTO) {
            throw new IllegalArgumentException("El documento pesa más de "
                    + MAX_BYTES_DOCUMENTO / (1024 * 1024) + " MB.");
        }
        String nombre = recortar(blanco(nombreFichero) ? "documento" : nombreFichero.trim(), 255);
        return documentos.save(new DocumentoPiel(fichaId, nombre, recortar(mime == null ? "" : mime.trim(), 120),
                bytes));
    }

    @Transactional
    public Optional<Long> borrarDocumento(Long id) {
        Optional<DocumentoPiel> documento = documento(id);
        documento.ifPresent(documentos::delete);
        return documento.map(DocumentoPiel::getFichaPielId);
    }

    // --- Enlaces con las pieles de cliente ---

    public List<Piel> enlazadas(Long fichaId) {
        return fichaId == null ? List.of() : pieles.findByFichaPielIdOrderByClienteAscRefPielAsc(fichaId);
    }

    /**
     * Las pieles de cliente que aún no tienen ficha, por cliente y
     * referencia, cada una con las fichas que se le PROPONEN: las que se
     * llaman exactamente como ella (sin mirar mayúsculas ni espacios de
     * sobra). Una piel sin nombre no tiene propuesta. Proponer no enlaza:
     * lo hace {@link #enlazar} cuando una persona lo acepta.
     */
    public List<SinFicha> sinFicha() {
        List<Piel> sueltas = pieles.findByFichaPielIdIsNullOrderByClienteAscRefPielAsc();
        if (sueltas.isEmpty()) {
            return List.of();
        }
        Map<Long, String> proveedorDe = proveedores.findAll().stream()
                .collect(Collectors.toMap(Proveedor::getId, Proveedor::getNombre));
        Map<String, List<FichaPiel>> porNombre = fichas.findAll().stream()
                .sorted(Comparator.comparing(ficha -> proveedorDe.getOrDefault(ficha.getProveedorId(), "")))
                .collect(Collectors.groupingBy(FichaPiel::getNombre, LinkedHashMap::new, Collectors.toList()));
        List<SinFicha> resultado = new ArrayList<>();
        for (Piel piel : sueltas) {
            String nombre = clave(piel.getNombre());
            List<Propuesta> propuestas = nombre.isEmpty() ? List.of()
                    : porNombre.getOrDefault(nombre, List.of()).stream()
                            .map(ficha -> new Propuesta(ficha.getId(), ficha.getNombre(),
                                    proveedorDe.getOrDefault(ficha.getProveedorId(), "")))
                            .toList();
            resultado.add(new SinFicha(piel, propuestas));
        }
        return resultado;
    }

    /**
     * Enlaza una piel de cliente con su ficha. Si la piel de cliente no
     * existía se da de alta con el nombre de la ficha; si existía sin nombre,
     * se le pone el de la ficha; si ya tenía nombre, no se toca.
     */
    @Transactional
    public Piel enlazar(String cliente, String refPiel, Long fichaId) {
        if (blanco(cliente) || blanco(refPiel)) {
            throw new IllegalArgumentException("Para enlazar hacen falta el cliente y la referencia de la piel.");
        }
        FichaPiel ficha = ficha(fichaId).orElseThrow(() -> new IllegalArgumentException("Esa piel ya no existe."));
        Piel piel = pieles.findById(new PielId(clave(cliente), clave(refPiel)))
                .orElseGet(() -> pieles.save(new Piel(clave(cliente), clave(refPiel), ficha.getNombre())));
        if (piel.getNombre().isBlank()) {
            piel.actualizar(ficha.getNombre());
        }
        piel.setFichaPielId(fichaId);
        return piel;
    }

    /** Deja la piel de cliente sin ficha. Su nombre no se toca. */
    @Transactional
    public Optional<Long> desenlazar(String cliente, String refPiel) {
        if (blanco(cliente) || blanco(refPiel)) {
            return Optional.empty();
        }
        Optional<Piel> piel = pieles.findById(new PielId(clave(cliente), clave(refPiel)));
        Optional<Long> ficha = piel.map(Piel::getFichaPielId);
        piel.ifPresent(laPiel -> laPiel.setFichaPielId(null));
        return ficha;
    }

    // --- Recorrido ---

    @Transactional
    public EventoPiel apuntar(Long fichaId, LocalDate fecha, String cliente, TipoEventoPiel tipo, String modelo,
                              String nota) {
        if (fichaId == null || !fichas.existsById(fichaId)) {
            throw new IllegalArgumentException("Esa piel ya no existe.");
        }
        if (blanco(cliente)) {
            throw new IllegalArgumentException("El evento necesita un cliente.");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("El evento necesita decir qué pasó.");
        }
        String elModelo = clave(modelo);
        return eventos.save(new EventoPiel(fichaId, fecha == null ? LocalDate.now() : fecha,
                recortar(clave(cliente), 60), tipo, elModelo.isEmpty() ? null : recortar(elModelo, 200),
                libre(nota, 2000)));
    }

    @Transactional
    public Optional<Long> borrarEvento(Long eventoId) {
        Optional<EventoPiel> evento = eventoId == null ? Optional.empty() : eventos.findById(eventoId);
        evento.ifPresent(eventos::delete);
        return evento.map(EventoPiel::getFichaPielId);
    }

    /**
     * El recorrido de una piel, de lo más antiguo a lo más reciente: lo
     * apuntado a mano y lo que sabe el catálogo de las pieles de cliente
     * enlazadas (qué artículos la llevan, en qué etapa está su modelo y en
     * qué temporadas va). Los pasos del catálogo van agrupados por cliente,
     * temporada y etapa, con las referencias juntas, para que una piel que
     * va en treinta bolsos no sean treinta líneas.
     */
    public List<Paso> recorrido(Long fichaId) {
        List<Paso> pasos = new ArrayList<>();
        for (EventoPiel evento : eventos.findByFichaPielIdOrderByFechaAscIdAsc(fichaId)) {
            pasos.add(new Paso(evento.getFecha(), true, evento.getCliente(), evento.getTipo().getTexto(),
                    evento.getModelo(), evento.getNota(), evento.getId()));
        }
        pasos.addAll(delCatalogo(fichaId));
        pasos.sort(Comparator.comparing(Paso::fecha)
                // El mismo día, lo del catálogo antes: su fecha es aproximada y casi siempre anterior.
                .thenComparing(Paso::fechaExacta)
                .thenComparing(Paso::cliente));
        return pasos;
    }

    private List<Paso> delCatalogo(Long fichaId) {
        List<Piel> suyas = enlazadas(fichaId);
        if (suyas.isEmpty()) {
            return List.of();
        }
        Map<Long, ResumenTemporada> temporadaDe = temporadas.resumenes().stream()
                .collect(Collectors.toMap(ResumenTemporada::id, temporada -> temporada));
        // cliente | temporada | etapa → referencias y la fecha más antigua que se conoce.
        record Clave(String cliente, String temporada, String etapa) {
        }
        Map<Clave, Set<String>> referencias = new LinkedHashMap<>();
        Map<Clave, LocalDate> fechas = new HashMap<>();
        for (Piel piel : suyas) {
            for (Articulo articulo : articulos.findByClienteAndRefPielOrderByReferencia(piel.getCliente(),
                    piel.getRefPiel())) {
                Modelo modelo = modelos.findById(articulo.getModeloId()).orElse(null);
                String etapa = modelo == null ? "" : modelo.getEtapa().texto();
                List<ResumenTemporada> susTemporadas = modelosTemporada.findByModeloId(articulo.getModeloId())
                        .stream().map(ModeloTemporada::getTemporadaId).map(temporadaDe::get)
                        .filter(temporada -> temporada != null).toList();
                if (susTemporadas.isEmpty()) {
                    Clave clave = new Clave(piel.getCliente(), "", etapa);
                    referencias.computeIfAbsent(clave, c -> new TreeSet<>()).add(articulo.getReferencia());
                    fechas.merge(clave, articulo.getFechaActualizacion().toLocalDate(), BibliotecaPieles::antes);
                }
                for (ResumenTemporada temporada : susTemporadas) {
                    Clave clave = new Clave(piel.getCliente(), temporada.temporada(), etapa);
                    referencias.computeIfAbsent(clave, c -> new TreeSet<>()).add(articulo.getReferencia());
                    fechas.merge(clave, temporada.fechaActualizacion().toLocalDate(), BibliotecaPieles::antes);
                }
            }
        }
        List<Paso> pasos = new ArrayList<>();
        referencias.forEach((clave, refs) -> pasos.add(new Paso(fechas.get(clave), false, clave.cliente(),
                (clave.etapa().isEmpty() ? "En el catálogo" : clave.etapa())
                        + (clave.temporada().isEmpty() ? "" : " · " + clave.temporada()),
                String.join(", ", refs), "", null)));
        return pasos;
    }

    private static LocalDate antes(LocalDate una, LocalDate otra) {
        return una.isBefore(otra) ? una : otra;
    }

    // --- Internos ---

    /**
     * El valor si está en la lista cerrada, o si es el que la ficha ya tenía
     * guardado (uno que se quitó del yml no se pierde ni impide guardar el
     * resto del formulario). Vacío es "no se sabe" y siempre vale.
     */
    private static String deLista(List<String> lista, String valor, String actual, String nombreLista) {
        if (valor.isEmpty()) {
            return null;
        }
        if (lista.contains(valor) || valor.equalsIgnoreCase(actual)) {
            return valor;
        }
        throw new IllegalArgumentException("«" + valor + "» no está en la lista de " + nombreLista
                + ": " + String.join(", ", lista) + ".");
    }

    private static Integer valoracion(String valor) {
        Integer numero = entero(valor, "La valoración");
        if (numero != null && (numero < VALORACION_MINIMA || numero > VALORACION_MAXIMA)) {
            throw new IllegalArgumentException("La valoración va de " + VALORACION_MINIMA + " a "
                    + VALORACION_MAXIMA + ".");
        }
        return numero;
    }

    private static Integer entero(String valor, String que) {
        if (blanco(valor)) {
            return null;
        }
        try {
            int numero = Integer.parseInt(valor.trim());
            if (numero < 0) {
                throw new NumberFormatException();
            }
            return numero;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(que + " es un número entero: «" + valor.trim() + "» no lo es.");
        }
    }

    private static Boolean siNo(String valor) {
        if (blanco(valor)) {
            return null;
        }
        return switch (valor.trim().toLowerCase(Locale.ROOT)) {
            case "true", "si", "sí" -> Boolean.TRUE;
            case "false", "no" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException("«" + valor.trim() + "» no es sí ni no.");
        };
    }

    private static LocalDate fecha(String valor) {
        if (blanco(valor)) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("«" + valor.trim() + "» no es una fecha.");
        }
    }

    /**
     * Un número con coma o con punto ("42,5"), o nulo si está vacío. Para los
     * tramos de precio, que llegan como texto de la pantalla.
     */
    public static Double numero(String valor, String que) {
        if (blanco(valor)) {
            return null;
        }
        try {
            double numero = Double.parseDouble(valor.trim().replace(',', '.'));
            if (numero < 0 || Double.isNaN(numero) || Double.isInfinite(numero)) {
                throw new NumberFormatException();
            }
            return numero;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(que + " es un número: «" + valor.trim() + "» no lo es.");
        }
    }

    private static String nombreDe(Proveedor proveedor) {
        return proveedor == null ? "" : proveedor.getNombre();
    }

    /** Texto libre: recortado y a su largo; vacío es nulo. No se pasa a mayúsculas. */
    private static String libre(String valor, int largo) {
        return blanco(valor) ? null : recortar(valor.trim(), largo);
    }

    private static String recortar(String valor, int largo) {
        return valor.length() <= largo ? valor : valor.substring(0, largo);
    }

    /** Nombres y referencias: en mayúsculas y con los espacios de dentro reducidos a uno. */
    static String clave(String texto) {
        return texto == null ? "" : texto.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static boolean blanco(String texto) {
        return texto == null || texto.isBlank();
    }
}
