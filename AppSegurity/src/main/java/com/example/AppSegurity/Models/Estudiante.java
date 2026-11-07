package com.example.AppSegurity.Models;

import com.example.AppSegurity.Enums.EstadoUsuario;
import com.example.AppSegurity.Sub_Clases.Auditoria;
import com.example.AppSegurity.Sub_Clases.BiometriaFacial;
import com.example.AppSegurity.Sub_Clases.Materia;
import com.example.AppSegurity.Sub_Clases.SeguridadJwt;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "estudiante")
public class Estudiante {

    //Parametros de la clase
    @Id
    private String estudianteId;
    private String nombre;
    private String apellidos;
    private String cedula;
    private String email;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordHash;     // nunca se devuelve por la API
    private boolean claveTemporal;   // true: la clave la generó el sistema y el estudiante debe cambiarla al entrar
    private EstadoUsuario estadoUsuario;
    private List<Materia> materiasInscritas;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private BiometriaFacial biometriaFacial;
    private Auditoria auditoria;
    private SeguridadJwt seguridadJwt;

    //Constructor sin parametros
    public Estudiante() {

    }

    //Constructor con parametros
    public Estudiante(String estudianteId, String nombre, String apellidos, String cedula, String email, String passwordHash, EstadoUsuario estadoUsuario, List<Materia> materiasInscritas, BiometriaFacial biometriaFacial, Auditoria auditoria, SeguridadJwt seguridadJwt) {
        this.estudianteId = estudianteId;
        this.nombre = nombre;
        this.apellidos = apellidos;
        this.cedula = cedula;
        this.email = email;
        this.passwordHash = passwordHash;
        this.estadoUsuario = estadoUsuario;
        this.materiasInscritas = materiasInscritas;
        this.biometriaFacial = biometriaFacial;
        this.auditoria = auditoria;
        this.seguridadJwt = seguridadJwt;
    }

}
