package com.example.AppSegurity.DTO;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecuperarConfirmarRequest {
    @Size(max = 120, message = "El correo no puede superar 120 caracteres.")
    private String email;

    @Size(max = 12, message = "El código OTP no puede superar 12 caracteres.")
    private String codigoOtp;

    @Size(max = 128, message = "La contraseña no puede superar 128 caracteres.")
    private String nuevaPassword;

    public RecuperarConfirmarRequest() {}
    public RecuperarConfirmarRequest(String email, String codigoOtp, String nuevaPassword) {
        this.email = email;
        this.codigoOtp = codigoOtp;
        this.nuevaPassword = nuevaPassword;
    }
}
