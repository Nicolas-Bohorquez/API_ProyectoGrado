package com.example.AppSegurity.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuerpo de alta/edición de un profesor o estudiante desde el panel SuperADMIN.
 * Los límites de tamaño son nuevos (antes no existía ninguno); el resto de reglas
 * (formato de correo, unicidad de cédula/correo) las sigue resolviendo AdminService.
 */
@Getter
@Setter
public class DatosPersonaRequest {

    @NotBlank(message = "El campo 'nombre' es obligatorio.")
    @Size(max = 150, message = "El campo 'nombre' no puede superar los 150 caracteres.")
    private String nombre;

    @NotBlank(message = "El campo 'apellidos' es obligatorio.")
    @Size(max = 150, message = "El campo 'apellidos' no puede superar los 150 caracteres.")
    private String apellidos;

    @NotBlank(message = "El campo 'cedula' es obligatorio.")
    @Size(max = 20, message = "El campo 'cedula' no puede superar los 20 caracteres.")
    private String cedula;

    @NotBlank(message = "El campo 'email' es obligatorio.")
    @Size(max = 150, message = "El campo 'email' no puede superar los 150 caracteres.")
    private String email;

    public Map<String, String> toMap() {
        Map<String, String> datos = new HashMap<>();
        datos.put("nombre", nombre);
        datos.put("apellidos", apellidos);
        datos.put("cedula", cedula);
        datos.put("email", email);
        return datos;
    }
}
