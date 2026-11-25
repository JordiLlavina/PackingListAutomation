package com.puntotres.packinglist.service.cargamodelos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.CatalogoTaras;
import com.puntotres.packinglist.config.CatalogoClientes;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.Etapa;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.ColorEnCarga;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.EstadoModelo;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.FotoEnCarga;
import com.puntotres.packinglist.service.cargamodelos.ModeloEnCarga.PielEnCarga;

/**
 * El lote tal como lo pinta la batería: lo que viaja en JSON a la pantalla.
 * Aquí se decide el orden de la lista (los que ya existen y no traen nada
 * nuevo, al final) y se calcula, para cada modelo, qué le falta y qué trae.
 */
@Service
public class VistasCarga {

    public record Opcion(String valor, String texto) {
    }

    public record VistaCliente(String clave, String nombre, boolean parteReferencias, List<Opcion> temporadas) {
    }

    public record Progreso(int total, int hechas, boolean terminadas) {
    }

    public record Cuentas(long aceptados, long pendientes, long cancelados) {
    }

    public record VistaColor(String clave, String codigo, String descripcion, boolean existe, String colorPiel,
                             String colorForro, List<String> coloresCombinacion) {
    }

    public record VistaPiel(String referencia, String refPiel, boolean existe, String nombrePiel, String forro,
                            List<String> combinaciones, String medidaCaja, String unidadesPorCaja,
                            String pesoNetoUnidadKg, List<VistaColor> colores) {
    }

    /** {@code fotoCatalogoId}: solo las ya guardadas, que se piden al catálogo y no al lote. */
    public record VistaFoto(String id, String nombre, String origen, boolean seleccionada, boolean guardada,
                            Long fotoCatalogoId, boolean lista, boolean ilegible) {
    }

    public record VistaFicha(String id, String nombre) {
    }

    /**
     * @param gris              ya existe y no trae nada nuevo
     * @param identidadEditable se pueden cambiar la referencia y la de prototipo
     * @param clienteEditable   se puede cambiar el cliente (solo en «Añadir modelo»)
     * @param faltan            lo que impide aceptarlo; vacío = completo
     * @param novedades         lo que gana un modelo que ya existe
     */
    public record VistaModelo(int id, String estado, boolean existe, boolean gris, boolean identidadEditable,
                              boolean clienteEditable, String cliente, String visible, String codigo,
                              String nombrePrototipo, Long temporadaId, String nombre, String tipo, String etapa,
                              List<String> faltan, List<String> novedades, boolean sinFotos,
                              List<VistaPiel> pieles, List<VistaFoto> fotos, List<VistaFicha> fichas) {
    }

    public record VistaLote(String origen, String cliente, List<VistaModelo> modelos, List<String> avisos,
                            Progreso fotos, Cuentas cuentas, Map<String, VistaCliente> clientes,
                            List<Opcion> etapas, List<String> cartones) {
    }

    private final ConstructorLote constructor;
    private final ArchivoTemporadas temporadas;
    private final CatalogoClientes clientes;
    private final CatalogoTaras taras;

    public VistasCarga(ConstructorLote constructor, ArchivoTemporadas temporadas, CatalogoClientes clientes,
                       CatalogoTaras taras) {
        this.constructor = constructor;
        this.temporadas = temporadas;
        this.clientes = clientes;
        this.taras = taras;
    }

    public VistaLote de(LoteCarga lote) {
        List<VistaModelo> normales = new ArrayList<>();
        List<VistaModelo> grises = new ArrayList<>();
        for (ModeloEnCarga modelo : lote.modelos) {
            VistaModelo vista = de(lote, modelo);
            (vista.gris() ? grises : normales).add(vista);
        }
        normales.addAll(grises);
        // En «Añadir modelo» se puede elegir cualquier cliente; en lo demás solo cuenta el del lote.
        Map<String, VistaCliente> losClientes = new LinkedHashMap<>();
        clientes.getClientes().forEach((clave, config) -> {
            if (lote.origen == OrigenCarga.MANUAL || clave.equals(lote.cliente)) {
                losClientes.put(clave, new VistaCliente(clave, config.getNombre(),
                        constructor.parteReferencias(clave),
                        temporadas.de(clave).stream()
                                .map(temporada -> new Opcion(String.valueOf(temporada.id()), temporada.temporada()))
                                .toList()));
            }
        });
        List<Opcion> etapas = new ArrayList<>();
        for (Etapa etapa : Etapa.values()) {
            etapas.add(new Opcion(etapa.name(), etapa.texto()));
        }
        return new VistaLote(lote.origen.name(), lote.cliente, normales, List.copyOf(lote.avisos), progreso(lote),
                cuentas(lote), losClientes, etapas, taras.tamanosDeMayorAMenor());
    }

    public Progreso progreso(LoteCarga lote) {
        return new Progreso(lote.fotosTotal(), lote.fotosHechas(), lote.fotosListas());
    }

    public Cuentas cuentas(LoteCarga lote) {
        return new Cuentas(lote.cuantos(EstadoModelo.ACEPTADO), lote.cuantos(EstadoModelo.PENDIENTE),
                lote.cuantos(EstadoModelo.CANCELADO));
    }

    public VistaModelo de(LoteCarga lote, ModeloEnCarga modelo) {
        List<String> novedades = constructor.novedades(modelo);
        List<VistaPiel> pieles = new ArrayList<>();
        // Un prototipo sin referencia todavía no tiene artículos: no se le enseñan pieles.
        if (!modelo.codigo.isEmpty()) {
            for (PielEnCarga piel : modelo.pieles) {
                List<VistaColor> colores = new ArrayList<>();
                for (ColorEnCarga color : piel.colores) {
                    colores.add(new VistaColor(color.clave, color.codigo, color.descripcion, color.existe,
                            color.colorPiel, color.colorForro, List.copyOf(color.coloresCombinacion)));
                }
                pieles.add(new VistaPiel(piel.referencia, piel.refPiel, piel.existe, piel.nombrePiel, piel.forro,
                        List.copyOf(piel.combinaciones), piel.medidaCaja, piel.unidadesPorCaja, piel.pesoNetoUnidadKg,
                        colores));
            }
        }
        List<VistaFoto> fotos = new ArrayList<>();
        for (FotoEnCarga foto : modelo.fotos) {
            fotos.add(new VistaFoto(foto.id, foto.nombre, foto.origen.name(), foto.seleccionada,
                    foto.origen == FotoEnCarga.Origen.GUARDADA, foto.fotoCatalogoId, foto.lista(), foto.ilegible()));
        }
        List<VistaFicha> fichas = modelo.fichas.stream().map(ficha -> new VistaFicha(ficha.id, ficha.nombre)).toList();
        boolean identidadEditable = !modelo.existe() || modelo.identidadLibre;
        return new VistaModelo(modelo.id, modelo.estado.name(), modelo.existe(),
                modelo.existe() && novedades.isEmpty(), identidadEditable,
                lote.origen == OrigenCarga.MANUAL && identidadEditable, modelo.cliente, modelo.nombreVisible(),
                modelo.codigo, modelo.nombrePrototipo, modelo.temporadaId, modelo.nombre, modelo.tipo.name(),
                modelo.etapa.name(), constructor.faltan(lote, modelo), novedades, modelo.sinFotos(), pieles, fotos,
                fichas);
    }
}
