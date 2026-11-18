package com.example.AppSegurity.DTO;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecuperarSolicitudRequest {
    @Size(max = 120, message = "El correo no puede superar 120 caracteres.")
    private String email;

    public RecuperarSolicitudRequest() {}
    public RecuperarSolicitudRequest(String email) {
        this.email = email;
    }
}
