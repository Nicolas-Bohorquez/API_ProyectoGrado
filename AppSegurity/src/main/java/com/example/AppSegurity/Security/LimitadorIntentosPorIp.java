package com.example.AppSegurity.Security;

import org.springframework.stereotype.Component;

/**
 * Frena el "credential spraying": un atacante que prueba UNA clave contra MUCHAS cuentas distintas desde la misma IP
 * nunca dispara el {@link LimitadorIntentos} por cuenta (cada cuenta solo acumula 1 de sus 5 intentos). Este limitador
 * cuenta los fallos por IP en vez de por correo.
 *
 * El umbral es deliberadamente alto (muy por encima del de {@link LimitadorIntentos}) porque varias cuentas legítimas
 * pueden compartir la misma IP pública (wifi de la universidad detrás de un NAT): un grupo de estudiantes equivocando
 * la clave ocasionalmente no debe bloquear a todo el salón. Lo que SÍ debe disparar esto es un volumen de fallos que
 * no se explica por errores humanos normales.
 *
 * Es una clase separada (no una segunda instancia de {@link LimitadorIntentos}) para no introducir ambigüedad en el
 * autowiring por tipo de los lugares que ya inyectan {@link LimitadorIntentos} sin calificador.
 */
@Component
public class LimitadorIntentosPorIp {

    public static final int MAX_FALLOS_POR_DEFECTO = 30;
    public static final long VENTANA_MS_POR_DEFECTO = 10 * 60 * 1000L;   // 10 minutos
    public static final long BLOQUEO_MS_POR_DEFECTO = 15 * 60 * 1000L;   // 15 minutos (igual al bloqueo por cuenta)

    private final LimitadorIntentos interno;

    public LimitadorIntentosPorIp() {
        this(MAX_FALLOS_POR_DEFECTO, VENTANA_MS_POR_DEFECTO, BLOQUEO_MS_POR_DEFECTO);
    }

    public LimitadorIntentosPorIp(int maxFallos, long ventanaMs, long bloqueoMs) {
        this.interno = new LimitadorIntentos(maxFallos, ventanaMs, bloqueoMs);
    }

    /** Solo para pruebas: sustituye el reloj. */
    public void usarReloj(java.util.function.LongSupplier nuevo) {
        interno.usarReloj(nuevo);
    }

    public void verificar(String ip) {
        interno.verificar(clave(ip));
    }

    public void registrarFallo(String ip) {
        interno.registrarFallo(clave(ip));
    }

    public void registrarExito(String ip) {
        interno.registrarExito(clave(ip));
    }

    public int registrosActivos() {
        return interno.registrosActivos();
    }

    private static String clave(String ip) {
        return "ip:" + (ip == null || ip.isBlank() ? "desconocida" : ip);
    }
}
