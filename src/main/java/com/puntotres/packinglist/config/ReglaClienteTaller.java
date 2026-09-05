package com.puntotres.packinglist.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Normas de un cliente para el packing generado desde el taller: cómo se
 * numeran sus cajas, cómo se traduce el sufijo de su número de pedido a una
 * destinación, y qué norma tiene cada destinación.
 */
public class ReglaClienteTaller {

    private NumeracionCajas numeracionCajas = NumeracionCajas.POR_DESTINACION;
    private Map<String, String> sufijosPo = new LinkedHashMap<>();
    private String destinoSinSufijo;
    private Map<String, ReglaDestinoTaller> destinos = new LinkedHashMap<>();

    public NumeracionCajas getNumeracionCajas() {
        return numeracionCajas;
    }

    public void setNumeracionCajas(NumeracionCajas numeracionCajas) {
        this.numeracionCajas = numeracionCajas;
    }

    public Map<String, String> getSufijosPo() {
        return sufijosPo;
    }

    public void setSufijosPo(Map<String, String> sufijosPo) {
        this.sufijosPo = new LinkedHashMap<>();
        sufijosPo.forEach((sufijo, destino) ->
                this.sufijosPo.put(normalizar(sufijo), normalizar(destino)));
    }

    public String getDestinoSinSufijo() {
        return destinoSinSufijo;
    }

    public void setDestinoSinSufijo(String destinoSinSufijo) {
        this.destinoSinSufijo = normalizar(destinoSinSufijo);
    }

    public Map<String, ReglaDestinoTaller> getDestinos() {
        return destinos;
    }

    public void setDestinos(Map<String, ReglaDestinoTaller> destinos) {
        this.destinos = new LinkedHashMap<>();
        destinos.forEach((destino, regla) -> this.destinos.put(normalizar(destino), regla));
    }

    /**
     * La destinación a la que va un pedido según el sufijo de su número:
     * "07704 CH" viaja a China. Un pedido sin sufijo va a la destinación por
     * defecto del cliente (PARIS en AMI).
     *
     * Vacío significa "no lo sé", y quien llama lo convierte en un aviso
     * bloqueante: mandar un bulto a la destinación equivocada es peor que
     * pedirle al usuario que lo diga.
     */
    public Optional<String> destinoDeSufijo(String sufijo) {
        if (sufijo == null || sufijo.isBlank()) {
            return Optional.ofNullable(destinoSinSufijo);
        }
        return Optional.ofNullable(sufijosPo.get(normalizar(sufijo)));
    }

    public Optional<ReglaDestinoTaller> destinoPara(String destino) {
        if (destino == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(destinos.get(normalizar(destino)));
    }

    /** Mayúsculas, sin espacios de sobra y con los múltiples colapsados. */
    static String normalizar(String texto) {
        return texto == null ? null
                : texto.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
