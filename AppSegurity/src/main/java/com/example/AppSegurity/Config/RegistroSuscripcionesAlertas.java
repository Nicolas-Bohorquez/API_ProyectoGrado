package com.example.AppSegurity.Config;

import com.example.AppSegurity.Enums.EstadoSesion;
import com.example.AppSegurity.Models.SesionSupervision;
import com.example.AppSegurity.Repositorys.SesionSupervisionRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.AbstractSubProtocolEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * Mantiene en RAM el registro de qué exámenes tienen al menos un docente suscrito en vivo
 * al tópico {@code /topic/alertas/{examenId}}.
 *
 * Permite informar al cliente estudiante (sin almacenar imágenes ni audio en la nube, cumpliendo
 * la Ley 1581 de 2012) si el receptor local del docente está escuchando en ese instante, y pedir
 * sincronización automática ({@code SYNC_EVIDENCIAS}) apenas el docente se suscribe.
 */
@Component
public class RegistroSuscripcionesAlertas implements ApplicationListener<AbstractSubProtocolEvent> {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RegistroSuscripcionesAlertas.class);
    private static final Pattern PATRON_ALERTAS = Pattern.compile("^/topic/alertas/([^/]+)$");

    private final Map<String, Set<String>> suscripcionesPorExamen = new ConcurrentHashMap<>();
    private final Map<String, String> examenPorClaveSub = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> clavesPorWsSession = new ConcurrentHashMap<>();

    @Autowired
    private SesionSupervisionRepository sesionSupervisionRepository;

    @Autowired
    @Lazy
    private SimpMessagingTemplate webSocketMessagingTemplate;

    @Autowired(required = false)
    private StompAutorizacionInterceptor stompAutorizacionInterceptor;

    @Override
    public void onApplicationEvent(AbstractSubProtocolEvent event) {
        if (event instanceof SessionSubscribeEvent) {
            manejarSuscripcion((SessionSubscribeEvent) event);
        } else if (event instanceof SessionUnsubscribeEvent) {
            manejarDesuscripcion((SessionUnsubscribeEvent) event);
        } else if (event instanceof SessionDisconnectEvent) {
            manejarDesconexion((SessionDisconnectEvent) event);
        }
    }

    public boolean hayReceptorActivo(String examenId) {
        if (examenId == null || examenId.isBlank()) {
            return false;
        }
        Set<String> subs = suscripcionesPorExamen.get(examenId);
        return subs != null && !subs.isEmpty();
    }

    private void manejarSuscripcion(SessionSubscribeEvent event) {
        StompHeaderAccessor acc = StompHeaderAccessor.wrap(event.getMessage());
        String destino = acc.getDestination();
        if (destino == null) {
            return;
        }
        Matcher m = PATRON_ALERTAS.matcher(destino);
        if (!m.matches()) {
            return;
        }
        String examenId = m.group(1);
        String wsSessionId = acc.getSessionId();
        if (wsSessionId == null || wsSessionId.isBlank()) {
            return;
        }
        String claveSub = construirClaveSub(wsSessionId, acc.getSubscriptionId());
        registrarSuscripcion(wsSessionId, claveSub, examenId);
        notificarSyncEvidencias(examenId);
    }

    private void manejarDesuscripcion(SessionUnsubscribeEvent event) {
        StompHeaderAccessor acc = StompHeaderAccessor.wrap(event.getMessage());
        String wsSessionId = acc.getSessionId();
        if (wsSessionId == null || wsSessionId.isBlank()) {
            return;
        }
        String claveSub = construirClaveSub(wsSessionId, acc.getSubscriptionId());
        eliminarSuscripcion(wsSessionId, claveSub);
    }

    private void manejarDesconexion(SessionDisconnectEvent event) {
        String wsSessionId = event.getSessionId();
        if (wsSessionId == null || wsSessionId.isBlank()) {
            return;
        }
        if (stompAutorizacionInterceptor != null) {
            stompAutorizacionInterceptor.invalidarWsSession(wsSessionId);
        }
        Set<String> claves = clavesPorWsSession.remove(wsSessionId);
        if (claves == null || claves.isEmpty()) {
            return;
        }
        for (String claveSub : claves) {
            quitarDeIndiceExamen(claveSub);
        }
    }

    private void registrarSuscripcion(String wsSessionId, String claveSub, String examenId) {
        String previo = examenPorClaveSub.put(claveSub, examenId);
        if (previo != null && !previo.equals(examenId)) {
            quitarClaveDeExamen(previo, claveSub);
        }
        suscripcionesPorExamen
                .computeIfAbsent(examenId, k -> ConcurrentHashMap.newKeySet())
                .add(claveSub);
        clavesPorWsSession
                .computeIfAbsent(wsSessionId, k -> ConcurrentHashMap.newKeySet())
                .add(claveSub);
    }

    private void eliminarSuscripcion(String wsSessionId, String claveSub) {
        Set<String> claves = clavesPorWsSession.get(wsSessionId);
        if (claves != null) {
            claves.remove(claveSub);
            if (claves.isEmpty()) {
                clavesPorWsSession.remove(wsSessionId, Collections.emptySet());
            }
        }
        quitarDeIndiceExamen(claveSub);
    }

    private void quitarDeIndiceExamen(String claveSub) {
        String examenId = examenPorClaveSub.remove(claveSub);
        if (examenId != null) {
            quitarClaveDeExamen(examenId, claveSub);
        }
    }

    private void quitarClaveDeExamen(String examenId, String claveSub) {
        suscripcionesPorExamen.computeIfPresent(examenId, (k, set) -> {
            set.remove(claveSub);
            return set.isEmpty() ? null : set;
        });
    }

    private void notificarSyncEvidencias(String examenId) {
        try {
            List<SesionSupervision> sesiones = sesionSupervisionRepository.findByExamenId(examenId);
            if (sesiones == null || sesiones.isEmpty()) {
                return;
            }
            Map<String, String> payload = Map.of("comando", "SYNC_EVIDENCIAS");
            for (SesionSupervision s : sesiones) {
                if (s.getSesionId() != null
                        && (s.getEstadoSesion() == EstadoSesion.INICIADA
                                || s.getEstadoSesion() == EstadoSesion.INTERRUMPIDA)) {
                    webSocketMessagingTemplate.convertAndSend("/topic/comandos/" + s.getSesionId(), payload);
                }
            }
        } catch (Exception e) {
            // Un error notificando sincronización nunca debe bloquear la suscripción del docente
            LOG.warn("[WS] No se pudo notificar SYNC_EVIDENCIAS para examen {}: {}", examenId, e.getMessage());
        }
    }

    private static String construirClaveSub(String wsSessionId, String subId) {
        return wsSessionId + ":" + (subId != null && !subId.isBlank() ? subId : "default");
    }
}
