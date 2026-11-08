package com.example.AppSegurity.Controllers;

import com.example.AppSegurity.DTO.LoginRequest;
import com.example.AppSegurity.Services.AutenticacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/auth")
public class AutenticacionController {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AutenticacionController.class);

    // El detalle técnico de un fallo (clase de la excepción, mensaje de Mongo, etc.) se queda en el log del servidor:
    // al cliente solo le llega un mensaje genérico. Los mensajes pensados para el usuario (400/409/429) no pasan por aquí.
    private ResponseEntity<?> loginRechazado(Exception e) {
        LOG.warn("[AUTH] Login rechazado: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        return ResponseEntity.status(401).body("Error de autenticación: Credenciales incorrectas");
    }

    private ResponseEntity<?> errorInterno(String mensaje, Exception e) {
        LOG.error("[AUTH] {} {}: {}", mensaje, e.getClass().getSimpleName(), e.getMessage());
        return ResponseEntity.status(500).body(Map.of("exito", false, "mensaje", mensaje + " Intenta de nuevo más tarde."));
    }

    @Autowired
    private AutenticacionService autenticacionService;

    @Autowired
    private com.example.AppSegurity.Repositorys.EstudianteRepository estudianteRepository;

    //Metodos Post
    @PostMapping("/login/estudiante")
    public ResponseEntity<?> loginEstudiante(@RequestBody LoginRequest loginRequest) {
        try {
            //Iniciamos el login del service pasandole el email y password y guardamos en una variable llamada token
            String token = autenticacionService.loginEstudiante(loginRequest.getEmail(), loginRequest.getPassword());
            Map<String, String> response = new HashMap<>();
            response.put("token", token);
            boolean debeCambiar = estudianteRepository.findByEmailIgnoreCase(loginRequest.getEmail()).stream()
                    .anyMatch(com.example.AppSegurity.Models.Estudiante::isClaveTemporal);
            response.put("claveTemporal", String.valueOf(debeCambiar));      // la app obliga a elegir una clave propia
            return ResponseEntity.ok(response);
        } catch (com.example.AppSegurity.Security.IntentosExcedidosException e) {
            return ResponseEntity.status(429).body("Error de autenticación: " + e.getMessage());
        } catch (Exception e) {
            return loginRechazado(e);
        }
    }
    
    @Autowired
    private com.example.AppSegurity.Repositorys.ProfesorRepository profesorRepository;

    @PostMapping("/login/profesor")
    public ResponseEntity<?> loginProfesor(@RequestBody LoginRequest loginRequest) {
        try {
            //Iniciamos el login del service pasandole el email y password y guardamos en una variable llamada token
            String token = autenticacionService.loginProfesor(loginRequest.getEmail(), loginRequest.getPassword());
            
            // Buscamos al profesor para obtener su ID real
            com.example.AppSegurity.Models.Profesor prof = profesorRepository.findByEmailInstitucional(loginRequest.getEmail()).orElse(null);
            
            Map<String, String> response = new HashMap<>();
            response.put("token", token);
            if(prof != null) {
                response.put("profesorId", prof.getCodigoProfesor());
                String rol = (prof.getRol() != null && !prof.getRol().isBlank()) ? prof.getRol() : "PROFESOR";
                if (prof.getCodigoProfesor() != null && prof.getCodigoProfesor().toUpperCase().startsWith("ADMIN")) {
                    rol = "SUPERADMIN";
                }
                response.put("rol", rol);
                response.put("nombre", ((prof.getNombre() != null ? prof.getNombre() : "") + " " + (prof.getApellidos() != null ? prof.getApellidos() : "")).trim());
            }
            return ResponseEntity.ok(response);
        } catch (com.example.AppSegurity.Security.IntentosExcedidosException e) {
            return ResponseEntity.status(429).body("Error de autenticación: " + e.getMessage());
        } catch (Exception e) {
            return loginRechazado(e);
        }
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<?> refrescarToken(@RequestHeader("Authorization") String tokenViejoHeader){
        try {
            //Extraemos el token viejo, cortamos y obtenemos los elementos esenciales del mismo para validarlo
            String tokenLimpio = tokenViejoHeader.substring(7);
            
            // Generamos el token para los estudiantes y docentes
            String nuevoToken = autenticacionService.refrescarToken(tokenLimpio);
            Map<String, String> response = new HashMap<>();
            response.put("token", nuevoToken);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(403).body("Token inválido o expirado");
        }
    }

    // --- ENDPOINTS DE AUTO-REGISTRO INSTITUCIONAL CON OTP ---

    @PostMapping("/registro/solicitar-codigo")
    public ResponseEntity<?> solicitarCodigoRegistro(@RequestBody com.example.AppSegurity.DTO.RegistroSolicitudRequest request) {
        try {
            autenticacionService.solicitarCodigoRegistro(request);
            Map<String, Object> resp = new HashMap<>();
            resp.put("exito", true);
            resp.put("mensaje", "Código de verificación enviado exitosamente a tu correo institucional.");
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("exito", false, "mensaje", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("exito", false, "mensaje", e.getMessage()));
        } catch (com.example.AppSegurity.Security.IntentosExcedidosException e) {
            return ResponseEntity.status(429).body(Map.of("exito", false, "mensaje", e.getMessage()));
        } catch (Exception e) {
            return errorInterno("Error interno al procesar el registro.", e);
        }
    }

    @PostMapping("/registro/confirmar-estudiante")
    public ResponseEntity<?> confirmarRegistroEstudiante(@RequestBody com.example.AppSegurity.DTO.RegistroConfirmarRequest request) {
        try {
            autenticacionService.confirmarRegistroEstudiante(request);
            Map<String, Object> resp = new HashMap<>();
            resp.put("exito", true);
            resp.put("mensaje", "Cuenta de estudiante creada y activada exitosamente. Ya puedes iniciar sesión.");
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("exito", false, "mensaje", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of("exito", false, "mensaje", e.getMessage()));
        } catch (com.example.AppSegurity.Security.IntentosExcedidosException e) {
            return ResponseEntity.status(429).body(Map.of("exito", false, "mensaje", e.getMessage()));
        } catch (Exception e) {
            return errorInterno("No se pudo completar el registro.", e);
        }
    }

    @PostMapping("/registro/confirmar-profesor")
    public ResponseEntity<?> confirmarRegistroProfesor(@RequestBody com.example.AppSegurity.DTO.RegistroConfirmarRequest request) {
        try {
            autenticacionService.confirmarRegistroProfesor(request);
            Map<String, Object> resp = new HashMap<>();
            resp.put("exito", true);
            resp.put("mensaje", "Cuenta docente creada y activada exitosamente. Ya puedes iniciar sesión.");
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("exito", false, "mensaje", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of("exito", false, "mensaje", e.getMessage()));
        } catch (com.example.AppSegurity.Security.IntentosExcedidosException e) {
            return ResponseEntity.status(429).body(Map.of("exito", false, "mensaje", e.getMessage()));
        } catch (Exception e) {
            return errorInterno("No se pudo completar el registro.", e);
        }
    }

    // --- ENDPOINTS DE RECUPERACIÓN DE CONTRASEÑA CON OTP ---

    @PostMapping("/recuperar/solicitar-codigo")
    public ResponseEntity<?> solicitarCodigoRecuperacion(@RequestBody com.example.AppSegurity.DTO.RecuperarSolicitudRequest request) {
        try {
            autenticacionService.solicitarCodigoRecuperacion(request);
            Map<String, Object> resp = new HashMap<>();
            resp.put("exito", true);
            resp.put("mensaje", "Código de recuperación enviado exitosamente a tu correo institucional.");
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("exito", false, "mensaje", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of("exito", false, "mensaje", e.getMessage()));
        } catch (com.example.AppSegurity.Security.IntentosExcedidosException e) {
            return ResponseEntity.status(429).body(Map.of("exito", false, "mensaje", e.getMessage()));
        } catch (Exception e) {
            return errorInterno("Error al procesar la recuperación.", e);
        }
    }

    @PostMapping("/recuperar/confirmar")
    public ResponseEntity<?> confirmarRecuperacionPassword(@RequestBody com.example.AppSegurity.DTO.RecuperarConfirmarRequest request) {
        try {
            autenticacionService.confirmarRecuperacionPassword(request);
            Map<String, Object> resp = new HashMap<>();
            resp.put("exito", true);
            resp.put("mensaje", "Contraseña restablecida exitosamente. Ya puedes iniciar sesión con tu nueva contraseña.");
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("exito", false, "mensaje", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of("exito", false, "mensaje", e.getMessage()));
        } catch (com.example.AppSegurity.Security.IntentosExcedidosException e) {
            return ResponseEntity.status(429).body(Map.of("exito", false, "mensaje", e.getMessage()));
        } catch (Exception e) {
            return errorInterno("Error al actualizar la contraseña.", e);
        }
    }
}
