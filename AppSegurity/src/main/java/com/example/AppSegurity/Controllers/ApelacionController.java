package com.example.AppSegurity.Controllers;

import com.example.AppSegurity.Enums.EstadoApelacion;
import com.example.AppSegurity.Models.SesionSupervision;
import com.example.AppSegurity.Services.ApelacionService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/apelaciones")
public class ApelacionController {
    @Autowired ApelacionService apelacionService;
    @Autowired com.example.AppSegurity.Security.AutorizacionService autorizacion;
    
    
   @PostMapping("/{sesionId}/solicitar")
    public ResponseEntity<String> solicitarApelacion(@PathVariable String sesionId, @RequestBody String argumentoEstudiante, org.springframework.security.core.Authentication authentication) {
        autorizacion.exigirAccesoASesion(authentication, sesionId);
        try {
            //Llamamos a apelacionService para solicitar la apelación del estudiante
            apelacionService.solicitarApelacion(sesionId, argumentoEstudiante);
            
            //Retornamos una respuesta de exito
            return ResponseEntity.ok("La apelación fue enviada al comité exitosamente.");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error al enviar apelación: " + e.getMessage());
        }
    }

    @GetMapping("/profesor/{profesorId}/pendientes")
    public ResponseEntity<?> obtenerPendientes(@PathVariable String profesorId, org.springframework.security.core.Authentication authentication) {
        autorizacion.exigirMismoProfesor(authentication, profesorId);
        try {
            //Buscamos y obtenemos la lista de sesiones que tienen una apelación pendiente para el profesor
            List<SesionSupervision> apelaciones = apelacionService.obtenerApelacionesPendientes(profesorId);
            
            //Luego retornamos esa lista de apelaciones para que el profesor pueda verlas 
            return ResponseEntity.ok(apelaciones);
        } catch (Exception e) {
            return ResponseEntity.status(404).body("Error al buscar apelaciones: " + e.getMessage());
        }
    }
    @PutMapping("/{sesionId}/resolver")
    public ResponseEntity<String> resolverApelacion(@PathVariable String sesionId, @RequestParam String resolucionComite, @RequestParam EstadoApelacion estadoApelacion, org.springframework.security.core.Authentication authentication) {
        autorizacion.exigirAccesoASesion(authentication, sesionId);
        
        try {
            //Llamamos al método para terminar la apelacion del estudiante
            apelacionService.terminarApelacion(sesionId, resolucionComite, estadoApelacion);
            return ResponseEntity.ok("La apelación ha sido resuelta y cerrada.");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error al resolver apelación: " + e.getMessage());
        }
    }
    
}
