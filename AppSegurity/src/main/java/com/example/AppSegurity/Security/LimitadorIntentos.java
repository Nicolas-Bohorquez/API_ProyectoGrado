package com.example.AppSegurity.Security;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Limita los intentos fallidos por clave (por ejemplo "login:correo" o "otp:correo") para frenar la fuerza bruta.
 *
 * Tras {@code maxFallos} fallos seguidos dentro de la ventana, esa clave queda bloqueada {@code bloqueoMs}. Un acierto
 * borra el contador. Vive en memoria (una instancia del servidor); los registros viejos se descartan solos.
 */
@Component
public class LimitadorIntentos {

    public static final int MAX_FALLOS_POR_DEFECTO = 5;
    public static final long VENTANA_MS_POR_DEFECTO = 15 * 60 * 1000L;
    public static final long BLOQUEO_MS_POR_DEFECTO = 15 * 60 * 1000L;
    private static final int TOPE_REGISTROS = 20_000;

    private static final class Registro {
        int fallos;
        long primerFallo;
        long bloqueadoHasta;
    }

    private final ConcurrentHashMap<String, Registro> registros = new ConcurrentHashMap<>();
    private final int maxFallos;
    private final long ventanaMs;
    private final long bloqueoMs;
    private volatile java.util.function.LongSupplier reloj = System::currentTimeMillis;

    public LimitadorIntentos() {
        this(MAX_FALLOS_POR_DEFECTO, VENTANA_MS_POR_DEFECTO, BLOQUEO_MS_POR_DEFECTO);
    }

    public LimitadorIntentos(int maxFallos, long ventanaMs, long bloqueoMs) {
        this.maxFallos = maxFallos;
        this.ventanaMs = ventanaMs;
        this.bloqueoMs = bloqueoMs;
    }

    /** Solo para pruebas: sustituye el reloj. */
    public void usarReloj(java.util.function.LongSupplier nuevo) {
        this.reloj = nuevo;
    }

    private static String normalizar(String clave) {
        return clave == null ? "" : clave.trim().toLowerCase();
    }

    /** Lanza IntentosExcedidosException si la clave está bloqueada. */
    public void verificar(String clave) {
        Registro r = registros.get(normalizar(clave));
        if (r == null) return;
        long ahora = reloj.getAsLong();
        synchronized (r) {
            if (r.bloqueadoHasta > ahora) {
                long minutos = Math.max(1, (r.bloqueadoHasta - ahora + 59_999) / 60_000);
                throw new IntentosExcedidosException(
                        "Demasiados intentos fallidos. Intenta de nuevo en " + minutos + " minuto(s).", r.bloqueadoHasta - ahora);
            }
        }
    }

    public void registrarFallo(String clave) {
        if (registros.size() > TOPE_REGISTROS) {
            purgar();
        }
        long ahora = reloj.getAsLong();
        Registro r = registros.computeIfAbsent(normalizar(clave), k -> new Registro());
        synchronized (r) {
            if (r.fallos == 0 || ahora - r.primerFallo > ventanaMs) {
                r.fallos = 0;
                r.primerFallo = ahora;
            }
            r.fallos++;
            if (r.fallos >= maxFallos) {
                r.bloqueadoHasta = ahora + bloqueoMs;
                r.fallos = 0;
            }
        }
    }

    public void registrarExito(String clave) {
        registros.remove(normalizar(clave));
    }

    private void purgar() {
        long ahora = reloj.getAsLong();
        registros.entrySet().removeIf(e -> {
            Registro r = e.getValue();
            synchronized (r) {
                return r.bloqueadoHasta < ahora && ahora - r.primerFallo > ventanaMs;
            }
        });
    }

    public int registrosActivos() {
        return registros.size();
    }
}
