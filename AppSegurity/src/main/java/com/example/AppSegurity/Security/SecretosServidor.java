package com.example.AppSegurity.Security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.function.Predicate;

/**
 * Lectura de los secretos del servidor (JWT_SECRET, AES_LLAVE) desde variables de entorno.
 *
 * Antes, si faltaba una variable se usaba una llave escrita en el código (pública en el repositorio): cualquiera podía
 * firmar tokens o descifrar las claves de sesión. Ahora:
 *   - Variable presente y válida  -> se usa.
 *   - Falta o es inválida         -> el servidor NO arranca (el error se ve en el log del despliegue).
 *   - Solo en las PRUEBAS AUTOMÁTICAS (Maven fija la propiedad de sistema app.llaves.desarrollo=true en el pom.xml) se
 *     genera una llave ALEATORIA nueva en cada arranque. No hay ninguna variable de entorno que active ese modo: en
 *     Railway o en cualquier otro equipo el servidor solo arranca con las llaves reales. Nunca hay una llave fija en el código.
 */
final class SecretosServidor {

    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private SecretosServidor() {}

    static boolean modoDesarrollo() {
        return "true".equalsIgnoreCase(System.getProperty("app.llaves.desarrollo"));
    }

    /**
     * @param nombre       nombre de la variable de entorno
     * @param valida       regla de validez (sobre el valor)
     * @param requisito    texto del requisito para el mensaje de error
     * @param largoAleatorio largo de la llave aleatoria en modo desarrollo
     */
    static String resolver(String nombre, Predicate<String> valida, String requisito, int largoAleatorio) {
        String valor = System.getenv(nombre);
        if (valor != null && valida.test(valor)) {
            return valor;
        }
        if (modoDesarrollo()) {
            System.err.println("[SEGURIDAD] " + nombre + " no definida o inválida: PRUEBAS AUTOMÁTICAS, se usa una llave "
                    + "aleatoria válida solo para este arranque.");
            return aleatoria(largoAleatorio);
        }
        throw new IllegalStateException("[SEGURIDAD] La variable de entorno " + nombre + " no está definida o no es válida ("
                + requisito + "). El servidor no arranca sin ella.");
    }

    static int bytesUtf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String aleatoria(int largo) {
        SecureRandom azar = new SecureRandom();
        StringBuilder sb = new StringBuilder(largo);
        for (int i = 0; i < largo; i++) {
            sb.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
