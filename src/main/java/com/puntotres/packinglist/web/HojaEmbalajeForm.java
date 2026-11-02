package com.puntotres.packinglist.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import com.puntotres.packinglist.model.DatosHojaEmbalaje;
import com.puntotres.packinglist.model.DatosHojaEmbalaje.DestinacionHoja;

/**
 * Lo que manda el formulario de «Info de embalaje» al generar una hoja, y la
 * conversión a los {@link DatosHojaEmbalaje} que se imprimen y se guardan.
 *
 * El cliente, su nombre y la temporada NO van aquí: salen de la sesión
 * ({@link EmbalajeEnCurso}), porque son los del pedido cargado y no algo que
 * se teclee; con un campo oculto, cualquiera podría imprimir una hoja de un
 * cliente con el pedido de otro. La fecha tampoco: es la del día en que se
 * genera.
 */
public class HojaEmbalajeForm {

    private String taller;
    private Integer numeroEnvio;
    private String nota;
    private String referencia;
    private String codigoColor;
    private String descripcionColor;
    private String nombreBolso;
    private String codigoModelo;
    /** La foto del catálogo elegida con el artículo; no se guarda en la hoja, solo se imprime. */
    private Long fotoId;
    private String medidaCaja;
    private Integer unidadesPorCaja;
    private String bolsaTela;
    private String etiquetaInterior;
    private String instruccionEspecial;
    private List<Destino> destinos = new ArrayList<>();
    private String repartoCajas;
    private String comentarios;
    private String enlaceVideo;

    /** Una fila de la tabla de destinaciones tal como llega del navegador. */
    public static class Destino {
        private String destino;
        private String pedido;
        private Integer cantidad;

        public Destino() {
        }

        public Destino(String destino, String pedido, Integer cantidad) {
            this.destino = destino;
            this.pedido = pedido;
            this.cantidad = cantidad;
        }

        /**
         * Una fila sin nada escrito: la que queda al pulsar «+ destinación» y
         * no rellenarla. Se mira también el pedido para no tirar en silencio
         * un número que alguien ya había tecleado.
         */
        boolean estaVacia() {
            return vacio(destino) && vacio(pedido) && (cantidad == null || cantidad == 0);
        }

        public String getDestino() { return destino; }
        public void setDestino(String destino) { this.destino = destino; }
        public String getPedido() { return pedido; }
        public void setPedido(String pedido) { this.pedido = pedido; }
        public Integer getCantidad() { return cantidad; }
        public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    }

    /**
     * El formulario con los valores de una hoja ya guardada, para arrancar
     * la pantalla después de «Importar al formulario».
     */
    public static HojaEmbalajeForm desde(DatosHojaEmbalaje datos) {
        HojaEmbalajeForm form = new HojaEmbalajeForm();
        form.taller = datos.taller();
        form.numeroEnvio = datos.numeroEnvio();
        form.nota = datos.nota();
        form.referencia = datos.referencia();
        form.codigoColor = datos.codigoColor();
        form.descripcionColor = datos.descripcionColor();
        form.nombreBolso = datos.nombreBolso();
        form.codigoModelo = datos.codigoModelo();
        form.medidaCaja = datos.medidaCaja();
        form.unidadesPorCaja = datos.unidadesPorCaja();
        form.bolsaTela = datos.bolsaTela();
        form.etiquetaInterior = datos.etiquetaInterior();
        form.instruccionEspecial = datos.instruccionEspecial();
        for (DestinacionHoja d : datos.destinaciones()) {
            form.destinos.add(new Destino(d.destino(), d.pedido(), d.cantidad()));
        }
        form.repartoCajas = datos.repartoCajas();
        form.comentarios = datos.comentarios();
        form.enlaceVideo = datos.enlaceVideo();
        return form;
    }

    /**
     * Los datos de la hoja. Las filas de destinación vacías se descartan (no
     * son un error: es la fila que se añadió y no se usó), una cantidad vacía
     * cuenta como cero y el enlace se recorta, porque un espacio pegado al
     * copiarlo haría un QR que no abre. Un número de envío vacío se resuelve
     * con {@code siguienteNumero}, el contador del archivo para el taller de
     * la hoja (cada taller lleva el suyo): solo se consulta si hace falta.
     */
    public DatosHojaEmbalaje aDatos(EmbalajeEnCurso enCurso, LocalDate hoy, ToIntFunction<String> siguienteNumero) {
        List<DestinacionHoja> destinaciones = new ArrayList<>();
        if (destinos != null) {
            for (Destino d : destinos) {
                if (d == null || d.estaVacia()) {
                    continue;
                }
                destinaciones.add(new DestinacionHoja(recortar(d.destino), recortar(d.pedido),
                        d.cantidad == null ? 0 : d.cantidad));
            }
        }
        return new DatosHojaEmbalaje(enCurso.getCliente(), enCurso.getNombreCliente(), enCurso.getTemporada(),
                recortar(taller), numeroEnvio != null ? numeroEnvio : siguienteNumero.applyAsInt(recortar(taller)),
                recortar(nota), hoy, recortar(referencia), recortar(codigoColor), recortar(descripcionColor),
                recortar(nombreBolso), recortar(codigoModelo), recortar(medidaCaja), unidadesPorCaja,
                recortar(bolsaTela), recortar(etiquetaInterior), recortar(instruccionEspecial),
                destinaciones, sinFinales(repartoCajas), sinFinales(comentarios), recortar(enlaceVideo));
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static String recortar(String texto) {
        return texto == null ? "" : texto.trim();
    }

    /** En los textos de varias líneas se respeta la sangría de dentro; solo sobran los saltos del final. */
    private static String sinFinales(String texto) {
        return texto == null ? "" : texto.stripTrailing();
    }

    public String getTaller() { return taller; }
    public void setTaller(String taller) { this.taller = taller; }
    public Integer getNumeroEnvio() { return numeroEnvio; }
    public void setNumeroEnvio(Integer numeroEnvio) { this.numeroEnvio = numeroEnvio; }
    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public String getCodigoColor() { return codigoColor; }
    public void setCodigoColor(String codigoColor) { this.codigoColor = codigoColor; }
    public String getDescripcionColor() { return descripcionColor; }
    public void setDescripcionColor(String descripcionColor) { this.descripcionColor = descripcionColor; }
    public String getNombreBolso() { return nombreBolso; }
    public void setNombreBolso(String nombreBolso) { this.nombreBolso = nombreBolso; }
    public String getCodigoModelo() { return codigoModelo; }
    public void setCodigoModelo(String codigoModelo) { this.codigoModelo = codigoModelo; }
    public Long getFotoId() { return fotoId; }
    public void setFotoId(Long fotoId) { this.fotoId = fotoId; }
    public String getMedidaCaja() { return medidaCaja; }
    public void setMedidaCaja(String medidaCaja) { this.medidaCaja = medidaCaja; }
    public Integer getUnidadesPorCaja() { return unidadesPorCaja; }
    public void setUnidadesPorCaja(Integer unidadesPorCaja) { this.unidadesPorCaja = unidadesPorCaja; }
    public String getBolsaTela() { return bolsaTela; }
    public void setBolsaTela(String bolsaTela) { this.bolsaTela = bolsaTela; }
    public String getEtiquetaInterior() { return etiquetaInterior; }
    public void setEtiquetaInterior(String etiquetaInterior) { this.etiquetaInterior = etiquetaInterior; }
    public String getInstruccionEspecial() { return instruccionEspecial; }
    public void setInstruccionEspecial(String instruccionEspecial) { this.instruccionEspecial = instruccionEspecial; }
    public List<Destino> getDestinos() { return destinos; }
    public void setDestinos(List<Destino> destinos) { this.destinos = destinos; }
    public String getRepartoCajas() { return repartoCajas; }
    public void setRepartoCajas(String repartoCajas) { this.repartoCajas = repartoCajas; }
    public String getComentarios() { return comentarios; }
    public void setComentarios(String comentarios) { this.comentarios = comentarios; }
    public String getEnlaceVideo() { return enlaceVideo; }
    public void setEnlaceVideo(String enlaceVideo) { this.enlaceVideo = enlaceVideo; }
}
