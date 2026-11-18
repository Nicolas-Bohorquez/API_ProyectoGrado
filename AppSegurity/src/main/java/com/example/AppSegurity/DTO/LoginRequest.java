package com.example.AppSegurity.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    //Parametros de la clase
    @NotBlank(message = "El correo es obligatorio.")
    @Size(max = 120, message = "El correo no puede superar 120 caracteres.")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria.")
    @Size(max = 128, message = "La contraseña no puede superar 128 caracteres.")
    private String password;

    //Constructor sin parametros
    public LoginRequest() {

    }

    //Constructor con parametros
    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }
}
