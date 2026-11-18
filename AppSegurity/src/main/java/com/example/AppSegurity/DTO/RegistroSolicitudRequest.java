package com.example.AppSegurity.DTO;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistroSolicitudRequest {

    @Size(max = 120, message = "El correo no puede superar 120 caracteres.")
    private String email;

    @Size(max = 25, message = "La cédula no puede superar 25 caracteres.")
    private String cedula;

    @Size(max = 20, message = "El rol no puede superar 20 caracteres.")
    private String rol; // "ESTUDIANTE" o "PROFESOR"

    @Size(max = 80, message = "El nombre no puede superar 80 caracteres.")
    private String nombre;

    public RegistroSolicitudRequest() {
    }

    public RegistroSolicitudRequest(String email, String cedula, String rol, String nombre) {
        this.email = email;
        this.cedula = cedula;
        this.rol = rol;
        this.nombre = nombre;
    }
}
