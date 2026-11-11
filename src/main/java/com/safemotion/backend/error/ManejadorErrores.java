package com.safemotion.backend.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Todas las respuestas de error tienen la misma forma: {"status": 400, "error": "mensaje"} (+ "actual" en un 409).
 * Nunca se devuelve la traza ni el mensaje interno de una excepción inesperada.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    static ResponseEntity<Map<String, Object>> cuerpo(HttpStatus status, String mensaje, Object datos) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("status", status.value());
        b.put("error", mensaje);
        if (datos != null) b.put("actual", datos);
        return ResponseEntity.status(status).body(b);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException e) {
        return cuerpo(e.getStatus(), e.getMessage(), e.getDatos());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
        var f = e.getBindingResult().getFieldError();
        return cuerpo(HttpStatus.BAD_REQUEST, f == null ? "Datos inválidos" : f.getField() + ": " + f.getDefaultMessage(), null);
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> parametro(Exception e) {
        return cuerpo(HttpStatus.BAD_REQUEST, "Parámetro inválido", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> jsonInvalido(HttpMessageNotReadableException e) {
        return cuerpo(HttpStatus.BAD_REQUEST, "El cuerpo no es un JSON válido", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> metodo(HttpRequestMethodNotSupportedException e) {
        return cuerpo(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> ruta(NoResourceFoundException e) {
        return cuerpo(HttpStatus.NOT_FOUND, "Ruta no encontrada", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> inesperado(Exception e) {
        log.error("Error inesperado", e);   // la traza queda en el log del servidor, no en la respuesta
        return cuerpo(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", null);
    }
}
