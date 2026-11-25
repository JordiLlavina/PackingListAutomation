package com.puntotres.packinglist.service.produccion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.persistence.Articulo;
import com.puntotres.packinglist.persistence.ArticuloColor;
import com.puntotres.packinglist.persistence.ColorCliente;
import com.puntotres.packinglist.persistence.MemoriaPieles;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.TablaColores;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;
import com.puntotres.packinglist.service.produccion.CatalogoProduccion.PielConColores;
import com.puntotres.packinglist.service.produccion.CatalogoProduccion.PielesModelo;

/**
 * Lo que el formulario de Hojas de Producción puede cargar del catálogo de un
 * cliente: los artículos del desplegable (uno por artículo y color del bolso,
 * como el de la info de embalaje) y las pieles enlazadas a cada modelo.
 *
 * Con una temporada elegida, solo los modelos que van en ella (los vínculos
 * que deja la carga de modelos); sin temporada, todo el catálogo del cliente.
 *
 * <b>Solo lee el catálogo</b>: lo que se imprime lo decide quien rellena el
 * formulario y se guarda en la hoja, nunca de vuelta al catálogo.
 */
@Service
@Transactional(readOnly = true)
public class ArticulosProduccion {

    private final CatalogoModelos catalogo;
    private final MemoriaPieles pieles;
    private final TablaColores colores;
    private final FotosCatalogo fotos;

    public ArticulosProduccion(CatalogoModelos catalogo, MemoriaPieles pieles, TablaColores colores,
                               FotosCatalogo fotos) {
        this.catalogo = catalogo;
        this.pieles = pieles;
        this.colores = colores;
        this.fotos = fotos;
    }

    public CatalogoProduccion de(String cliente, Long temporadaId) {
        if (cliente == null || cliente.isBlank()) {
            return new CatalogoProduccion(List.of(), Map.of());
        }
        List<Modelo> modelos = modelosDe(cliente, temporadaId);
        Map<Long, Long> fotosPorModelo = fotos.principalesDe(modelos.stream().map(Modelo::getId).toList());

        List<ArticuloProduccion> articulos = new ArrayList<>();
        Map<String, PielesModelo> pielesPorModelo = new LinkedHashMap<>();
        for (Modelo modelo : modelos) {
            String codigo = modelo.nombreVisible();
            Long fotoId = fotosPorModelo.get(modelo.getId());
            Map<String, Set<String>> pielesDelModelo = new LinkedHashMap<>();
            Map<String, Set<String>> combinacionesDelModelo = new LinkedHashMap<>();
            for (Articulo articulo : catalogo.articulosDelModelo(modelo.getId())) {
                anadir(cliente, modelo, codigo, fotoId, articulo, articulos, pielesDelModelo, combinacionesDelModelo);
            }
            pielesPorModelo.put(codigo, new PielesModelo(conColores(pielesDelModelo), conColores(combinacionesDelModelo)));
        }
        return new CatalogoProduccion(articulos, pielesPorModelo);
    }

    private List<Modelo> modelosDe(String cliente, Long temporadaId) {
        if (temporadaId == null) {
            return catalogo.modelosDe(cliente);
        }
        List<Modelo> modelos = new ArrayList<>();
        for (Long id : catalogo.idsDeModeloEn(cliente, temporadaId)) {
            catalogo.modelo(id).ifPresent(modelos::add);
        }
        return modelos;
    }

    /**
     * Mete en la lista una entrada por color del artículo (o una sola sin
     * color) y apunta su piel y sus combinaciones, con sus colores, en las
     * pieles del modelo.
     */
    private void anadir(String cliente, Modelo modelo, String codigo, Long fotoId, Articulo articulo,
                        List<ArticuloProduccion> articulos, Map<String, Set<String>> pielesDelModelo,
                        Map<String, Set<String>> combinacionesDelModelo) {
        MemoriaPieles.Recordadas recordadas = pieles.buscar(cliente, articulo.getRefPiel(), articulo.getReferencia());
        String pielBase = !recordadas.nombrePiel().isBlank() ? recordadas.nombrePiel() : articulo.getRefPiel();
        List<String> casillas = articulo.getCasillasCombinacion();
        String primeraCombinacion = articulo.getCombinaciones().isEmpty() ? "" : articulo.getCombinaciones().get(0);
        int posicionPrimera = casillas.indexOf(primeraCombinacion);

        Set<String> coloresPiel = pielBase.isBlank() ? new LinkedHashSet<>()
                : pielesDelModelo.computeIfAbsent(pielBase, k -> new LinkedHashSet<>());
        for (String combinacion : articulo.getCombinaciones()) {
            combinacionesDelModelo.computeIfAbsent(combinacion, k -> new LinkedHashSet<>());
        }

        List<ArticuloColor> delArticulo = catalogo.coloresDe(cliente, articulo.getReferencia());
        if (delArticulo.isEmpty()) {
            articulos.add(new ArticuloProduccion(articulo.getReferencia(), "", "", modelo.getNombre(), codigo, fotoId,
                    pielBase, "", primeraCombinacion, "", articulo.getForro()));
            return;
        }
        for (ArticuloColor color : delArticulo) {
            String descripcion = colores.buscar(cliente, color.getCodigoColor())
                    .map(ColorCliente::getDescripcion).orElse("");
            // Sin color de piel apuntado, el del bolso es lo que se sabe: la
            // descripción de su código en /colores o el propio código.
            String colorBolso = descripcion.isBlank() ? color.getCodigoColor() : descripcion;
            String colorBase = color.getColorPiel().isBlank() ? colorBolso : color.getColorPiel();
            coloresPiel.add(colorBase);

            List<String> coloresCombinacion = color.getColoresCombinacion();
            for (int i = 0; i < casillas.size(); i++) {
                String nombre = casillas.get(i);
                if (!nombre.isBlank() && i < coloresCombinacion.size() && !coloresCombinacion.get(i).isBlank()) {
                    combinacionesDelModelo.computeIfAbsent(nombre, k -> new LinkedHashSet<>()).add(coloresCombinacion.get(i));
                }
            }
            String colorCombinacion = posicionPrimera >= 0 && posicionPrimera < coloresCombinacion.size()
                    ? coloresCombinacion.get(posicionPrimera) : "";

            String forro = articulo.getForro();
            if (!forro.isBlank() && !color.getColorForro().isBlank()) {
                forro = forro + " " + color.getColorForro();
            }
            articulos.add(new ArticuloProduccion(articulo.getReferencia(), color.getCodigoColor(),
                    descripcion.equalsIgnoreCase(color.getCodigoColor()) ? "" : descripcion, modelo.getNombre(),
                    codigo, fotoId, pielBase, colorBase, primeraCombinacion, colorCombinacion,
                    forro.toUpperCase(Locale.ROOT)));
        }
    }

    private static List<PielConColores> conColores(Map<String, Set<String>> porNombre) {
        List<PielConColores> lista = new ArrayList<>();
        porNombre.forEach((nombre, coloresDeLaPiel) -> lista.add(new PielConColores(nombre, List.copyOf(coloresDeLaPiel))));
        return lista;
    }
}
