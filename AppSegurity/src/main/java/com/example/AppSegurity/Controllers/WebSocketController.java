package com.example.AppSegurity.Controllers;

import com.example.AppSegurity.Config.StompAutorizacionInterceptor;
import com.example.AppSegurity.Enums.EstadoSesion;
import com.example.AppSegurity.Models.AlertaEvidencia;
import com.example.AppSegurity.Repositorys.AlertaEvidenciaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.util.Map;

@Controller
public class WebSocketController {

    @Autowired
    private com.example.AppSegurity.Repositorys.SesionSupervisionRepository sesionSupervisionRepository;

    @Autowired
    private AlertaEvidenciaRepository alertaEvidenciaRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired(required = false)
    private StompAutorizacionInterceptor stompAutorizacionInterceptor;

    @MessageMapping("/alerta-ack/{examenId}")
    public void confirmarRecepcionAlerta(@DestinationVariable String examenId, Map<String, Object> cuerpo) {
        if (cuerpo == null || examenId == null || examenId.isBlank()) {
            return;
        }
        Object rawId = cuerpo.get("alertaId");
        String alertaId = rawId == null ? "" : String.valueOf(rawId).trim();
        if (alertaId.isEmpty()) {
            return;
        }
        alertaEvidenciaRepository.findById(alertaId).ifPresent((AlertaEvidencia alerta) -> {
            if (alerta.getSesionId() == null) {
                return;
            }
            var sesionOpt = sesionSupervisionRepository.findById(alerta.getSesionId());
            if (sesionOpt.isEmpty() || !examenId.equals(sesionOpt.get().getExamenId())) {
                return;
            }
            if (!alerta.isEntregadaDocente()) {
                alerta.setEntregadaDocente(true);
                alertaEvidenciaRepository.save(alerta);
            }
        });
    }

    @MessageMapping("/comando/{sesionId}")
    public void reenviarComando(@DestinationVariable String sesionId, Map<String, Object> comando) {
        if (comando == null) {
            return;
        }
        if ("FRAUDE".equals(comando.get("comando"))) {
            var sesionOpt = sesionSupervisionRepository.findById(sesionId);
            if (sesionOpt.isEmpty()) {
                return;
            }
            var sesion = sesionOpt.get();
            var estado = sesion.getEstadoSesion();
            if (estado == EstadoSesion.FINALIZADA || estado == EstadoSesion.APELACION_CURSO) {
                return;
            }
            sesion.setEstadoSesion(EstadoSesion.ANULADA);
            sesionSupervisionRepository.save(sesion);
            if (stompAutorizacionInterceptor != null) {
                stompAutorizacionInterceptor.invalidarSesion(sesionId);
            }
        }
        messagingTemplate.convertAndSend("/topic/comandos/" + sesionId, (Object) comando);
    }
}
