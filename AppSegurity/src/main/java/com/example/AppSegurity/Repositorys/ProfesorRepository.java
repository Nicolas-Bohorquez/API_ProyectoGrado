package com.example.AppSegurity.Repositorys;

import com.example.AppSegurity.Enums.EstadoUsuario;
import com.example.AppSegurity.Models.Profesor;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProfesorRepository extends MongoRepository<Profesor, String>{
    //Busca y trae el email del profesor
    Optional<Profesor> findByEmailInstitucional(String emailInstitucional);
    Optional<Profesor> findByEmailInstitucionalIgnoreCase(String emailInstitucional);
    Optional<Profesor> findByCedula(String cedula);
    Optional<Profesor> findByCodigoProfesor(String codigoProfesor);
    long countByEstado(EstadoUsuario estado);
    long countByRolIgnoreCaseAndEstado(String rol, EstadoUsuario estado);
}
