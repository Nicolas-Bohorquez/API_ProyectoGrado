package com.example.AppSegurity.Security;

/** Demasiados intentos fallidos: el cliente debe esperar (se responde 429). */
public class IntentosExcedidosException extends RuntimeException {

    private final long milisegundosRestantes;

    public IntentosExcedidosException(String mensaje, long milisegundosRestantes) {
        super(mensaje);
        this.milisegundosRestantes = milisegundosRestantes;
    }

    public long getMilisegundosRestantes() {
        return milisegundosRestantes;
    }
}
