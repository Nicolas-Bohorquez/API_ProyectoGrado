package com.example.AppSegurity.Security;

/**
 * Excepción de negocio con un mensaje pensado para mostrarse al usuario/cliente.
 * Permite a los controladores distinguir errores de validación o reglas de dominio
 * de fallos técnicos inesperados (MongoDB, NPE, E/S), evitando filtrar detalles internos.
 */
public class ErrorNegocioException extends RuntimeException {
    public ErrorNegocioException(String mensaje) {
        super(mensaje);
    }
}
