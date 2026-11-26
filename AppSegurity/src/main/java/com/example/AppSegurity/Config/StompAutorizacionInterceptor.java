package com.example.AppSegurity.Config;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.example.AppSegurity.Security.AutorizacionService;

/**
 * Autoriza cada mensaje STOMP que envía un cliente (conectar, suscribirse, publicar).
 *
 * Antes cualquier usuario autenticado —incluido cualquier estudiante— podía suscribirse al video de otra sesión o a las
 * alertas de otro examen, y publicar comandos (incluido FRAUDE, que anula una sesión). Ahora cada destino tiene dueño:
 *
 *   SUBSCRIBE /topic/alertas/{examen}   docente dueño del examen (o administrador)
 *   SUBSCRIBE /topic/stream/{sesion}    docente dueño del examen de esa sesión (o administrador)
 *   SUBSCRIBE /topic/comandos/{sesion}  el estudiante dueño de la sesión, o el docente dueño del examen
 *   SEND      /topic/stream/{sesion}    solo el estudiante dueño de la sesión
 *   SEND      /topic/comandos/{sesion}  solo el docente dueño del examen (o administrador)
 *   SEND      /app/comando/{sesion}     solo el docente dueño del examen (o administrador)
 *
 * Para evitar consultas repetidas a MongoDB en tráfico continuo (fotogramas de video y voz), las autorizaciones de
 * publicación en /topic/stream/* y /topic/voz/* se cachean brevemente en RAM por sesión WebSocket y se invalidan al
 * desconectar o cerrar la sesión de examen. Todo lo demás se verifica siempre contra base de datos.
 */
@Component
public class StompAutorizacionInterceptor implements ChannelInterceptor {

    private static final Pattern ALERTAS = Pattern.compile("^/topic/alertas/([^/]+)$");
    private static final Pattern STREAM = Pattern.compile("^/topic/stream/([^/]+)$");
    private static final Pattern COMANDOS = Pattern.compile("^/topic/comandos/([^/]+)$");
    private static final Pattern VOZ = Pattern.compile("^/topic/voz/([^/]+)$");
    private static final Pattern APP_COMANDO = Pattern.compile("^/app/comando/([^/]+)$");
    private static final Pattern APP_ACK = Pattern.compile("^/app/alerta-ack/([^/]+)$");

    static final long CACHE_SEND_TTL_MS = 20_000L;
    private static final int MAX_ENTRADAS_CACHE = 4096;

    private record ClaveCacheSend(String wsSessionId, String usuario, String rol, String destino, String sesionId) {}

    private final Map<ClaveCacheSend, Long> cacheSendMedia = new ConcurrentHashMap<>();

    @Autowired
    private AutorizacionService autorizacion;

    public void invalidarWsSession(String wsSessionId) {
        if (wsSessionId == null || wsSessionId.isBlank()) {
            return;
        }
        cacheSendMedia.keySet().removeIf(k -> wsSessionId.equals(k.wsSessionId()));
    }

    public void invalidarSesion(String sesionId) {
        if (sesionId == null || sesionId.isBlank()) {
            return;
        }
        cacheSendMedia.keySet().removeIf(k -> sesionId.equals(k.sesionId()));
    }

    private static String rolPrincipal(Authentication auth) {
        if (AutorizacionService.esAdmin(auth)) {
            return "A";
        }
        if (AutorizacionService.esProfesor(auth)) {
            return "P";
        }
        if (AutorizacionService.esEstudiante(auth)) {
            return "E";
        }
        return "U";
    }

    private boolean estaCacheado(ClaveCacheSend clave, long ahoraMs) {
        Long expiraEn = cacheSendMedia.get(clave);
        if (expiraEn == null) {
            return false;
        }
        if (ahoraMs < expiraEn) {
            return true;
        }
        cacheSendMedia.remove(clave, expiraEn);
        return false;
    }

    private void registrarEnCache(ClaveCacheSend clave, long ahoraMs) {
        if (cacheSendMedia.size() >= MAX_ENTRADAS_CACHE) {
            cacheSendMedia.entrySet().removeIf(e -> ahoraMs >= e.getValue());
            if (cacheSendMedia.size() >= MAX_ENTRADAS_CACHE) {
                cacheSendMedia.clear();
            }
        }
        cacheSendMedia.put(clave, ahoraMs + CACHE_SEND_TTL_MS);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acc == null || acc.getCommand() == null) {
            return message;
        }
        StompCommand comando = acc.getCommand();
        if (StompCommand.CONNECT.equals(comando) || StompCommand.STOMP.equals(comando)) {
            if (!(acc.getUser() instanceof Authentication)) {
                throw new MessagingException("Conexión rechazada: se requiere un token válido");
            }
            return message;
        }
        if (!StompCommand.SUBSCRIBE.equals(comando) && !StompCommand.SEND.equals(comando)) {
            return message;               // DISCONNECT, UNSUBSCRIBE, ACK...: nada que autorizar
        }

        Principal usuario = acc.getUser();
        if (!(usuario instanceof Authentication)) {
            throw new MessagingException("Operación rechazada: sesión sin autenticar");
        }
        Authentication auth = (Authentication) usuario;
        String destino = acc.getDestination() == null ? "" : acc.getDestination();
        String wsSessionId = acc.getSessionId() != null ? acc.getSessionId() : "";
        try {
            if (StompCommand.SUBSCRIBE.equals(comando)) {
                autorizarSuscripcion(auth, destino);
            } else {
                autorizarPublicacion(auth, wsSessionId, destino);
            }
        } catch (AccessDeniedException e) {
            throw new MessagingException("Operación no permitida sobre " + destino);
        }
        return message;
    }

    private void autorizarSuscripcion(Authentication auth, String destino) {
        Matcher m;
        if ((m = ALERTAS.matcher(destino)).matches()) {
            exigirDocente(auth);
            autorizacion.exigirDocenteDeExamenExistente(auth, m.group(1));
        } else if ((m = STREAM.matcher(destino)).matches()) {
            exigirDocente(auth);
            autorizacion.exigirAccesoASesionExistente(auth, m.group(1));
        } else if ((m = COMANDOS.matcher(destino)).matches() || (m = VOZ.matcher(destino)).matches()) {
            autorizacion.exigirAccesoASesionExistente(auth, m.group(1));
        } else {
            throw new AccessDeniedException("Destino no permitido");
        }
    }

    private void autorizarPublicacion(Authentication auth, String wsSessionId, String destino) {
        Matcher m;
        if ((m = STREAM.matcher(destino)).matches()) {
            String sesionId = m.group(1);
            if (!AutorizacionService.esEstudiante(auth)) {
                throw new AccessDeniedException("Solo el estudiante publica su video");
            }
            ClaveCacheSend clave = new ClaveCacheSend(wsSessionId, String.valueOf(auth.getName()), rolPrincipal(auth), destino, sesionId);
            long ahora = System.currentTimeMillis();
            if (!wsSessionId.isEmpty() && estaCacheado(clave, ahora)) {
                return;
            }
            autorizacion.exigirAccesoASesionExistente(auth, sesionId);
            if (!wsSessionId.isEmpty()) {
                registrarEnCache(clave, ahora);
            }
        } else if ((m = VOZ.matcher(destino)).matches()) {
            String sesionId = m.group(1);
            exigirDocente(auth);
            ClaveCacheSend clave = new ClaveCacheSend(wsSessionId, String.valueOf(auth.getName()), rolPrincipal(auth), destino, sesionId);
            long ahora = System.currentTimeMillis();
            if (!wsSessionId.isEmpty() && estaCacheado(clave, ahora)) {
                return;
            }
            autorizacion.exigirAccesoASesionExistente(auth, sesionId);
            if (!wsSessionId.isEmpty()) {
                registrarEnCache(clave, ahora);
            }
        } else if ((m = COMANDOS.matcher(destino)).matches() || (m = APP_COMANDO.matcher(destino)).matches()) {
            exigirDocente(auth);
            autorizacion.exigirAccesoASesionExistente(auth, m.group(1));
        } else if ((m = APP_ACK.matcher(destino)).matches()) {
            exigirDocente(auth);
            autorizacion.exigirDocenteDeExamenExistente(auth, m.group(1));
        } else {
            throw new AccessDeniedException("Destino no permitido");
        }
    }

    private void exigirDocente(Authentication auth) {
        if (!AutorizacionService.esProfesor(auth) && !AutorizacionService.esAdmin(auth)) {
            throw new AccessDeniedException("Solo docentes");
        }
    }
}
