package com.example.AppSegurity.Models;

import com.example.AppSegurity.Enums.EstadoUsuario;
import com.example.AppSegurity.Sub_Clases.Auditoria;
import com.example.AppSegurity.Sub_Clases.Materia;
import com.example.AppSegurity.Sub_Clases.SeguridadJwt;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter 
@Document(collection = "profesor")
public class Profesor {
    //parametros de la clase
    @Id
    private String codigoProfesor;
    private String nombre;
    private String apellidos;
    @Indexed
    private String cedula;
    @Indexed
    private String emailInstitucional;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordHash; // Cifrado Argon2 para mayor seguridad
    private EstadoUsuario estado;
    private List<Materia> materias; // Lista de materias asignadas al profesor
    private Auditoria auditoria;
    private SeguridadJwt seguridadJwt;
    private String rol; // "PROFESOR" o "SUPERADMIN"

    
    //Constructor vacio
    public Profesor(){
        
    }
    //Constructor con parametros

    public Profesor(String codigoProfesor, String nombre, String apellidos, String cedula, String emailInstitucional, String passwordHash, EstadoUsuario estado, List<Materia> materias, Auditoria auditoria, SeguridadJwt seguridadJwt) {
        this.codigoProfesor = codigoProfesor;
        this.nombre = nombre;
        this.apellidos = apellidos;
        this.cedula = cedula;
        this.emailInstitucional = emailInstitucional;
        this.passwordHash = passwordHash;
        this.estado = estado;
        this.materias = materias;
        this.auditoria = auditoria;
        this.seguridadJwt = seguridadJwt;
    }

    
}
