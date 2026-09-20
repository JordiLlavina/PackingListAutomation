package com.puntotres.packinglist.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Toda ruta de la aplicación tiene dueño: o la reclama exactamente una
 * Seccion, o está declarada como pública.
 *
 * Sin este test, un endpoint nuevo nace sin protección y sin que nada avise,
 * que es la forma más normal de perder una aplicación entera por una feature
 * pequeña. Se lee del mapa real de Spring y no por reflexión sobre las
 * anotaciones, para que cuente también lo que se registre por otros caminos.
 */
@SpringBootTest
class CoberturaDeRutasTest {

    @Autowired
    private RequestMappingHandlerMapping mapeo;

    @Test
    void todaRutaTieneSeccionOEsPublica() {
        List<String> huerfanas = new ArrayList<>();
        for (String ruta : rutasDeLaAplicacion()) {
            if (Seccion.esPublica(ruta)) {
                continue;
            }
            if (Seccion.deRuta(ruta).isEmpty()) {
                huerfanas.add(ruta);
            }
        }
        assertEquals(List.of(), huerfanas,
                "Estas rutas no las reclama ninguna sección ni están declaradas como públicas. "
                        + "Añádelas a la Seccion que les toque, o a Seccion.RUTAS_PUBLICAS si de "
                        + "verdad las puede abrir cualquiera que haya entrado.");
    }

    @Test
    void ningunaRutaLaReclamanDosSecciones() {
        List<String> repetidas = new ArrayList<>();
        for (String ruta : rutasDeLaAplicacion()) {
            long cuantas = java.util.Arrays.stream(Seccion.values())
                    .filter(seccion -> seccion.reclama(ruta))
                    .count();
            if (cuantas > 1) {
                repetidas.add(ruta);
            }
        }
        assertEquals(List.of(), repetidas,
                "Una ruta reclamada por dos secciones da un permiso distinto según el orden del "
                        + "enum, que es un fallo que no se ve mirando el código.");
    }

    @Test
    void ningunaRutaEsALaVezPublicaYDeUnaSeccion() {
        List<String> ambiguas = new ArrayList<>();
        for (String ruta : rutasDeLaAplicacion()) {
            if (Seccion.esPublica(ruta) && Seccion.deRuta(ruta).isPresent()) {
                ambiguas.add(ruta);
            }
        }
        assertEquals(List.of(), ambiguas, "Ser pública gana sobre la sección, así que el permiso "
                + "declarado no se aplicaría y nadie se enteraría.");
    }

    /** Las rutas declaradas por los controladores de la aplicación. */
    private Set<String> rutasDeLaAplicacion() {
        Set<String> rutas = new TreeSet<>();
        mapeo.getHandlerMethods().forEach((info, metodo) -> {
            if (!esNuestro(metodo)) {
                return;
            }
            patronesDe(info).forEach(patron -> rutas.add(aRutaConcreta(patron)));
        });
        return rutas;
    }

    private boolean esNuestro(HandlerMethod metodo) {
        return metodo.getBeanType().getPackageName().startsWith("com.puntotres.packinglist");
    }

    private List<String> patronesDe(RequestMappingInfo info) {
        List<String> patrones = new ArrayList<>();
        Optional.ofNullable(info.getPathPatternsCondition())
                .ifPresent(condicion -> condicion.getPatterns()
                        .forEach(patron -> patrones.add(patron.getPatternString())));
        return patrones;
    }

    /**
     * Convierte el patrón declarado en una URL de ejemplo: las variables de
     * ruta se sustituyen por un valor cualquiera, porque lo que se compara es
     * el camino, no el valor.
     */
    private String aRutaConcreta(String patron) {
        return patron.replaceAll("\\{[^/}]+\\}", "ejemplo");
    }
}
