package com.puntotres.packinglist.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reglas de la entrada por packing list de taller, desde application.yml:
 *
 * <pre>
 * packing-list:
 *   taller:
 *     altura-palet-por-defecto-cm: 168
 *     clientes:
 *       "[AMI]":
 *         destinos:
 *           "[CHINA]": { altura-max-cm: 158, prioridad: 1, mezcla: NINGUNA }
 * </pre>
 *
 * El packing que manda el taller no se respeta: su numeración de cajas y su
 * reparto en bultos son orientativos, y el programa los regenera con estas
 * normas. Añadir una destinación es una línea de yml; un cliente sin bloque
 * propio tiene destino único, mezcla libre y la altura que teclee el usuario.
 */
@ConfigurationProperties(prefix = "packing-list.taller")
public class ReglasTallerProperties {

    private int alturaPaletPorDefectoCm = 168;
    private int alturaPropiaPaletCm = 11;
    private int posicionesPalet = 4;
    private String medidaCajaPorDefecto = "60x40x40";
    private Map<String, ReglaClienteTaller> clientes = new LinkedHashMap<>();

    public int getAlturaPaletPorDefectoCm() {
        return alturaPaletPorDefectoCm;
    }

    public void setAlturaPaletPorDefectoCm(int alturaPaletPorDefectoCm) {
        this.alturaPaletPorDefectoCm = alturaPaletPorDefectoCm;
    }

    public int getAlturaPropiaPaletCm() {
        return alturaPropiaPaletCm;
    }

    public void setAlturaPropiaPaletCm(int alturaPropiaPaletCm) {
        this.alturaPropiaPaletCm = alturaPropiaPaletCm;
    }

    public int getPosicionesPalet() {
        return posicionesPalet;
    }

    public void setPosicionesPalet(int posicionesPalet) {
        this.posicionesPalet = posicionesPalet;
    }

    public String getMedidaCajaPorDefecto() {
        return medidaCajaPorDefecto;
    }

    public void setMedidaCajaPorDefecto(String medidaCajaPorDefecto) {
        this.medidaCajaPorDefecto = medidaCajaPorDefecto;
    }

    public Map<String, ReglaClienteTaller> getClientes() {
        return clientes;
    }

    public void setClientes(Map<String, ReglaClienteTaller> clientes) {
        this.clientes = new LinkedHashMap<>();
        clientes.forEach((clave, regla) ->
                this.clientes.put(ReglaClienteTaller.normalizar(clave), regla));
    }

    public Optional<ReglaClienteTaller> clienteTaller(String clave) {
        if (clave == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(clientes.get(ReglaClienteTaller.normalizar(clave)));
    }

    /**
     * Altura aprovechable de un palet de esa destinación, ya descontado lo que
     * levanta el palet vacío. Es el límite de cada una de sus pilas.
     *
     * La altura se busca primero en la destinación hija, luego en su padre y
     * por último en el valor por defecto. La que teclea el usuario solo entra
     * cuando el cliente no tiene norma propia: si la tiene, manda ella —el
     * campo ni siquiera se le enseña—, y un valor que llegara igualmente no
     * puede saltarse la norma del cliente.
     */
    public int alturaUtilCm(String clienteClave, String destinoHija, String destinoPadre,
                            Integer alturaTecleadaCm) {
        Optional<ReglaClienteTaller> cliente = clienteTaller(clienteClave);
        Integer maxima = cliente
                .flatMap(regla -> regla.destinoPara(destinoHija)
                        .map(ReglaDestinoTaller::getAlturaMaxCm))
                .orElse(null);
        if (maxima == null) {
            maxima = cliente
                    .flatMap(regla -> regla.destinoPara(destinoPadre)
                            .map(ReglaDestinoTaller::getAlturaMaxCm))
                    .orElse(null);
        }
        if (maxima == null) {
            maxima = cliente.isPresent() || alturaTecleadaCm == null
                    ? alturaPaletPorDefectoCm
                    : alturaTecleadaCm;
        }
        return maxima - alturaPropiaPaletCm;
    }

    /**
     * Orden de servicio cuando no hay género para todos: número menor, antes.
     * Se pregunta por la destinación HIJA, no por su padre: CHINE FRANCH
     * cuelga de WHOLESALE —la última— y sin embargo se sirve de las primeras.
     *
     * Vacío = destinación sin norma. Quien llama lo convierte en bloqueo:
     * repartir a ojo entre destinaciones es lo que no puede pasar.
     */
    public OptionalInt prioridadDe(String clienteClave, String destinoHija) {
        Integer prioridad = clienteTaller(clienteClave)
                .flatMap(regla -> regla.destinoPara(destinoHija))
                .map(ReglaDestinoTaller::getPrioridad)
                .orElse(null);
        return prioridad == null ? OptionalInt.empty() : OptionalInt.of(prioridad);
    }

    /**
     * Qué se puede meter junto en una caja de esa destinación. Se pregunta por
     * la destinación PADRE, que es la que acaba en el packing list y cuyo
     * almacén recibe el bulto. Sin norma, se mezcla libremente.
     */
    public TipoMezcla mezclaDe(String clienteClave, String destinoPadre) {
        return clienteTaller(clienteClave)
                .flatMap(regla -> regla.destinoPara(destinoPadre))
                .map(ReglaDestinoTaller::getMezcla)
                .orElse(TipoMezcla.LIBRE);
    }
}
