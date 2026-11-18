package com.example.AppSegurity.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Cuerpo de cambio de estado de un profesor o estudiante desde el panel SuperADMIN. */
@Getter
@Setter
public class CambiarEstadoRequest {

    @NotBlank(message = "El campo estado es requerido")
    @Size(max = 20, message = "El campo estado no es válido.")
    private String estado;
}
