package com.example.AppSegurity.Repositorys;

import com.example.AppSegurity.Models.AlertaEvidencia;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AlertaEvidenciaRepository extends MongoRepository<AlertaEvidencia, String>{
    
    //buscar todas las alertas que tengan el mismo id de sesion
    List<AlertaEvidencia> findBySesionIdOrderByHoraCapturaAsc(String sesionId);
    
    //buscar todas las alertas de multiples sesiones
    List<AlertaEvidencia> findBySesionIdInOrderByHoraCapturaDesc(List<String> sesionIds);

    Optional<AlertaEvidencia> findFirstBySesionIdAndIdCliente(String sesionId, String idCliente);

    // Conteos para el resumen del administrador (se cuentan en la base de datos, sin cargar las alertas en memoria)
    long countByClaseAlertaIn(java.util.Collection<com.example.AppSegurity.Enums.ClaseAlerta> clases);
}
