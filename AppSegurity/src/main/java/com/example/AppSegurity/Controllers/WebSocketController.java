package com.example.AppSegurity.Controllers;

import com.example.AppSegurity.Config.StompAutorizacionInterceptor;
import com.example.AppSegurity.Enums.EstadoSesion;
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
    private SimpMessagingTemplate messagingTemplate;

    @Autowired(required = false)
    private StompAutorizacionInterceptor stompAutorizacionInterceptor;

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
