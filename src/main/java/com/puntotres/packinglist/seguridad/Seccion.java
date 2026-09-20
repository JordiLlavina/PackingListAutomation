package com.puntotres.packinglist.seguridad;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.util.AntPathMatcher;

/**
 * Las partes de la aplicación que se conceden por separado, con las rutas que
 * cuelgan de cada una.
 *
 * Es el ÚNICO sitio donde vive el mapa de rutas a permisos: de aquí salen el
 * interceptor que las protege, las tarjetas que pinta el menú y las casillas
 * de la pantalla de administración. Añadir una sección es una constante.
 *
 * Las rutas del packing list cuelgan de la raíz (/importar, /generar,
 * /revision...) en vez de agruparse bajo /packing-list/. Reordenarlas sería
 * más limpio, pero rompe marcadores y toca todas las plantillas: se declaran
 * una a una y CoberturaDeRutasTest es lo que impide que esta lista se quede
 * coja.
 */
public enum Seccion {

    PACKING_LIST("Packing list y albarán",
            "/packing-list", "/packing-list/**",
            "/importar", "/revision", "/recalcular", "/alternar-fila",
            "/packing-puntotres", "/generar", "/resultados", "/nuevo",
            "/descargar/**", "/descargar-todo", "/descargar-volcado-erp",
            "/descargar-etiquetas/**"),

    ETIQUETAS_ARTICULO("Etiquetas de artículo",
            "/etiquetas-articulo",
            "/etiquetas-articulo-produccion", "/etiquetas-articulo-produccion/**"),

    ESCANDALLOS("Escandallos para ClickUp",
            "/clickup", "/clickup/**"),

    TARAS("Taras de los cartones",
            "/taras"),

    TEMPORADAS("Temporadas y ficheros",
            "/temporadas", "/temporadas/**"),

    ACTIVIDAD("Actividad",
            "/actividad", "/actividad/**"),

    /**
     * No es concedible como las demás: la abre solo quien es administrador.
     * ACTIVIDAD sí lo es, porque tiene sentido que un jefe de producción vea
     * el uso sin poder tocar permisos.
     */
    ADMINISTRACION("Usuarios y permisos",
            "/administracion", "/administracion/**");

    /**
     * Accesibles a cualquiera que haya entrado. Nada es accesible sin entrar:
     * la cadena de seguridad exige identidad para todo lo que no sea un
     * estático.
     */
    private static final List<String> RUTAS_PUBLICAS = List.of(
            "/", "/menu", "/error", "/sin-permiso",
            "/login", "/login/**", "/oauth2/**", "/salir");

    private static final AntPathMatcher COMPARADOR = new AntPathMatcher();

    private final String rotulo;
    private final List<String> rutas;

    Seccion(String rotulo, String... rutas) {
        this.rotulo = rotulo;
        this.rutas = List.of(rutas);
    }

    public String getRotulo() {
        return rotulo;
    }

    public List<String> getRutas() {
        return rutas;
    }

    /** Si esta sección es la dueña de esa URL. */
    public boolean reclama(String uri) {
        return rutas.stream().anyMatch(patron -> COMPARADOR.match(patron, uri));
    }

    /** La sección dueña de una URL, si alguna lo es. */
    public static Optional<Seccion> deRuta(String uri) {
        return Arrays.stream(values()).filter(seccion -> seccion.reclama(uri)).findFirst();
    }

    /** Si esa URL la puede abrir cualquiera que haya entrado. */
    public static boolean esPublica(String uri) {
        return RUTAS_PUBLICAS.stream().anyMatch(patron -> COMPARADOR.match(patron, uri));
    }

    /** Las que se conceden desde la pantalla de administración. */
    public static List<Seccion> concedibles() {
        return Arrays.stream(values()).filter(seccion -> seccion != ADMINISTRACION).toList();
    }
}
