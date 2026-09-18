package com.puntotres.packinglist.carrier.dhl;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Monta el RestClient de DHL: base URL, Basic Auth, timeouts, JSON sin nulos y log. */
final class DhlRestClientFactory {

    private DhlRestClientFactory() { }

    static RestClient crear(DhlProperties props, RestClient.Builder builder, ObjectMapper base) {
        // Sin nulos: el esquema de DHL usa additionalProperties: false y minItems
        // en varias listas; un null serializado da 400. Sin fallar por campos
        // desconocidos: las respuestas traen mucho más de lo que se lee.
        ObjectMapper json = base.copy()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(props.getTimeoutSegundos())).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofSeconds(props.getTimeoutSegundos()));
        return builder.clone()
                .baseUrl(props.getBaseUrl())
                .requestFactory(new BufferingClientHttpRequestFactory(fabrica))
                .messageConverters(convertidores -> {
                    convertidores.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    convertidores.add(0, new MappingJackson2HttpMessageConverter(json));
                })
                .requestInterceptor(new DhlLogInterceptor())
                .defaultHeaders(h -> {
                    h.setBasicAuth(props.getApiKey(), props.getApiSecret());
                    h.set("Plugin-Name", "PuntotresPackingList");
                    h.set("Plugin-Version", "1.0");
                })
                .build();
    }
}
