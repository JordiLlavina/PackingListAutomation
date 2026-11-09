package com.puntotres.packinglist.modelos.service.transcripcion;

import java.time.Duration;
import java.util.function.UnaryOperator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.puntotres.packinglist.modelos.ModelosProperties;

/**
 * Elige el transcriptor y el resumidor según lo que esté configurado: el de
 * verdad si hay credenciales, el apagado si no. Sin ellas la aplicación
 * arranca igual y las notas de voz se guardan sin transcripción
 * ({@code NO_APLICA}) o sin resumen; nada falla.
 *
 * <ul>
 * <li>Transcriptor: {@link AzureSpeechTranscriptor} si hay clave y región de
 * Azure Speech ({@link ModelosProperties#transcripcionConfigurada()}).</li>
 * <li>Resumidor: {@link ResumidorClaude} si hay {@code ANTHROPIC_API_KEY}, la
 * misma clave que usan la extracción por imágenes y la conversión del excel
 * del taller.</li>
 * </ul>
 *
 * Se decide al arrancar: poner una clave pide reiniciar. Los tests sustituyen
 * los dos beans por unos falsos ({@code @Primary}).
 */
@Configuration
public class TranscripcionConfig {

    /** Hasta que Azure contesta: una nota de voz de tres minutos tarda unos segundos, esto es de sobra. */
    static final Duration ESPERA_AZURE = Duration.ofMinutes(2);

    private static final Duration ESPERA_CONEXION = Duration.ofSeconds(10);

    @Bean
    public Transcriptor transcriptor(ModelosProperties props, RestClient.Builder builder) {
        if (!props.transcripcionConfigurada()) {
            return new TranscriptorDesactivado();
        }
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(ESPERA_CONEXION);
        fabrica.setReadTimeout(ESPERA_AZURE);
        return new AzureSpeechTranscriptor(builder.requestFactory(fabrica), props);
    }

    @Bean
    public ResumidorModificacion resumidorModificacion(ModelosProperties props) {
        return resumidor(props, System::getenv);
    }

    /** El resumidor según las variables de entorno que se le den; aparte para poder probarlo. */
    static ResumidorModificacion resumidor(ModelosProperties props, UnaryOperator<String> entorno) {
        ResumidorClaude claude = new ResumidorClaude(props, entorno);
        return claude.disponible() ? claude : new ResumidorDesactivado();
    }
}
