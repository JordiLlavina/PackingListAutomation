package com.puntotres.packinglist.shipping.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.puntotres.packinglist.shipping.carrier.CarrierException;
import com.puntotres.packinglist.shipping.service.DatoInvalidoException;
import com.puntotres.packinglist.shipping.service.EnvioNoEncontradoException;
import com.puntotres.packinglist.shipping.service.TransicionIlegalException;

/** Solo para la API: la web traduce las mismas excepciones a mensajes en pantalla. */
@RestControllerAdvice(basePackages = "com.puntotres.packinglist.shipping.api")
public class ApiExceptionHandler {

    @ExceptionHandler(EnvioNoEncontradoException.class)
    public ResponseEntity<ErrorApi> noEncontrado(EnvioNoEncontradoException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorApi.de(e.getMessage())); }

    @ExceptionHandler({TransicionIlegalException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ErrorApi> conflicto(RuntimeException e) { return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorApi.de(e.getMessage())); }

    @ExceptionHandler(DatoInvalidoException.class)
    public ResponseEntity<ErrorApi> invalido(DatoInvalidoException e) { return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorApi.de(e.getMessage())); }

    @ExceptionHandler(CarrierException.class)
    public ResponseEntity<ErrorApi> transportista(CarrierException e) { return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorApi.de(e.getMensajeUsuario())); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorApi> validacion(MethodArgumentNotValidException e) {
        List<String> detalles = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).toList();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorApi("Datos no válidos.", detalles));
    }
}
