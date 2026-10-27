package com.puntotres.packinglist.service.corte;

import java.util.Map;
import java.util.Set;

/**
 * Un cliente sin documentos del corte propios (los genéricos del catálogo):
 * solo puede entrar por el excel de producción de Puntotres, que ya trae la
 * piel y el color escritos. Su pedido no se sabe leer, y su referencia no se
 * sabe partir: se queda entera como modelo y sin piel, y la carpeta de fotos
 * se tiene que llamar como ella.
 *
 * No es un bean a propósito: si lo fuera entraría en la lista de clientes de
 * {@link DocumentosCorteService} y el pedido de cualquier cliente se daría
 * por legible.
 */
public class ClienteCorteGenerico implements ClienteCorte {

    private static final String SIN_PEDIDO =
            "este cliente todavía no tiene lector de excel de pedido: usa el excel de producción";

    private final String clave;

    public ClienteCorteGenerico(String clave) {
        this.clave = ReferenciaCorte.normalizar(clave);
    }

    @Override
    public String clave() {
        return clave;
    }

    @Override
    public PedidoCorte leerPedido(byte[] excel) {
        throw new IllegalArgumentException(SIN_PEDIDO);
    }

    @Override
    public Map<String, String> coloresDelPedido(byte[] excel) {
        throw new IllegalArgumentException(SIN_PEDIDO);
    }

    @Override
    public Set<String> pielesDelPedido(byte[] excel) {
        throw new IllegalArgumentException(SIN_PEDIDO);
    }

    @Override
    public ReferenciaCorte partir(String referencia) {
        String entera = ReferenciaCorte.normalizar(referencia);
        return new ReferenciaCorte(entera, entera, "");
    }
}
