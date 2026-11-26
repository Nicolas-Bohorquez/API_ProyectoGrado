package com.example.AppSegurity.DTO;

import com.example.AppSegurity.Sub_Clases.FechaExamen;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrearExamenRequest {

    @NotBlank(message = "El código del examen es obligatorio.")
    @Size(max = 80, message = "El código del examen no puede superar 80 caracteres.")
    private String codigoExamen;

    @NotBlank(message = "El código del profesor es obligatorio.")
    @Size(max = 80, message = "El código del profesor no puede superar 80 caracteres.")
    private String profesorId;

    @NotBlank(message = "El código de la materia es obligatorio.")
    @Size(max = 80, message = "El código de la materia no puede superar 80 caracteres.")
    private String materiaCodigo;

    private FechaExamen fechaExamen;
}
