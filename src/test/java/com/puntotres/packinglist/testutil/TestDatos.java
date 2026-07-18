package com.puntotres.packinglist.testutil;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.PaletData;

/**
 * Factory methods compartidos por los tests de servicio, para no repetir la
 * construcción a mano de CajaData/PaletData en cada clase de test.
 */
public final class TestDatos {

    private TestDatos() {
    }

    public static CajaData caja(int numero, String pedido, String referencia, String color,
                                 int cantidad, Double neto, Double bruto) {
        CajaData caja = new CajaData();
        caja.setNumeroCaja(numero);
        caja.setNumeroPedido(pedido);
        caja.setReferencia(referencia);
        caja.setCodigoColor(color);
        caja.setTamanoCaja("60x40x40");
        caja.setCantidad(cantidad);
        caja.setPesoNetoKg(neto);
        caja.setPesoBrutoKg(bruto);
        return caja;
    }

    public static PaletData palet(String destino, int numero, int inicio, int fin) {
        PaletData palet = new PaletData();
        palet.setDestino(destino);
        palet.setNumeroPalet(numero);
        palet.setCajaInicio(inicio);
        palet.setCajaFin(fin);
        return palet;
    }
}
