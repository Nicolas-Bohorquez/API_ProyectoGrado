package com.example.AppSegurity.Controllers;

import com.example.AppSegurity.Models.Examen;
import com.example.AppSegurity.Services.ExamenService;
import com.example.AppSegurity.Sub_Clases.ConfiguracionExamen;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/examenes")

public class ExamenController {

    @Autowired
    private ExamenService examenService;
    @Autowired
    private com.example.AppSegurity.Security.AutorizacionService autorizacion;

    //Metodos Post
    @PostMapping("/crearExamen")
    public ResponseEntity<?> crearExamen(@RequestBody Examen peticionExamen, org.springframework.security.core.Authentication authentication) {
        autorizacion.exigirMismoProfesor(authentication, peticionExamen.getProfesorId());      // solo puede crear exámenes a su nombre
        try {
            //Creamos el examen y lo guardamos en una variable Examen
            Examen examenCreado = examenService.crearExamen(peticionExamen.getCodigoExamen(), peticionExamen.getProfesorId(),
                    peticionExamen.getMateriaCodigo(), peticionExamen.getFechaExamen());

            //Retornamos el examen creado
            return ResponseEntity.status(200).body(examenCreado);
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error al crear el examen: " + e.getMessage());
        }
    }

    @PutMapping("/{codigoExamen}/configurar")
    public ResponseEntity<?> configurarRestriccionesExamen(@PathVariable String codigoExamen, @RequestBody java.util.Map<String, Object> payload, org.springframework.security.core.Authentication authentication) {
        autorizacion.exigirDocenteDeExamen(authentication, codigoExamen);
        try {
            Examen examenConfigurado = examenService.configurarDesdeMapa(codigoExamen, payload);
            return ResponseEntity.ok(examenConfigurado);
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error al configurr el examen: " + e.getMessage());
        }
    }

    @PostMapping("/{codigoExamen}/abrir")
    public ResponseEntity<?> abrirSalaExamen(@PathVariable String codigoExamen, org.springframework.security.core.Authentication authentication) {
        autorizacion.exigirDocenteDeExamen(authentication, codigoExamen);
        try {
            //Abrimos el examen mediante su codigo 
            examenService.abrirSalaExamen(codigoExamen);

            return ResponseEntity.ok("Sala abierta exitosamente");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error en abrir la sala del examen: " + e.getMessage());
        }
    }
    
    @PostMapping("/{codigoExamen}/cerrar")
    public ResponseEntity<?> cerrarSalaExamen(@PathVariable String codigoExamen, org.springframework.security.core.Authentication authentication) {
        autorizacion.exigirDocenteDeExamen(authentication, codigoExamen);
        try {
            //cerramos el examen mediante su codigo 
            examenService.cerrarSalaExamen(codigoExamen);

            return ResponseEntity.ok("Sala cerrada exitosamente");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error al cerrar la sala del examen: " + e.getMessage());
        }
    }
    
    //Metodo GET
    @GetMapping("/profesor/{profesorId}")
    public ResponseEntity<?> obtenerExamenesProfesor(@PathVariable String profesorId, org.springframework.security.core.Authentication authentication){
        autorizacion.exigirMismoProfesor(authentication, profesorId);
        try {
            //Obtenemos todos los examenes del profesor y lo guardamos en una lista para mandarselo al profesor
            List<Examen> examenes_profesor = examenService.obtenerExamenesProfesor(profesorId);
            
            //retornamos esa lista de examenes al profesor
            return ResponseEntity.ok(examenes_profesor);
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error al obtener los examenes del profesor: "+e.getMessage());
        }
    }
    
    @PostMapping("/{codigoExamen}/estudiantes/bulk")
    public ResponseEntity<?> bulkUploadEstudiantes(@PathVariable String codigoExamen, @RequestBody List<com.example.AppSegurity.Models.Estudiante> estudiantes, org.springframework.security.core.Authentication authentication) {
        autorizacion.exigirDocenteDeExamen(authentication, codigoExamen);
        try {
            java.util.List<java.util.Map<String, String>> credenciales = examenService.bulkInscribirEstudiantes(codigoExamen, estudiantes);
            java.util.Map<String, Object> respuesta = new java.util.LinkedHashMap<>();
            respuesta.put("mensaje", "Estudiantes inscritos correctamente");
            respuesta.put("credenciales", credenciales);          // solo las cuentas nuevas, con su clave temporal
            return ResponseEntity.ok(respuesta);
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error al inscribir estudiantes: " + e.getMessage());
        }
    }

    // Guardar el análisis global de la IA en el examen (reemplaza si ya existe)
    @PutMapping("/{codigoExamen}/analisis-ia")
    public ResponseEntity<?> guardarAnalisisIA(
            @PathVariable String codigoExamen,
            @RequestBody com.example.AppSegurity.Sub_Clases.AnalisisGlobal_IA analisis, org.springframework.security.core.Authentication authentication) {
        autorizacion.exigirDocenteDeExamen(authentication, codigoExamen);
        try {
            com.example.AppSegurity.Models.Examen examen = examenService.obtenerExamenPorId(codigoExamen);
            if (examen == null) {
                return ResponseEntity.status(404).body("Examen no encontrado");
            }
            examen.setAnalisisGlobalIA(analisis);
            examenService.guardarExamen(examen);
            return ResponseEntity.ok("Análisis guardado correctamente");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error al guardar el análisis: " + e.getMessage());
        }
    }

    // Obtener el análisis global de la IA del examen
    @GetMapping("/{codigoExamen}/analisis-ia")
    public ResponseEntity<?> obtenerAnalisisIA(@PathVariable String codigoExamen, org.springframework.security.core.Authentication authentication) {
        autorizacion.exigirDocenteDeExamen(authentication, codigoExamen);
        try {
            com.example.AppSegurity.Models.Examen examen = examenService.obtenerExamenPorId(codigoExamen);
            if (examen == null) {
                return ResponseEntity.status(404).body("Examen no encontrado");
            }
            if (examen.getAnalisisGlobalIA() == null) {
                return ResponseEntity.status(204).body(null); // 204 = sin contenido, no hay análisis aún
            }
            return ResponseEntity.ok(examen.getAnalisisGlobalIA());
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error al obtener el análisis: " + e.getMessage());
        }
    }
}
