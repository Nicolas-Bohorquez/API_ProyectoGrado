package com.example.AppSegurity.DTO;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistroConfirmarRequest {

    @Size(max = 120, message = "El correo no puede superar 120 caracteres.")
    private String email;

    @Size(max = 12, message = "El código OTP no puede superar 12 caracteres.")
    private String codigoOtp;

    @Size(max = 80, message = "El nombre no puede superar 80 caracteres.")
    private String nombre;

    @Size(max = 80, message = "Los apellidos no pueden superar 80 caracteres.")
    private String apellidos;

    @Size(max = 25, message = "La cédula no puede superar 25 caracteres.")
    private String cedula;

    @Size(max = 128, message = "La contraseña no puede superar 128 caracteres.")
    private String password;

    @Size(max = 30, message = "El código de profesor no puede superar 30 caracteres.")
    private String codigoProfesor; // Opcional, solo para profesores

    public RegistroConfirmarRequest() {
    }

    public RegistroConfirmarRequest(String email, String codigoOtp, String nombre, String apellidos, String cedula, String password, String codigoProfesor) {
        this.email = email;
        this.codigoOtp = codigoOtp;
        this.nombre = nombre;
        this.apellidos = apellidos;
        this.cedula = cedula;
        this.password = password;
        this.codigoProfesor = codigoProfesor;
    }
}
