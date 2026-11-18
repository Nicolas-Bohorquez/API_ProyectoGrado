package com.example.AppSegurity.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuerpo de POST /api/estudiante/cambiar-clave. El mensaje de "faltan campos" es el mismo
 * en ambos campos a propósito: así, sin importar cuál esté vacío, se conserva el mensaje
 * genérico que ya mostraba el controller antes de este cambio.
 */
@Getter
@Setter
public class CambiarClaveRequest {

    @NotBlank(message = "Faltan la clave actual o la nueva.")
    @Size(max = 128, message = "La clave actual no puede superar los 128 caracteres.")
    private String claveActual;

    @NotBlank(message = "Faltan la clave actual o la nueva.")
    @Size(max = 128, message = "La nueva clave no puede superar los 128 caracteres.")
    private String claveNueva;
}
