package com.example.AppSegurity.Controllers;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import java.util.Map;

@Controller
public class WebSocketController {

    @org.springframework.beans.factory.annotation.Autowired
    private com.example.AppSegurity.Repositorys.SesionSupervisionRepository sesionSupervisionRepository;

    @MessageMapping("/comando/{sesionId}")
    @SendTo("/topic/comandos/{sesionId}")
    public Map<String, Object> reenviarComando(@DestinationVariable String sesionId, Map<String, Object> comando) {
        if (comando != null && "FRAUDE".equals(comando.get("comando"))) {
            sesionSupervisionRepository.findById(sesionId).ifPresent(sesion -> {
                var estado = sesion.getEstadoSesion();
                if (estado != com.example.AppSegurity.Enums.EstadoSesion.FINALIZADA
                        && estado != com.example.AppSegurity.Enums.EstadoSesion.APELACION_CURSO) {
                    sesion.setEstadoSesion(com.example.AppSegurity.Enums.EstadoSesion.ANULADA);
                    sesionSupervisionRepository.save(sesion);
                }
            });
        }
        return comando;
    }
}
