package com.puntotres.packinglist.model;

import java.util.List;

/**
 * Estructura del JSON de entrada real: un envío completo con todas sus
 * destinaciones. Refleja exactamente la jerarquía del JSON (por eso las
 * clases anidadas), y es el {@code EnvioImportService} quien lo traduce
 * al modelo de dominio (DestinoData/CajaData/PaletData).
 */
public class EnvioInput {

    private String cliente;
    private List<DestinoInput> destinos;

    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }

    public List<DestinoInput> getDestinos() { return destinos; }
    public void setDestinos(List<DestinoInput> destinos) { this.destinos = destinos; }

    public static class DestinoInput {

        private String destino;
        private List<PaletInput> palets;
        private List<ReferenciaInput> referencias;

        public String getDestino() { return destino; }
        public void setDestino(String destino) { this.destino = destino; }

        public List<PaletInput> getPalets() { return palets; }
        public void setPalets(List<PaletInput> palets) { this.palets = palets; }

        public List<ReferenciaInput> getReferencias() { return referencias; }
        public void setReferencias(List<ReferenciaInput> referencias) { this.referencias = referencias; }
    }

    public static class PaletInput {

        private int palet;
        private int cajaInicio;
        private int cajaFin;
        // Opcionales: dimensiones del palet "LxWxH" en cm (p. ej. "80x120x130")
        // y tara del palet en kg. Solo los usan las plantillas que imprimen
        // datos de palet (APC/genérica); sin tara se asume 10 kg.
        private String medidas;
        private Double tara;

        public int getPalet() { return palet; }
        public void setPalet(int palet) { this.palet = palet; }

        public int getCajaInicio() { return cajaInicio; }
        public void setCajaInicio(int cajaInicio) { this.cajaInicio = cajaInicio; }

        public int getCajaFin() { return cajaFin; }
        public void setCajaFin(int cajaFin) { this.cajaFin = cajaFin; }

        public String getMedidas() { return medidas; }
        public void setMedidas(String medidas) { this.medidas = medidas; }

        public Double getTara() { return tara; }
        public void setTara(Double tara) { this.tara = tara; }
    }

    public static class ReferenciaInput {

        private String referencia;
        private String color;
        private String medidaCaja;
        private String pedido;
        private Integer cantidadTotal;
        private List<CajaRangoInput> cajas;
        // Opcionales según cliente: la talla (cinturones AMI, SIZE de APC),
        // el nombre comercial del modelo, el código de livraison de APC y el
        // canal de la línea (columna DESTINATION de APC: WHOLESALE, RETAIL,
        // AUSTRALIA...). Una referencia con varias tallas o canales aparece
        // como varias entradas, igual que ya ocurre con los colores.
        private String talla;
        private String modelo;
        private String livraisonCode;
        private String canal;

        public String getReferencia() { return referencia; }
        public void setReferencia(String referencia) { this.referencia = referencia; }

        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }

        public String getMedidaCaja() { return medidaCaja; }
        public void setMedidaCaja(String medidaCaja) { this.medidaCaja = medidaCaja; }

        public String getPedido() { return pedido; }
        public void setPedido(String pedido) { this.pedido = pedido; }

        public Integer getCantidadTotal() { return cantidadTotal; }
        public void setCantidadTotal(Integer cantidadTotal) { this.cantidadTotal = cantidadTotal; }

        public List<CajaRangoInput> getCajas() { return cajas; }
        public void setCajas(List<CajaRangoInput> cajas) { this.cajas = cajas; }

        public String getTalla() { return talla; }
        public void setTalla(String talla) { this.talla = talla; }

        public String getModelo() { return modelo; }
        public void setModelo(String modelo) { this.modelo = modelo; }

        public String getLivraisonCode() { return livraisonCode; }
        public void setLivraisonCode(String livraisonCode) { this.livraisonCode = livraisonCode; }

        public String getCanal() { return canal; }
        public void setCanal(String canal) { this.canal = canal; }
    }

    /**
     * Una entrada de cajas de la imagen, en cualquiera de sus dos formas:
     * caja suelta {"caja": 31, "unidades": 50} o rango
     * {"cajaInicio": 1, "cajaFin": 30, "unidadesPorCaja": 50}.
     *
     * {@code pesoBruto} (kg) es opcional: el peso bruto de la caja física
     * cuando la imagen del packing list lo indica. Si falta (null) el peso
     * queda pendiente y lo infiere el {@code WeightInferenceService} o lo
     * teclea el humano en la revisión. En un rango el peso aplica a cada una
     * de sus cajas (mismo producto y mismas unidades por caja). El peso es de
     * la caja física entera: en una caja mixta (mismo nº de caja en varias
     * entradas de talla/color) se pone una sola vez, en la primera entrada.
     */
    public static class CajaRangoInput {

        private Integer caja;
        private Integer unidades;
        private Integer cajaInicio;
        private Integer cajaFin;
        private Integer unidadesPorCaja;
        private Double pesoBruto;

        public boolean esRango() { return caja == null; }

        public Integer getCaja() { return caja; }
        public void setCaja(Integer caja) { this.caja = caja; }

        public Integer getUnidades() { return unidades; }
        public void setUnidades(Integer unidades) { this.unidades = unidades; }

        public Integer getCajaInicio() { return cajaInicio; }
        public void setCajaInicio(Integer cajaInicio) { this.cajaInicio = cajaInicio; }

        public Integer getCajaFin() { return cajaFin; }
        public void setCajaFin(Integer cajaFin) { this.cajaFin = cajaFin; }

        public Integer getUnidadesPorCaja() { return unidadesPorCaja; }
        public void setUnidadesPorCaja(Integer unidadesPorCaja) { this.unidadesPorCaja = unidadesPorCaja; }

        public Double getPesoBruto() { return pesoBruto; }
        public void setPesoBruto(Double pesoBruto) { this.pesoBruto = pesoBruto; }
    }
}
