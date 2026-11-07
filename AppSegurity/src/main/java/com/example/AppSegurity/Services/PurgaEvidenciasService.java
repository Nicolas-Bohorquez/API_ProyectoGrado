package com.example.AppSegurity.Services;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Retención de evidencias (Ley 1581 de 2012: los datos personales se conservan solo el tiempo necesario para su finalidad).
 *
 * Cada día borra las evidencias (fotos, capturas, audio) con más de {@code app.evidencias.retencion-dias} días. Por defecto
 * son 180; con 0 se desactiva. Solo toca archivos NORMALES dentro de la carpeta de evidencias (nunca sigue enlaces
 * simbólicos ni sale de ella) y retira las carpetas que quedan vacías.
 */
@Service
public class PurgaEvidenciasService {

    public static final int DIAS_POR_DEFECTO = 180;
    private static final int DIAS_MINIMOS = 1;          // un valor absurdo (p. ej. 0,0001) nunca borra lo de hoy

    private final Path base;
    private final int retencionDias;

    @Autowired
    public PurgaEvidenciasService(@Value("${app.evidencias.retencion-dias:" + DIAS_POR_DEFECTO + "}") int retencionDias) {
        this(Paths.get(System.getProperty("user.home"), "Documents", "DataSupervision", "Examenes"), retencionDias);
    }

    /** Constructor para pruebas: carpeta base y plazo explícitos. */
    public PurgaEvidenciasService(Path base, int retencionDias) {
        this.base = base.toAbsolutePath().normalize();
        this.retencionDias = retencionDias;
    }

    @Scheduled(cron = "${app.evidencias.purga-cron:0 30 3 * * *}")
    public void purgarProgramada() {
        if (retencionDias <= 0) {
            return;                                     // desactivada
        }
        int borrados = purgar(Instant.now().minus(Duration.ofDays(Math.max(retencionDias, DIAS_MINIMOS))));
        if (borrados > 0) {
            System.out.println("[RETENCIÓN] Evidencias eliminadas por superar " + retencionDias + " días: " + borrados);
        }
    }

    /** Borra los archivos modificados antes de {@code limite}. Devuelve cuántos archivos eliminó. */
    public int purgar(Instant limite) {
        if (!Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        int borrados = recorrer(base, limite);
        return borrados;
    }

    private int recorrer(Path carpeta, Instant limite) {
        int borrados = 0;
        try (DirectoryStream<Path> hijos = Files.newDirectoryStream(carpeta)) {
            for (Path hijo : hijos) {
                try {
                    BasicFileAttributes atributos = Files.readAttributes(hijo, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (atributos.isSymbolicLink() || atributos.isOther()) {
                        continue;                       // nunca se sigue un enlace ni se toca algo raro
                    }
                    if (atributos.isDirectory()) {
                        borrados += recorrer(hijo, limite);
                        if (!hijo.equals(base) && estaVacia(hijo)) {
                            Files.deleteIfExists(hijo);
                        }
                    } else if (atributos.isRegularFile() && atributos.lastModifiedTime().toInstant().isBefore(limite)) {
                        if (hijo.toAbsolutePath().normalize().startsWith(base)) {
                            Files.deleteIfExists(hijo);
                            borrados++;
                        }
                    }
                } catch (IOException e) {
                    System.err.println("[RETENCIÓN] No se pudo procesar " + hijo + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[RETENCIÓN] No se pudo leer " + carpeta + ": " + e.getMessage());
        }
        return borrados;
    }

    private boolean estaVacia(Path carpeta) throws IOException {
        try (DirectoryStream<Path> hijos = Files.newDirectoryStream(carpeta)) {
            return !hijos.iterator().hasNext();
        }
    }
}
