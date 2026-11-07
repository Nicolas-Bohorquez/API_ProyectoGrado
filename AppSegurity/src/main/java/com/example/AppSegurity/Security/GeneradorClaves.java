package com.example.AppSegurity.Security;

import java.security.SecureRandom;

/** Claves temporales impredecibles (SecureRandom), sin caracteres que se confunden (0/O, 1/l/I). */
public final class GeneradorClaves {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private GeneradorClaves() {
    }

    /** Clave de 256 bits en el formato exacto que espera Fernet (base64 url-safe de 32 bytes aleatorios, con relleno). */
    public static String claveFernet() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().encodeToString(bytes);
    }

    public static String claveTemporal() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
