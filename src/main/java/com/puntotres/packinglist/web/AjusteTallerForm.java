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
        /** Lo que pesa una caja LLENA de esta referencia, si se ha pesado. */
        private Double pesoBrutoKg;
        /** Las cantidades objetivo de cada fila de color, por destinación. */
        private List<FilaEditada> filas = new ArrayList<>();

        public String getMedidaCaja() { return medidaCaja; }
        public void setMedidaCaja(String medidaCaja) { this.medidaCaja = medidaCaja; }

        public Integer getUnidadesPorCaja() { return unidadesPorCaja; }
        public void setUnidadesPorCaja(Integer unidadesPorCaja) {
            this.unidadesPorCaja = unidadesPorCaja;
        }

        public Double getPesoBrutoKg() { return pesoBrutoKg; }
        public void setPesoBrutoKg(Double pesoBrutoKg) { this.pesoBrutoKg = pesoBrutoKg; }

        public List<FilaEditada> getFilas() { return filas; }
        public void setFilas(List<FilaEditada> filas) { this.filas = filas; }
    }

    /**
     * Lo que se envía a cada destinación en una fila: cuánto y con qué número
     * de pedido. Los dos mapas van por nombre de destinación y en paralelo,
     * porque son dos campos del mismo dato y la pantalla los pinta juntos.
     */
    public static class FilaEditada {

        private Map<String, Integer> objetivos = new LinkedHashMap<>();
        /** El PO de AMI, el {@code Document d'achat} de APC. */
        private Map<String, String> pedidos = new LinkedHashMap<>();

        public Map<String, Integer> getObjetivos() { return objetivos; }
        public void setObjetivos(Map<String, Integer> objetivos) { this.objetivos = objetivos; }

        public Map<String, String> getPedidos() { return pedidos; }
        public void setPedidos(Map<String, String> pedidos) { this.pedidos = pedidos; }
    }

    private List<GrupoEditado> grupos = new ArrayList<>();

    public List<GrupoEditado> getGrupos() { return grupos; }
    public void setGrupos(List<GrupoEditado> grupos) { this.grupos = grupos; }
}
