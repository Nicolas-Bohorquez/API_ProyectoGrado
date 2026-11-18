package com.example.AppSegurity.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CambiarRolRequest {

    @NotBlank(message = "El rol es obligatorio.")
    @Pattern(regexp = "(?i)^(PROFESOR|SUPERADMIN)$", message = "El rol debe ser PROFESOR o SUPERADMIN.")
    private String rol;
}
