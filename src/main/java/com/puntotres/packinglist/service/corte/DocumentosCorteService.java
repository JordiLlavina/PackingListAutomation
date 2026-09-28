package com.puntotres.packinglist.service.corte;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * Los documentos del corte: a qué cliente le toca cada pedido y, a partir de
 * lo tecleado en la pantalla de pieles, los Word que salen.
 */
@Service
public class DocumentosCorteService {

    private final List<ClienteCorte> clientes;

    public DocumentosCorteService(List<ClienteCorte> clientes) {
        this.clientes = List.copyOf(clientes);
    }

    /** El cliente con esa clave, o vacío si todavía no tiene documentos del corte. */
    public Optional<ClienteCorte> clientePara(String clave) {
        String buscada = ReferenciaCorte.normalizar(clave);
        return clientes.stream()
                .filter(cliente -> cliente.clave().equals(buscada))
                .findFirst();
    }
}
