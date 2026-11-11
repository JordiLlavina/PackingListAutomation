package com.puntotres.packinglist.modelos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de la app de modelos y modificaciones, desde el bloque
 * {@code modelos} de application.yml:
 *
 * <pre>
 * modelos:
 *   url-publica: ${MODELOS_URL_PUBLICA:http://localhost:8080}
 *   tipos-cambio:
 *     - { clave: CONSUMO_PIEL, texto: "Consumo piel", icono: piel }
 *   destinos-aplicacion: [ICSUITE, EXCEL_ESCANDALLOS]
 * </pre>
 *
 * Los secretos (contraseña de administración, claves VAPID, clave de Azure
 * Speech) llegan por variables de entorno y nunca se escriben en el yml. Sin
 * ellas la función correspondiente queda apagada y la aplicación lo dice, no
 * falla: ver {@link #pushConfigurado()} y {@link #transcripcionConfigurada()}.
 *
 * @param urlPublica      dirección con la que se llega a la app desde fuera
 *                        (la del QR de cada modelo)
 * @param adminPassword   contraseña del administrador; vacía = sin definir
 * @param tiposCambio     las razones de un cambio (los botones de la cara del taller), en este orden;
 *                        una modificación puede llevar varias, guardadas en una sola columna
 *                        separadas por comas ({@link #SEPARADOR_TIPOS}, {@link #clavesDe})
 * @param destinosAplicacion dónde aplica un cambio el despacho
 * @param recordatorios   las reglas anti-olvido
 * @param push            claves VAPID del Web Push
 * @param azureSpeech     credenciales de la transcripción de voz
 */
@ConfigurationProperties("modelos")
public record ModelosProperties(
        String urlPublica,
        String adminPassword,
        List<TipoCambio> tiposCambio,
        List<String> destinosAplicacion,
        Recordatorios recordatorios,
        Push push,
        AzureSpeech azureSpeech) {

    /** Un botón de la cara del taller: su clave, el texto que se ve y el icono. */
    public record TipoCambio(String clave, String texto, String icono) {
    }

    /** Entre las claves de una modificación con varias razones ({@code CONSUMO_PIEL,FORNITURAS}). */
    public static final String SEPARADOR_TIPOS = ",";

    /** Entre los textos de varias razones cuando se enseñan juntas. */
    public static final String SEPARADOR_TEXTOS = " · ";

    /** Reglas anti-olvido, por el lado que debe actuar. */
    public record Recordatorios(Taller taller, Despacho despacho) {

        /**
         * @param resumenColaHora hora (0-23) desde la que sale cada día el resumen
         *                        de la cola al taller; negativa o sin poner, no sale
         * @param urgenteDias     cada cuántos días se recuerda al taller una orden
         *                        urgente sin hacer; 0 lo apaga
         */
        public record Taller(Integer resumenColaHora, int urgenteDias) {
        }

        /**
         * @param sinAplicarDias          cada cuántos días se recuerda un aviso sin aplicar del todo
         * @param respuestaPendienteHoras horas hasta avisar de una respuesta pendiente
         */
        public record Despacho(int sinAplicarDias, int respuestaPendienteHoras) {
        }
    }

    /** Claves VAPID del Web Push y el asunto ({@code mailto:}) que las acompaña. */
    public record Push(String vapidPublic, String vapidPrivate, String asunto) {
    }

    /** Credenciales de Azure Speech para transcribir las notas de voz. */
    public record AzureSpeech(String clave, String region) {
    }

    /** El tipo de cambio con esa clave; vacío si no existe o la clave es nula. */
    public Optional<TipoCambio> tipoCambio(String clave) {
        if (clave == null || tiposCambio == null) {
            return Optional.empty();
        }
        return tiposCambio.stream().filter(tipo -> tipo.clave().equals(clave)).findFirst();
    }

    /**
     * Las claves de lo guardado en {@code tipo_cambio} ({@code A,B}), sin
     * espacios ni vacías; una lista vacía si es nulo o está en blanco.
     */
    public static List<String> clavesDe(String claves) {
        List<String> lista = new ArrayList<>();
        if (claves == null) {
            return lista;
        }
        for (String clave : claves.split(SEPARADOR_TIPOS)) {
            String limpia = clave.strip();
            if (!limpia.isEmpty() && !lista.contains(limpia)) {
                lista.add(limpia);
            }
        }
        return lista;
    }

    /**
     * El texto de lo guardado en {@code tipo_cambio}: el de cada clave en el
     * yml (la clave tal cual si ya no está), unidos con {@link #SEPARADOR_TEXTOS}
     * («Consumo piel · Fornituras»); null si no hay ninguna.
     */
    public String textoTiposCambio(String claves) {
        List<String> lista = clavesDe(claves);
        if (lista.isEmpty()) {
            return null;
        }
        return String.join(SEPARADOR_TEXTOS, lista.stream()
                .map(clave -> tipoCambio(clave).map(TipoCambio::texto).orElse(clave))
                .toList());
    }

    /** Hay clave pública y privada: sin las dos no se puede mandar ningún push. */
    public boolean pushConfigurado() {
        return push != null && hayTexto(push.vapidPublic()) && hayTexto(push.vapidPrivate());
    }

    /** Hay clave y región de Azure Speech: sin ellas no se transcribe la voz. */
    public boolean transcripcionConfigurada() {
        return azureSpeech != null && hayTexto(azureSpeech.clave()) && hayTexto(azureSpeech.region());
    }

    /** Dirección pública del modelo: la URL pública sin barra final, {@code /m/} y el id. */
    public String urlDeModelo(long id) {
        String base = urlPublica == null ? "" : urlPublica;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/m/" + id;
    }

    private static boolean hayTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
