package com.puntotres.packinglist.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Todo lo que lleva impresa una Hoja de Producción para un taller externo.
 *
 * Es el snapshot que se guarda (serializado a JSON) en
 * {@code hoja_produccion.datos}: lo guardado tiene que ser lo que se mandó al
 * taller, no lo que diga hoy el catálogo. Va en {@code model} y no en
 * {@code service.produccion} para que la capa de persistencia no dependa de los
 * servicios. Los textos nulos se normalizan a vacío para que ni el excel ni el
 * JSON tengan que distinguir «no hay» de «está en blanco».
 *
 * <b>Una hoja es un modelo</b> (decisión del usuario): el modelo va en la
 * cabecera, con la foto, y cada línea es un color de ese modelo. La foto no
 * viaja en el JSON: se vuelve a buscar en el catálogo por
 * {@link #modeloParaFoto()}, como en la info de embalaje.
 */
public record DatosHojaProduccion(
        String cliente,
        String nombreCliente,
        String temporada,
        String modelo,
        String codigoModelo,
        String nombreBolso,
        String taller,
        int numero,
        LocalDate fechaSalida,
        List<LineaProduccion> lineas) {

    /**
     * Una fila de la tabla: un color del modelo con todo lo que el taller
     * necesita para hacerlo. El hilo va partido en marca, número y cm porque
     * el excel los pinta en líneas distintas de la misma celda; el marcado
     * exterior va en sus tres partes (nombre, color, tamaño: «AMI», «ORO»,
     * «4CM»), que son las tres cajas de texto de la pantalla, y se compone al
     * imprimir ({@link #marcadoExterior()}).
     */
    public record LineaProduccion(
            Integer unidades,
            String pielBase,
            String colorBase,
            String pielCombinacion,
            String colorCombinacion,
            String marcaHilo,
            String numeroHilo,
            String cmHilo,
            String marcadoInterior,
            String marcadoNombre,
            String marcadoColor,
            String marcadoTamano,
            String fornitura,
            String forro,
            String tinta) {

        /** El «no lleva» de los desplegables; también lo que sale cuando el marcado exterior no tiene ninguna parte. */
        public static final String NADA = "-";

        public LineaProduccion {
            pielBase = vacioSiNulo(pielBase);
            colorBase = vacioSiNulo(colorBase);
            pielCombinacion = vacioSiNulo(pielCombinacion);
            colorCombinacion = vacioSiNulo(colorCombinacion);
            marcaHilo = vacioSiNulo(marcaHilo);
            numeroHilo = vacioSiNulo(numeroHilo);
            cmHilo = vacioSiNulo(cmHilo);
            marcadoInterior = vacioSiNulo(marcadoInterior);
            marcadoNombre = vacioSiNulo(marcadoNombre);
            marcadoColor = vacioSiNulo(marcadoColor);
            marcadoTamano = vacioSiNulo(marcadoTamano);
            fornitura = vacioSiNulo(fornitura);
            forro = vacioSiNulo(forro);
            tinta = vacioSiNulo(tinta);
        }

        /**
         * Una línea en la que no se ha escrito nada del bolso: la que se añadió
         * y no se usó. Los marcados, la fornitura y la marca del hilo NO
         * cuentan, porque la pantalla los deja puestos por defecto en cada
         * línea nueva (el nombre del marcado es el cliente, el interior sale
         * del taller): una línea que solo tiene eso no es una instrucción para
         * el taller.
         */
        public boolean estaVacia() {
            return unidades == null && pielBase.isBlank() && colorBase.isBlank()
                    && pielCombinacion.isBlank() && colorCombinacion.isBlank() && numeroHilo.isBlank()
                    && cmHilo.isBlank() && forro.isBlank() && tinta.isBlank();
        }

        /**
         * El marcado exterior tal como se imprime: sus tres partes unidas con un
         * espacio, saltando las vacías y los «-»; sin ninguna, «-». La pantalla
         * aplica la misma regla al teclear ({@code hoja-produccion.js}).
         */
        public String marcadoExterior() {
            return marcadoExterior(marcadoNombre, marcadoColor, marcadoTamano);
        }

        public static String marcadoExterior(String nombre, String color, String tamano) {
            StringBuilder texto = new StringBuilder();
            for (String parte : List.of(limpio(nombre), limpio(color), limpio(tamano))) {
                if (parte.isEmpty() || NADA.equals(parte)) {
                    continue;
                }
                if (texto.length() > 0) {
                    texto.append(' ');
                }
                texto.append(parte);
            }
            return texto.length() == 0 ? NADA : texto.toString();
        }

        /** Los cm del hilo como se imprimen: «3 CM». Si ya se escribió la unidad («3cm») no se repite. */
        public String cmHiloConUnidad() {
            String cm = cmHilo.trim();
            if (cm.isEmpty()) {
                return "";
            }
            return cm.toUpperCase(Locale.ROOT).endsWith("CM") ? cm : cm + " CM";
        }

        /** El hilo en una línea («Gütermann 181 · 3 CM»), para listados; el excel lo parte en tres. */
        public String hilo() {
            String texto = (marcaHilo + " " + numeroHilo).trim();
            String cm = cmHiloConUnidad();
            if (!cm.isEmpty()) {
                texto = texto.isEmpty() ? cm : texto + " · " + cm;
            }
            return texto;
        }

        private static String limpio(String texto) {
            return texto == null ? "" : texto.trim();
        }
    }

    public DatosHojaProduccion {
        cliente = vacioSiNulo(cliente);
        nombreCliente = vacioSiNulo(nombreCliente);
        temporada = vacioSiNulo(temporada);
        modelo = vacioSiNulo(modelo);
        codigoModelo = vacioSiNulo(codigoModelo);
        nombreBolso = vacioSiNulo(nombreBolso);
        taller = vacioSiNulo(taller);
        lineas = lineas == null ? List.of() : List.copyOf(lineas);
    }

    /** Total de unidades de la hoja: la suma de sus líneas con unidades. */
    public int totalUnidades() {
        return lineas.stream().mapToInt(l -> l.unidades() == null ? 0 : l.unidades()).sum();
    }

    /** El código con el que se busca la foto en el catálogo: el del artículo elegido o, si no, el modelo tecleado. */
    public String modeloParaFoto() {
        return !codigoModelo.isBlank() ? codigoModelo : modelo.trim();
    }

    private static String vacioSiNulo(String texto) {
        return texto == null ? "" : texto;
    }
}
