package com.puntotres.packinglist.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Una caja de cartón del envío y las líneas del JSON que la describen.
 *
 * Una misma caja puede ocupar varias líneas (varias tallas, colores o
 * referencias dentro del mismo bulto), pero se pesa UNA sola vez: el peso
 * de la caja es el de su <b>línea líder</b> (la primera con ese número de
 * caja) y las demás líneas no aportan peso. Si la líder no trae peso, la
 * caja está pendiente aunque otra línea sí lo traiga.
 *
 * Es la regla que ya usaba el packing list de AMI, aquí en un único sitio
 * para que todos los clientes y ambas salidas (packing y etiquetas) la
 * compartan en vez de reimplementarla.
 */
public record CajaFisica(int numeroCaja, List<CajaData> lineas) {

    public CajaFisica {
        if (lineas == null || lineas.isEmpty()) {
            throw new IllegalArgumentException("Una caja física necesita al menos una línea");
        }
        lineas = List.copyOf(lineas);
    }

    /** Las líneas de una misma caja, en su orden de llegada. */
    public static CajaFisica de(List<CajaData> lineas) {
        return new CajaFisica(lineas.get(0).getNumeroCaja(), lineas);
    }

    /**
     * Agrupa líneas sueltas en cajas físicas por número de caja,
     * conservando el orden de llegada (la primera línea de cada grupo es
     * la líder). Los números de caja se reinician en cada destinación, así
     * que hay que agrupar las cajas de UNA sola destinación.
     */
    public static List<CajaFisica> agrupar(Collection<CajaData> cajas) {
        Map<Integer, List<CajaData>> porNumero = new LinkedHashMap<>();
        for (CajaData caja : cajas) {
            porNumero.computeIfAbsent(caja.getNumeroCaja(), n -> new ArrayList<>()).add(caja);
        }
        return porNumero.values().stream().map(CajaFisica::de).toList();
    }

    /** La línea que porta el peso de la caja entera. */
    public CajaData lider() {
        return lineas.get(0);
    }

    /** Peso bruto de la caja entera; null si su líder no lo trae. */
    public Double pesoBrutoKg() {
        return lider().getPesoBrutoKg();
    }

    /** Peso neto de la caja entera; null si su líder no lo trae. */
    public Double pesoNetoKg() {
        return lider().getPesoNetoKg();
    }

    public boolean tienePesosCompletos() {
        return lider().tienePesosCompletos();
    }

    public Integer numeroPalet() {
        return lider().getNumeroPalet();
    }

    /** Unidades de la caja entera: suma de todas sus líneas. */
    public int unidades() {
        return lineas.stream().mapToInt(CajaData::getCantidad).sum();
    }
}
