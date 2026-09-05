package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo que el usuario teclea en la pantalla de ajuste.
 *
 * Las filas se identifican por POSICIÓN dentro de la digestión guardada en
 * sesión, no por referencia ni por color: es la misma decisión que ya toma la
 * pantalla de revisión, y por el mismo motivo —dos filas pueden compartir
 * cualquier combinación de datos visibles, y la posición no.
 */
public class AjusteTallerForm {

    /** El cartón y las unidades por caja de una referencia. */
    public static class GrupoEditado {

        private String medidaCaja;
        private Integer unidadesPorCaja;
        /** Las cantidades objetivo de cada fila de color, por destinación. */
        private List<FilaEditada> filas = new ArrayList<>();

        public String getMedidaCaja() { return medidaCaja; }
        public void setMedidaCaja(String medidaCaja) { this.medidaCaja = medidaCaja; }

        public Integer getUnidadesPorCaja() { return unidadesPorCaja; }
        public void setUnidadesPorCaja(Integer unidadesPorCaja) {
            this.unidadesPorCaja = unidadesPorCaja;
        }

        public List<FilaEditada> getFilas() { return filas; }
        public void setFilas(List<FilaEditada> filas) { this.filas = filas; }
    }

    /** Las cantidades objetivo de una fila, por nombre de destinación. */
    public static class FilaEditada {

        private Map<String, Integer> objetivos = new LinkedHashMap<>();

        public Map<String, Integer> getObjetivos() { return objetivos; }
        public void setObjetivos(Map<String, Integer> objetivos) { this.objetivos = objetivos; }
    }

    private List<GrupoEditado> grupos = new ArrayList<>();

    public List<GrupoEditado> getGrupos() { return grupos; }
    public void setGrupos(List<GrupoEditado> grupos) { this.grupos = grupos; }
}
