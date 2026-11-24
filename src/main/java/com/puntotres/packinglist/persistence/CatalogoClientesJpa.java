package com.puntotres.packinglist.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.config.CatalogoClientes;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;

/**
 * El catálogo de clientes que manda en la aplicación arrancada: los
 * programados del yml (AMI, APC) primero, en su orden, y detrás los de
 * plantilla genérica de la tabla {@code cliente}, por nombre.
 *
 * Además da de alta, corrige y elimina los genéricos para la pantalla
 * /clientes. Los programados no se tocan desde aquí: su packing list es código.
 */
@Service
@Primary
public class CatalogoClientesJpa implements CatalogoClientes {

    /** Cómo se dice en pantalla lo que un cliente tiene guardado en cada tabla: singular y plural. */
    private static final Map<String, List<String>> NOMBRE_DE_LA_TABLA = Map.ofEntries(
            Map.entry("TEMPORADA_GUARDADA", List.of("temporada guardada", "temporadas guardadas")),
            Map.entry("MODELO", List.of("modelo del catálogo", "modelos del catálogo")),
            Map.entry("ARTICULO", List.of("artículo del catálogo", "artículos del catálogo")),
            Map.entry("ARTICULO_COLOR", List.of("color de artículo", "colores de artículo")),
            Map.entry("FOTO_MODELO", List.of("foto de modelo", "fotos de modelos")),
            Map.entry("MODELO_TEMPORADA", List.of("modelo en temporada", "modelos en temporadas")),
            Map.entry("COLOR_CLIENTE", List.of("color", "colores")),
            Map.entry("PIEL", List.of("piel", "pieles")),
            Map.entry("HOJA_EMBALAJE", List.of("hoja de info de embalaje", "hojas de info de embalaje")),
            Map.entry("EVENTO_PIEL", List.of("anotación en la biblioteca de pieles",
                    "anotaciones en la biblioteca de pieles")));

    private final ClienteRepository repositorio;
    private final ClientesProperties programados;
    private final JdbcTemplate jdbc;

    public CatalogoClientesJpa(ClienteRepository repositorio, ClientesProperties programados, JdbcTemplate jdbc) {
        this.repositorio = repositorio;
        this.programados = programados;
        this.jdbc = jdbc;
    }

    /** Lo que se teclea en el formulario de /clientes, ya sin espacios sobrantes. */
    public record DatosCliente(String nombre, String nombreLegal, String direccionEntrega,
                               String placeholderTemporada, String etiquetaPedido) {

        public DatosCliente {
            nombre = limpio(nombre);
            nombreLegal = limpio(nombreLegal);
            direccionEntrega = limpio(direccionEntrega);
            placeholderTemporada = limpio(placeholderTemporada);
            etiquetaPedido = limpio(etiquetaPedido);
        }

        private static String limpio(String texto) {
            return texto == null || texto.isBlank() ? null : texto.trim();
        }
    }

    @Override
    public Map<String, ClienteConfig> getClientes() {
        Map<String, ClienteConfig> clientes = new LinkedHashMap<>(programados.getClientes());
        for (Cliente cliente : guardados()) {
            clientes.putIfAbsent(cliente.getClave(), cliente.comoConfig());
        }
        return clientes;
    }

    /** Los de plantilla genérica, por nombre, para la lista de /clientes. */
    public List<Cliente> guardados() {
        return repositorio.findAll().stream()
                .sorted(Comparator.comparing(Cliente::getNombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** AMI y APC: salen en la lista, pero ni se corrigen ni se eliminan desde la pantalla. */
    public boolean esProgramado(String clave) {
        return clave != null && programados.clientePara(clave).isPresent();
    }

    /**
     * Da de alta un cliente de plantilla genérica y devuelve su clave, que es
     * el nombre en mayúsculas.
     *
     * @throws IllegalArgumentException con el motivo, para enseñarlo tal cual
     */
    @Transactional
    public String alta(DatosCliente datos) {
        validar(datos);
        String clave = ClienteConfig.normalizar(datos.nombre());
        if (clave.length() > 60) {
            throw new IllegalArgumentException("El nombre es demasiado largo: como mucho 60 letras.");
        }
        if (getClientes().containsKey(clave)) {
            throw new IllegalArgumentException("Ya hay un cliente que se llama " + datos.nombre() + ".");
        }
        repositorio.save(new Cliente(clave, datos.nombre(), datos.nombreLegal(), datos.direccionEntrega(),
                datos.placeholderTemporada(), datos.etiquetaPedido()));
        return clave;
    }

    /**
     * Corrige los datos de un cliente genérico. La clave no cambia aunque
     * cambie el nombre: es lo que lo une a lo que tiene guardado.
     */
    @Transactional
    public void corregir(String clave, DatosCliente datos) {
        validar(datos);
        Cliente cliente = repositorio.findById(ClienteConfig.normalizar(clave)).orElseThrow(
                () -> new IllegalArgumentException("El cliente " + clave + " ya no existe."));
        String otraClave = ClienteConfig.normalizar(datos.nombre());
        if (!otraClave.equals(cliente.getClave()) && getClientes().containsKey(otraClave)) {
            throw new IllegalArgumentException("Ya hay otro cliente que se llama " + datos.nombre() + ".");
        }
        cliente.actualizar(datos.nombre(), datos.nombreLegal(), datos.direccionEntrega(),
                datos.placeholderTemporada(), datos.etiquetaPedido());
    }

    /**
     * Lo que el cliente tiene guardado, tabla por tabla y solo lo que no está
     * vacío, ya en palabras ("2 temporadas guardadas", "1 color"). Vacío = se
     * puede eliminar.
     *
     * Se buscan TODAS las tablas con una columna {@code cliente}, no una
     * lista fija: una tabla que se añada mañana con datos de cliente queda
     * protegida sin acordarse de tocar esto.
     */
    public List<String> datosGuardados(String clave) {
        String buscada = ClienteConfig.normalizar(clave);
        List<String> tablas = jdbc.queryForList("""
                select c.table_name
                from information_schema.columns c
                join information_schema.tables t
                  on t.table_schema = c.table_schema and t.table_name = c.table_name
                where upper(c.table_schema) = 'PUBLIC'
                  and upper(c.column_name) = 'CLIENTE'
                  and t.table_type = 'BASE TABLE'
                order by c.table_name
                """, String.class);
        List<String> datos = new ArrayList<>();
        for (String tabla : tablas) {
            Long cuantos = jdbc.queryForObject(
                    "select count(*) from " + tabla + " where upper(trim(cliente)) = ?", Long.class, buscada);
            if (cuantos != null && cuantos > 0) {
                String sinNombre = "filas en " + tabla.toLowerCase();
                List<String> nombre = NOMBRE_DE_LA_TABLA.getOrDefault(tabla.toUpperCase(),
                        List.of(sinNombre, sinNombre));
                datos.add(cuantos + " " + nombre.get(cuantos == 1 ? 0 : 1));
            }
        }
        return datos;
    }

    /**
     * Elimina un cliente genérico que no tenga nada guardado. Con algo
     * guardado no se elimina: decisión del usuario (noviembre de 2026), para
     * que un borrado no deje temporadas, modelos o colores sin dueño.
     */
    @Transactional
    public void eliminar(String clave) {
        if (esProgramado(clave)) {
            throw new IllegalArgumentException(clave + " tiene su packing list programado y no se puede eliminar.");
        }
        Cliente cliente = repositorio.findById(ClienteConfig.normalizar(clave)).orElseThrow(
                () -> new IllegalArgumentException("El cliente " + clave + " ya no existe."));
        List<String> datos = datosGuardados(cliente.getClave());
        if (!datos.isEmpty()) {
            throw new IllegalArgumentException(cliente.getNombre() + " no se puede eliminar porque tiene "
                    + describir(datos) + ".");
        }
        repositorio.delete(cliente);
    }

    /** "2 temporadas guardadas, 40 modelos del catálogo y 1 color". */
    public static String describir(List<String> partes) {
        if (partes.size() == 1) {
            return partes.get(0);
        }
        return String.join(", ", partes.subList(0, partes.size() - 1)) + " y " + partes.get(partes.size() - 1);
    }

    private static void validar(DatosCliente datos) {
        if (datos.nombre() == null) {
            throw new IllegalArgumentException("Falta el nombre del cliente.");
        }
        if (datos.nombreLegal() == null) {
            throw new IllegalArgumentException("Falta el nombre legal: es el que sale en el packing list.");
        }
        if (datos.direccionEntrega() == null) {
            throw new IllegalArgumentException("Falta la dirección de entrega: sale en el packing list.");
        }
    }
}
