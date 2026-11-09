package com.example.AppSegurity.Repositorys;

import com.example.AppSegurity.Enums.EstadoSesion;
import com.example.AppSegurity.Models.SesionSupervision;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SesionSupervisionRepository extends MongoRepository<SesionSupervision, String>{
    
    //Buscar y obtener todos las apelaciones con estado en revision
    List<SesionSupervision> findByExamenIdInAndEstadoSesion(List<String> idsExamenes,EstadoSesion estadoSesion);
    List<SesionSupervision> findByExamenIdIn(List<String> idsExamenes);

    //Buscar y obtener toda la info de la sesión de un estudiante en concreto
    Optional<SesionSupervision> findByEstudianteId(String estudianteId);

    // Todas las sesiones de un estudiante (de cualquier examen): sirve para saber si es su primer examen o un equipo nuevo
    List<SesionSupervision> findAllByEstudianteId(String estudianteId);

    //Buscar y obtener todas las sesiones de un examen especifico
    List<SesionSupervision> findByExamenId(String examenId);
}
