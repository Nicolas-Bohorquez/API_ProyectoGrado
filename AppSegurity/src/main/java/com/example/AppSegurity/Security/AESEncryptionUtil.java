package com.example.AppSegurity.Security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utilidad de cifrado autenticado AES-GCM (256/128 bits) con vector de inicialización aleatorio (IV).
 *
 * Ya no hay llave de contingencia escrita en el código ni descifrado de respaldo en modo ECB (inseguro): un dato que no
 * pasa la verificación GCM se rechaza. Lo único cifrado con esta clase son las claves de sesión; las que se cifraron con
 * una AES_LLAVE anterior ya no se podían leer tras rotarla, y SesionSupervision.getClaveSesion() las trata como "sin clave".
 */
public class AESEncryptionUtil {

    // AES_LLAVE: 16, 24 o 32 BYTES (no caracteres). Sin ella el servidor no arranca. Ver SecretosServidor.
    private static final String LLAVE_AES = SecretosServidor.resolver("AES_LLAVE",
            s -> { int n = SecretosServidor.bytesUtf8(s); return n == 16 || n == 24 || n == 32; },
            "exactamente 16, 24 o 32 bytes", 32);
    private static final int GCM_IV_LENGTH = 12; // 96 bits estándar NIST para GCM
    private static final int GCM_TAG_LENGTH = 128; // 128 bits de autenticación de integridad

    /** Fuerza la validación de la llave al arrancar (fail-fast) en vez de en el primer examen. */
    public static void verificarConfiguracion() {
        if (LLAVE_AES == null) throw new IllegalStateException("AES_LLAVE no configurada");
    }

    public static String encriptarDatoSensible(String dato) {
        if (dato == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            SecretKeySpec secretKey = new SecretKeySpec(LLAVE_AES.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherText = cipher.doFinal(dato.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Error cifrando los datos sensibles: " + e.getMessage(), e);
        }
    }

    public static String desencriptarDatoSensible(String datoEncriptado) {
        if (datoEncriptado == null || datoEncriptado.isBlank()) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(datoEncriptado);

            // Formato único AES-GCM: IV 12 bytes + Ciphertext + Tag 16 bytes
            if (decoded.length <= GCM_IV_LENGTH + 16) {
                throw new IllegalArgumentException("Dato cifrado demasiado corto o con formato desconocido");
            }
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            SecretKeySpec secretKey = new SecretKeySpec(LLAVE_AES.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Error descifrando los datos sensibles: " + e.getMessage(), e);
        }
    }
}
