package com.example.AppSegurity.Config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

// Sin esto, un cuerpo JSON invalido (mal codificado, mal formado, etc.) termina en el
// forward por defecto de Spring Boot a "/error", que la configuracion de seguridad bloquea
// (no esta en la lista de rutas publicas) y el cliente recibe un 403 vacio en vez de un 400
// con un mensaje que explique el problema.
@RestControllerAdvice
public class ManejadorErroresGlobal {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> cuerpoInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("exito", false, "mensaje", "La petición no tiene un formato JSON válido."));
    }
}
