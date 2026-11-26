package com.example.AppSegurity.Config;

import com.example.AppSegurity.Models.Alerta.AlertaAudio;
import com.example.AppSegurity.Models.Alerta.AlertaProceso;
import com.example.AppSegurity.Models.Alerta.AlertaTeclado;
import com.example.AppSegurity.Models.Alerta.AlertaVision;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * Registra en el arranque las subclases polimórficas {@code @TypeAlias} de {@code AlertaEvidencia}
 * dentro de {@link MongoMappingContext}. Sin este registro explícito, tras un reinicio en frío
 * (antes de escribir una alerta nueva de cada subtipo), Spring Data MongoDB podría deserializar
 * documentos previos como la clase base perdiendo campos como {@code nombreProceso} o {@code combinacionTeclas}.
 */
@Configuration
public class MongoMapeoAlertasConfig {

    @Autowired
    private MongoMappingContext mongoMappingContext;

    @PostConstruct
    public void registrarSubtiposAlerta() {
        mongoMappingContext.getPersistentEntity(AlertaVision.class);
        mongoMappingContext.getPersistentEntity(AlertaAudio.class);
        mongoMappingContext.getPersistentEntity(AlertaProceso.class);
        mongoMappingContext.getPersistentEntity(AlertaTeclado.class);
    }
}
