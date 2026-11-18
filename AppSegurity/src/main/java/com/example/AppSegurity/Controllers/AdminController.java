package com.example.AppSegurity.Controllers;

import com.example.AppSegurity.DTO.CambiarEstadoRequest;
import com.example.AppSegurity.DTO.DatosPersonaRequest;
import com.example.AppSegurity.Services.AdminService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    /** Ejecuta la acción y traduce las excepciones a respuestas HTTP coherentes. */
    private ResponseEntity<?> ejecutar(Supplier<Object> accion) {
        try {
            return ResponseEntity.ok(accion.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    private Map<String, Object> mensaje(String texto) {
        Map<String, Object> m = new HashMap<>();
        m.put("mensaje", texto);
        return m;
    }

    @GetMapping("/resumen")
    public ResponseEntity<?> obtenerResumen() {
        return ejecutar(() -> adminService.obtenerResumen());
    }

    @GetMapping("/profesores")
    public ResponseEntity<?> listarProfesores() {
        return ejecutar(() -> adminService.listarProfesores());
    }

    @GetMapping("/estudiantes")
    public ResponseEntity<?> listarEstudiantes() {
        return ejecutar(() -> adminService.listarEstudiantes());
    }

    // ---------------------------------------------------------------- Estado

    @PutMapping("/profesores/{codigoProfesor}/estado")
    public ResponseEntity<?> cambiarEstadoProfesor(@PathVariable String codigoProfesor, @Valid @RequestBody CambiarEstadoRequest payload) {
        String nuevoEstado = payload.getEstado();
        return ejecutar(() -> {
            adminService.cambiarEstadoProfesor(codigoProfesor, nuevoEstado);
            return mensaje("Estado del docente actualizado con éxito a " + nuevoEstado);
        });
    }

    @PutMapping("/estudiantes/{estudianteId}/estado")
    public ResponseEntity<?> cambiarEstadoEstudiante(@PathVariable String estudianteId, @Valid @RequestBody CambiarEstadoRequest payload) {
        String nuevoEstado = payload.getEstado();
        return ejecutar(() -> {
            adminService.cambiarEstadoEstudiante(estudianteId, nuevoEstado);
            return mensaje("Estado del estudiante actualizado con éxito a " + nuevoEstado);
        });
    }

    // --------------------------------------------------------------- Edición

    @PutMapping("/profesores/{codigoProfesor}")
    public ResponseEntity<?> editarProfesor(@PathVariable String codigoProfesor, @Valid @RequestBody DatosPersonaRequest datos) {
        return ejecutar(() -> {
            adminService.editarProfesor(codigoProfesor, datos.toMap());
            return mensaje("Datos del docente actualizados correctamente.");
        });
    }

    @PutMapping("/estudiantes/{estudianteId}")
    public ResponseEntity<?> editarEstudiante(@PathVariable String estudianteId, @Valid @RequestBody DatosPersonaRequest datos) {
        return ejecutar(() -> {
            adminService.editarEstudiante(estudianteId, datos.toMap());
            return mensaje("Datos del estudiante actualizados correctamente.");
        });
    }

    // -------------------------------------------------------------- Creación

    @PostMapping("/profesores")
    public ResponseEntity<?> crearProfesor(@Valid @RequestBody DatosPersonaRequest datos) {
        return ejecutar(() -> adminService.crearProfesor(datos.toMap()));
    }

    @PostMapping("/estudiantes")
    public ResponseEntity<?> crearEstudiante(@Valid @RequestBody DatosPersonaRequest datos) {
        return ejecutar(() -> adminService.crearEstudiante(datos.toMap()));
    }

    // --------------------------------------------------- Restablecer contraseña

    @PostMapping("/profesores/{codigoProfesor}/restablecer-clave")
    public ResponseEntity<?> restablecerClaveProfesor(@PathVariable String codigoProfesor) {
        return ejecutar(() -> {
            Map<String, Object> m = mensaje("Contraseña restablecida.");
            m.put("claveTemporal", adminService.restablecerClaveProfesor(codigoProfesor));
            return m;
        });
    }

    @PostMapping("/estudiantes/{estudianteId}/restablecer-clave")
    public ResponseEntity<?> restablecerClaveEstudiante(@PathVariable String estudianteId) {
        return ejecutar(() -> {
            Map<String, Object> m = mensaje("Contraseña restablecida.");
            m.put("claveTemporal", adminService.restablecerClaveEstudiante(estudianteId));
            return m;
        });
    }

    // -------------------------------------------------------------- Eliminar

    @DeleteMapping("/profesores/{codigoProfesor}")
    public ResponseEntity<?> eliminarProfesor(@PathVariable String codigoProfesor) {
        return ejecutar(() -> {
            adminService.eliminarProfesor(codigoProfesor);
            return mensaje("Cuenta del docente eliminada.");
        });
    }

    @DeleteMapping("/estudiantes/{estudianteId}")
    public ResponseEntity<?> eliminarEstudiante(@PathVariable String estudianteId) {
        return ejecutar(() -> {
            adminService.eliminarEstudiante(estudianteId);
            return mensaje("Cuenta del estudiante eliminada.");
        });
    }
}
