package com.example.AppSegurity.Services;

import org.springframework.stereotype.Service;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;

@Service
public class FileStorageService {

    private final String BASE_DIR = System.getProperty("user.home") + "/Documents/DataSupervision/Examenes/";

    private String sanitizarSegmento(String segmento) {
        if (segmento == null || segmento.isBlank()) {
            return "general";
        }
        return segmento.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    public String guardarEvidenciaBase64(String base64String, String sesionId, String prefijo, String nombreEstudiante, String materiaCodigo) {
        if (base64String == null || base64String.isEmpty()) {
            return null;
        }

        try {
            // Eliminar cabecera si existe (ej. data:image/webp;base64,...)
            if (base64String.contains(",")) {
                base64String = base64String.split(",")[1];
            }

            byte[] decodedBytes;
            // Si el string empieza con gAAAAA, es un token de Fernet AES-256 (texto plano cifrado E2EE)
            if (base64String.startsWith("gAAAAA")) {
                decodedBytes = base64String.getBytes();
            } else {
                // Decodificar imágenes viejas no encriptadas
                decodedBytes = Base64.getDecoder().decode(base64String);
            }

            String sMateria = sanitizarSegmento(materiaCodigo);
            String sEstudiante = sanitizarSegmento(nombreEstudiante);
            String sSesion = sanitizarSegmento(sesionId);
            String sPrefijo = sanitizarSegmento(prefijo);

            // Crear jerarquia de carpetas segura: BASE_DIR/materia/estudiante/sesionId/prefijo/
            String pathJerarquia = sMateria + "/" + sEstudiante + "/" + sSesion + "/" + sPrefijo + "/";
            Path baseDirPath = Paths.get(BASE_DIR).toAbsolutePath().normalize();
            Path destinoDir = baseDirPath.resolve(pathJerarquia).normalize();

            // Blindaje contra Path Traversal
            if (!destinoDir.startsWith(baseDirPath)) {
                System.err.println("[SEGURIDAD] Intento de path traversal bloqueado: " + pathJerarquia);
                return null;
            }

            File directorio = destinoDir.toFile();
            if (!directorio.exists()) {
                directorio.mkdirs();
            }

            // Generar nombre único con extensión dinámica
            String extension = "audio".equalsIgnoreCase(sPrefijo) ? ".wav" : ".webp";
            String fileName = sPrefijo + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
            File archivo = new File(directorio, fileName);

            // Verificar que el archivo final permanezca estrictamente dentro de baseDir
            if (!archivo.toPath().normalize().startsWith(baseDirPath)) {
                System.err.println("[SEGURIDAD] Archivo fuera de baseDir bloqueado: " + fileName);
                return null;
            }

            try (FileOutputStream fos = new FileOutputStream(archivo)) {
                fos.write(decodedBytes);
            }

            return pathJerarquia + fileName;

        } catch (Exception e) {
            System.err.println("Error al guardar la evidencia: " + e.getMessage());
            return null;
        }
    }
}
