package com.puntotres.packinglist.persistence;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Memoria del programa entre envíos: qué cartón y cuántas unidades por caja
 * usa cada referencia de cada cliente.
 *
 * Solo se guarda lo que un humano ha dado por bueno: una referencia con las
 * unidades por caja sin rellenar NO se memoriza, porque memorizar el valor
 * por defecto lo daría por bueno la próxima vez y el error se propagaría en
 * silencio de un envío al siguiente.
 */
@Service
public class MemoriaReferencias {

    /** Lo que la memoria sabe de una referencia. */
    public record DatosCaja(String medidaCaja, int unidadesPorCaja) {
    }

    private final MemoriaCajaReferenciaRepository repositorio;

    public MemoriaReferencias(MemoriaCajaReferenciaRepository repositorio) {
        this.repositorio = repositorio;
    }

    public Optional<DatosCaja> buscar(String cliente, String referencia) {
        if (cliente == null || referencia == null) {
            return Optional.empty();
        }
        return repositorio.findById(clave(cliente, referencia))
                .map(fila -> new DatosCaja(fila.getMedidaCaja(), fila.getUnidadesPorCaja()));
    }

    /**
     * Guarda o corrige lo aprendido de una referencia. Las llamadas sin
     * unidades por caja se ignoran en silencio: son las referencias que el
     * usuario ha dejado pendientes, y no hay nada que recordar de ellas.
     */
    @Transactional
    public void recordar(String cliente, String referencia,
                         String medidaCaja, Integer unidadesPorCaja) {
        if (cliente == null || referencia == null || medidaCaja == null
                || unidadesPorCaja == null || unidadesPorCaja <= 0) {
            return;
        }
        MemoriaCajaReferenciaId clave = clave(cliente, referencia);
        repositorio.findById(clave).ifPresentOrElse(
                fila -> fila.actualizar(medidaCaja, unidadesPorCaja),
                () -> repositorio.save(new MemoriaCajaReferencia(
                        clave.cliente(), clave.referencia(), medidaCaja, unidadesPorCaja)));
    }

    /**
     * Cliente y referencia en mayúsculas y sin espacios de sobra: la
     * referencia llega tecleada a mano o leída de un excel, y "ull164" y
     * "ULL164 " son la misma.
     */
    private static MemoriaCajaReferenciaId clave(String cliente, String referencia) {
        return new MemoriaCajaReferenciaId(
                cliente.trim().toUpperCase(Locale.ROOT),
                referencia.trim().toUpperCase(Locale.ROOT));
    }
}
