package com.puntotres.packinglist.carrier.dhl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

/**
 * Registra cada petición y respuesta a DHL. A INFO, método, ruta, código y
 * duración; a DEBUG, los cuerpos. Nunca las cabeceras: ahí va Authorization.
 * Los PDF en base64 se sustituyen por su tamaño para no llenar el log.
 * Necesita BufferingClientHttpRequestFactory para poder leer el cuerpo de la
 * respuesta dos veces (aquí y en el cliente).
 */
public class DhlLogInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger("dhl.api");
    private static final Pattern DOCUMENTO = Pattern.compile("\"content\"\\s*:\\s*\"([A-Za-z0-9+/=]{40,})\"");

    @Override
    public ClientHttpResponse intercept(HttpRequest peticion, byte[] cuerpo, ClientHttpRequestExecution ejecucion) throws IOException {
        long inicio = System.nanoTime();
        if (log.isDebugEnabled()) {
            log.debug("→ {} {} {}", peticion.getMethod(), peticion.getURI().getPath(), sinDocumentos(new String(cuerpo, StandardCharsets.UTF_8)));
        }
        ClientHttpResponse respuesta = ejecucion.execute(peticion, cuerpo);
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        log.info("DHL {} {} → {} en {} ms", peticion.getMethod(), peticion.getURI().getPath(), respuesta.getStatusCode().value(), ms);
        if (log.isDebugEnabled()) {
            String texto = StreamUtils.copyToString(respuesta.getBody(), StandardCharsets.UTF_8);
            log.debug("← {}", sinDocumentos(texto));
        }
        return respuesta;
    }

    static String sinDocumentos(String json) {
        Matcher m = DOCUMENTO.matcher(json);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement("\"content\":\"<documento base64, " + m.group(1).length() + " caracteres>\""));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
