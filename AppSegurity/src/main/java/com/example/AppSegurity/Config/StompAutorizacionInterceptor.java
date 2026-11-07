package com.example.AppSegurity.Config;

import java.security.Principal;
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
 * Todo lo demás se rechaza. Los mensajes que publica el servidor (alertas en vivo) no pasan por aquí.
 */
@Component
public class StompAutorizacionInterceptor implements ChannelInterceptor {

    private static final Pattern ALERTAS = Pattern.compile("^/topic/alertas/([^/]+)$");
    private static final Pattern STREAM = Pattern.compile("^/topic/stream/([^/]+)$");
    private static final Pattern COMANDOS = Pattern.compile("^/topic/comandos/([^/]+)$");
    private static final Pattern APP_COMANDO = Pattern.compile("^/app/comando/([^/]+)$");

    @Autowired
    private AutorizacionService autorizacion;

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
        try {
            if (StompCommand.SUBSCRIBE.equals(comando)) {
                autorizarSuscripcion(auth, destino);
            } else {
                autorizarPublicacion(auth, destino);
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
        } else if ((m = COMANDOS.matcher(destino)).matches()) {
            autorizacion.exigirAccesoASesionExistente(auth, m.group(1));
        } else {
            throw new AccessDeniedException("Destino no permitido");
        }
    }

    private void autorizarPublicacion(Authentication auth, String destino) {
        Matcher m;
        if ((m = STREAM.matcher(destino)).matches()) {
            if (!AutorizacionService.esEstudiante(auth)) {
                throw new AccessDeniedException("Solo el estudiante publica su video");
            }
            autorizacion.exigirAccesoASesionExistente(auth, m.group(1));
        } else if ((m = COMANDOS.matcher(destino)).matches() || (m = APP_COMANDO.matcher(destino)).matches()) {
            exigirDocente(auth);
            autorizacion.exigirAccesoASesionExistente(auth, m.group(1));
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
