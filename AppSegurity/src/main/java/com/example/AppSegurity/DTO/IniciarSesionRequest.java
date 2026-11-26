package com.example.AppSegurity.DTO;

import com.example.AppSegurity.Sub_Clases.Conexion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class IniciarSesionRequest {

    //Parametros de la clase
    @Size(max = 120, message = "El estudianteId no puede superar 120 caracteres.")
    private String estudianteId;
    @NotBlank(message = "El PIN del examen es obligatorio.")
    @Size(max = 32, message = "El PIN del examen no puede superar 32 caracteres.")
    private String pinExamen;
    private Conexion conexion;

    //Constructor sin parametros
    public IniciarSesionRequest() {

    }

    //Constructor con parametros
    public IniciarSesionRequest(String estudianteId, String pinExamen, Conexion conexion) {
        this.estudianteId = estudianteId;
        this.pinExamen = pinExamen;
        this.conexion = conexion;
    }

}
