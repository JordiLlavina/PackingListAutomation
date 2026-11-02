package com.puntotres.packinglist.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Todo lo que lleva impresa una hoja de «Info de embalaje» para el taller.
 *
 * Es el snapshot que se guarda (serializado a JSON) en {@code hoja_embalaje.datos}:
 * lo guardado tiene que ser lo que se mandó, no lo que diga hoy el pedido o la
 * memoria del artículo. La foto del modelo no forma parte: se vuelve a buscar
 * en el catálogo por {@code codigoModelo} al regenerar.
 *
 * Va en {@code model} y no en {@code service.embalaje} para que la capa de
 * persistencia no dependa de los servicios. Los textos nulos se normalizan a
 * vacío para que ni el documento ni el JSON guardado tengan que distinguir
 * «no hay» de «está en blanco».
 */
public record DatosHojaEmbalaje(
        String cliente,
        String nombreCliente,
        String temporada,
        String taller,
        int numeroEnvio,
        String nota,
        LocalDate fecha,
        String referencia,
        String codigoColor,
        String descripcionColor,
        String nombreBolso,
        String codigoModelo,
        String medidaCaja,
        Integer unidadesPorCaja,
        String bolsaTela,
        String etiquetaInterior,
        String instruccionEspecial,
        List<DestinacionHoja> destinaciones,
        String repartoCajas,
        String comentarios,
        String enlaceVideo) {

    /** Una fila de la tabla de destinaciones: a dónde va, con qué pedido y cuántas unidades. */
    public record DestinacionHoja(String destino, String pedido, int cantidad) {
        public DestinacionHoja {
            destino = vacioSiNulo(destino);
            pedido = vacioSiNulo(pedido);
        }
    }

    public DatosHojaEmbalaje {
        cliente = vacioSiNulo(cliente);
        nombreCliente = vacioSiNulo(nombreCliente);
        temporada = vacioSiNulo(temporada);
        taller = vacioSiNulo(taller);
        nota = vacioSiNulo(nota);
        referencia = vacioSiNulo(referencia);
        codigoColor = vacioSiNulo(codigoColor);
        descripcionColor = vacioSiNulo(descripcionColor);
        nombreBolso = vacioSiNulo(nombreBolso);
        codigoModelo = vacioSiNulo(codigoModelo);
        medidaCaja = vacioSiNulo(medidaCaja);
        bolsaTela = vacioSiNulo(bolsaTela);
        etiquetaInterior = vacioSiNulo(etiquetaInterior);
        instruccionEspecial = vacioSiNulo(instruccionEspecial);
        repartoCajas = vacioSiNulo(repartoCajas);
        comentarios = vacioSiNulo(comentarios);
        enlaceVideo = vacioSiNulo(enlaceVideo);
        destinaciones = destinaciones == null ? List.of() : List.copyOf(destinaciones);
    }

    private static String vacioSiNulo(String texto) {
        return texto == null ? "" : texto;
    }

    /** Total de unidades del artículo: la suma de sus destinaciones. */
    public int totalUnidades() {
        return destinaciones.stream().mapToInt(DestinacionHoja::cantidad).sum();
    }

    /** Si hay enlace al vídeo (y por tanto sale el QR). */
    public boolean tieneEnlace() {
        return !enlaceVideo.isBlank();
    }
}
