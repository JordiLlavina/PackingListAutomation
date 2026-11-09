package com.puntotres.packinglist.modelos.api;

import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.puntotres.packinglist.modelos.service.FusionPendiente;

/**
 * Los errores de la API de modelos, siempre como {@code {"mensaje": ...}}: la
 * SPA enseña ese texto tal cual, así que tiene que poder leerlo una persona.
 * Solo para los controladores de este paquete: la app vieja sigue con sus
 * páginas de error.
 *
 * Un {@link IllegalArgumentException} es la forma en que los servicios dicen
 * "eso no vale" (un código caducado, un login repetido): es un 400 con su
 * mensaje, no un fallo del programa.
 */
@RestControllerAdvice(basePackages = "com.puntotres.packinglist.modelos.api")
public class ManejadorErroresApi {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErroresApi.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> datoQueNoVale(IllegalArgumentException error) {
        return respuesta(HttpStatus.BAD_REQUEST, error.getMessage(), error);
    }

    /**
     * Asignar una referencia que ya tiene otro modelo es juntar los dos, y eso
     * pide confirmación: un 409 con el mensaje y lo que se juntaría
     * ({@code {"mensaje": ..., "fusion": {...}}}), para que la pantalla lo
     * diga antes de volver a pedirlo confirmado.
     */
    @ExceptionHandler(FusionPendiente.class)
    public ResponseEntity<Map<String, Object>> fusionPendiente(FusionPendiente pendiente) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", pendiente.getMessage(), "fusion", pendiente.fusion()));
    }

    /** Un parámetro de la URL que no se deja convertir: casi siempre un valor de un desplegable que no existe. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> parametroQueNoVale(MethodArgumentTypeMismatchException error) {
        return respuesta(HttpStatus.BAD_REQUEST, "Valor no válido: " + texto(error.getValue()), error);
    }

    /** Un cuerpo JSON que no se entiende o con un valor que no existe ({@code "prioridad": "URGENTE"}). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> cuerpoQueNoVale(HttpMessageNotReadableException error) {
        String mensaje = error.getCause() instanceof InvalidFormatException formato
                ? "Valor no válido: " + texto(formato.getValue())
                : "Valor no válido: no se entiende lo que se ha enviado";
        return respuesta(HttpStatus.BAD_REQUEST, mensaje, error);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> conEstado(ResponseStatusException error) {
        return respuesta(error.getStatusCode(), error.getReason(), error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> sinPermiso(AccessDeniedException error) {
        return respuesta(HttpStatus.FORBIDDEN, "No tienes permiso para esto", error);
    }

    // No corre con multipart: se pasa del límite al leer la petición, antes de elegir controlador, y Tomcat
    // contesta 413 con el cuerpo vacío. El límite de verdad, con mensaje, es Adjuntos.MAX_BYTES (un 400).
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> demasiadoGrande(MaxUploadSizeExceededException error) {
        return respuesta(HttpStatus.PAYLOAD_TOO_LARGE, "El fichero es demasiado grande", error);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> noExiste(NoSuchElementException error) {
        return respuesta(HttpStatus.NOT_FOUND, error.getMessage(), error);
    }

    private static ResponseEntity<Map<String, String>> respuesta(HttpStatusCode estado, String mensaje,
                                                                 Exception error) {
        String texto = mensaje == null || mensaje.isBlank() ? textoPorDefecto(estado) : mensaje;
        if (estado.is5xxServerError()) {
            LOG.warn("Error {} en la API de modelos: {}", estado.value(), texto, error);
        }
        return ResponseEntity.status(estado).body(Map.of("mensaje", texto));
    }

    /** El valor tal como llegó; un parámetro repetido llega como array. */
    private static String texto(Object valor) {
        return valor instanceof Object[] varios ? String.join(", ", Arrays.stream(varios).map(String::valueOf)
                .toList()) : String.valueOf(valor);
    }

    private static String textoPorDefecto(HttpStatusCode estado) {
        if (estado.value() == HttpStatus.NOT_FOUND.value()) {
            return "No existe";
        }
        return estado.is5xxServerError() ? "Algo ha fallado en el servidor" : "La petición no vale";
    }
}
