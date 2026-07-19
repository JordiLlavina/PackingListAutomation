package com.puntotres.packinglist.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat limita a 10 el número total de "parts" de una petición
 * multipart/form-data (cuenta tanto los campos normales del formulario como
 * los ficheros), por defecto. El formulario de /importar ya envía 9 campos
 * de texto además de las imágenes, así que con solo 4-5 imágenes se supera
 * el límite y salta FileCountLimitExceededException. Este valor no se
 * puede fijar desde spring.servlet.multipart (esa propiedad solo cubre
 * max-file-size/max-request-size), hay que tocar el conector de Tomcat.
 */
@Configuration
public class TomcatMultipartConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private static final int MAX_PART_COUNT = 100;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers((TomcatConnectorCustomizer) this::setMaxPartCount);
    }

    private void setMaxPartCount(Connector connector) {
        connector.setMaxPartCount(MAX_PART_COUNT);
    }
}
