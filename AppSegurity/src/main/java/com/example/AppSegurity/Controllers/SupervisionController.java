package com.example.AppSegurity.Controllers;

import com.example.AppSegurity.DTO.IniciarSesionRequest;
import com.example.AppSegurity.DTO.NuevaAlertaRequest;
import com.example.AppSegurity.Models.AlertaEvidencia;
import com.example.AppSegurity.Models.SesionSupervision;
import com.example.AppSegurity.Security.AutorizacionService;
import com.example.AppSegurity.Services.SesionSupervisionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import com.example.AppSegurity.Sub_Clases.AnalisisGlobal_IA;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import java.nio.file.Path;
import java.nio.file.Paths;


@RestController
@RequestMapping("/api/supervision")

public class SupervisionController {

    @Autowired
    private SesionSupervisionService sesionSupervisionService;
    @Autowired
    private AutorizacionService autorizacion;

    @PostMapping("/iniciar")
    public ResponseEntity<?> iniciarSesionSupervision(@RequestBody IniciarSesionRequest peticion, Authentication authentication) {
        // La identidad sale del TOKEN, no del cuerpo: un estudiante solo puede iniciar sesión como él mismo
        String estudianteIdSeguro = peticion.getEstudianteId();
        if (AutorizacionService.esEstudiante(authentication)) {
            com.example.AppSegurity.Models.Estudiante yo = autorizacion.estudianteActual(authentication)
                    .orElseThrow(() -> new AccessDeniedException("No se encontró al estudiante del token"));
            String enviado = peticion.getEstudianteId();
            if (enviado != null && !enviado.equalsIgnoreCase(yo.getEstudianteId()) && !enviado.equalsIgnoreCase(yo.getEmail())) {
                throw new AccessDeniedException("No puede iniciar una sesión a nombre de otro estudiante");
            }
            estudianteIdSeguro = yo.getEstudianteId();
        }
        try {
            //Iniciamos una sesion de supervision par el estudiante
            SesionSupervision sesionSupervision = sesionSupervisionService.iniciarSesion(estudianteIdSeguro, peticion.getPinExamen(), peticion.getConexion());

            //Retornamos la sesion iniciada
            return ResponseEntity.ok(sesionSupervision);

        } catch (Exception e) {
            return ResponseEntity.status(403).body("Acceso denegado: " + e.getMessage());
        }
    }

    //Metodos POST
    @PostMapping("/{sesionId}/alerta")
    public ResponseEntity<?> registrarAlerta(@PathVariable String sesionId, @RequestBody NuevaAlertaRequest alertaRequest, Authentication authentication) {
        autorizacion.exigirAccesoASesion(authentication, sesionId);
        try {
            //Llamamos a supervisionService y registramos la alerta
            sesionSupervisionService.registrarAlerta(sesionId, alertaRequest);

            //retornamos una respuesta que todo salio bien
            return ResponseEntity.ok("Alerta guardada exitosamente");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error al registrar la alerta: " + e.getMessage());

        }
    }

    @PostMapping("/{sesionId}/finalizar")
    public ResponseEntity<?> finalizarSesion(@PathVariable String sesionId, Authentication authentication) {
        autorizacion.exigirAccesoASesion(authentication, sesionId);
        try {
            //Llamamos a supervisionService y damos por finalizado Sesion
            sesionSupervisionService.finalizarSesion(sesionId);

            //Retornamos una respuesta de que se finalizo correctamente la sesión
            return ResponseEntity.ok("La sesión fue finalizada exitosamente");

        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error al finalizar la sesión: " + e.getMessage());
        }
    }

    @PostMapping("/{sesionId}/veredicto")
    public ResponseEntity<?> guardarVeredicto(@PathVariable String sesionId, @RequestBody AnalisisGlobal_IA analisis_ia, @RequestParam String veredictoDocente, @RequestParam String urlPDF, Authentication authentication) {
        autorizacion.exigirAccesoASesion(authentication, sesionId);
        try {
            //Llamamos a supervisionService y guardamos el veredicto 
            sesionSupervisionService.guardarVeredicto_Reporte(sesionId, analisis_ia, veredictoDocente, urlPDF);

            //Retornamos un mensaje de exito 
            return ResponseEntity.ok("Se guardo el veredicto correctamente");

        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error al guardar el veredicto: " + e.getMessage());

        }
    }

    //Metodo GET

    @GetMapping("/evidencia/descargar")
    public ResponseEntity<Resource> descargarEvidencia(@RequestParam String ruta, Authentication authentication) {
        try {
            String BASE_DIR = System.getProperty("user.home") + "/Documents/DataSupervision/Examenes/";
            Path baseDir = Paths.get(BASE_DIR).toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(ruta).normalize();

            // Bloquea path traversal (../ o rutas absolutas): el archivo debe quedar dentro de la carpeta base
            if (!filePath.startsWith(baseDir)) {
                return ResponseEntity.status(403).build();
            }

            // Solo el docente dueño del examen de esa sesión (o un administrador) puede bajar la evidencia. Se autoriza
            // con la ruta YA normalizada, así que los '..' no sirven para saltarse la comprobación.
            autorizacion.exigirEvidencia(authentication, baseDir.relativize(filePath));

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && java.nio.file.Files.isRegularFile(filePath)) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/{sesionId}/alertas")
    public ResponseEntity<?> obtenerAlertas(@PathVariable String sesionId, Authentication authentication) {
        autorizacion.exigirAccesoASesion(authentication, sesionId);
        try {
            //Creamos una lista de alerta y llamamos a supervisionService para obtener el historial de las alertas
            List<AlertaEvidencia> listaAlertas = sesionSupervisionService.obtenerHistorialAlertas(sesionId);

            //Retornamos la lista al profesor
            return ResponseEntity.ok(listaAlertas);
        } catch (Exception e) {
            return ResponseEntity.status(404).body("Error al obtener las alertas: " + e.getMessage());
        }

    }
    
    @GetMapping("/sesion/{sesionId}")
    public ResponseEntity<?> obtenerSesion(@PathVariable String sesionId, Authentication authentication) {
        autorizacion.exigirAccesoASesion(authentication, sesionId);
        try {
            return ResponseEntity.ok(sesionSupervisionService.obtenerSesion(sesionId));
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error: " + e.getMessage());
        }
    }
    
    @GetMapping("/examen/{codigoExamen}/alertas")
    public ResponseEntity<?> obtenerAlertasExamen(@PathVariable String codigoExamen, Authentication authentication) {
        autorizacion.exigirDocenteDeExamen(authentication, codigoExamen);
        try {
            List<AlertaEvidencia> listaAlertas = sesionSupervisionService.obtenerAlertasExamen(codigoExamen);
            return ResponseEntity.ok(listaAlertas);
        } catch (Exception e) {
            return ResponseEntity.status(404).body("Error al obtener las alertas globales: " + e.getMessage());
        }
    }
    
    @GetMapping("/reglas/{sesionId}")
    public ResponseEntity<?> obtenerReglasExamen(@PathVariable String sesionId, Authentication authentication) {
        autorizacion.exigirAccesoASesion(authentication, sesionId);
        try {
            com.example.AppSegurity.DTO.ReglasExamenResponse reglas = sesionSupervisionService.obtenerReglasExamen(sesionId);
            return ResponseEntity.ok(reglas);
        } catch (Exception e) {
            return ResponseEntity.status(404).body("Error al obtener reglas: " + e.getMessage());
        }
    }

    @GetMapping("/examen/{codigoExamen}/sesiones")
    public ResponseEntity<?> obtenerSesionesPorExamen(@PathVariable String codigoExamen, @RequestParam(required = false, defaultValue = "false") boolean todas, Authentication authentication) {
        autorizacion.exigirDocenteDeExamen(authentication, codigoExamen);
        try {
            List<SesionSupervision> sesiones = sesionSupervisionService.obtenerSesionesPorExamen(codigoExamen, todas);
            return ResponseEntity.ok(sesiones);
        } catch (Exception e) {
            return ResponseEntity.status(404).body("Error al obtener las sesiones: " + e.getMessage());
        }
    }

    @PostMapping("/{sesionId}/heartbeat")
    public ResponseEntity<?> registrarHeartbeat(@PathVariable String sesionId, @RequestParam(required = false) String deviceId, Authentication authentication) {
        autorizacion.exigirAccesoASesion(authentication, sesionId);
        try {
            SesionSupervision sesion = sesionSupervisionService.registrarHeartbeat(sesionId, deviceId);
            java.util.Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("sesionId", sesion.getSesionId());
            resp.put("estadoSesion", sesion.getEstadoSesion());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(403).body("Error en heartbeat: " + e.getMessage());
        }
    }

    @PostMapping("/{sesionId}/interrumpir")
    public ResponseEntity<?> interrumpirSesion(@PathVariable String sesionId, @RequestParam(required = false, defaultValue = "App cerrada abruptamente") String motivo, Authentication authentication) {
        autorizacion.exigirAccesoASesion(authentication, sesionId);
        try {
            sesionSupervisionService.marcarSesionInterrumpida(sesionId, motivo);
            return ResponseEntity.ok("Sesión marcada como interrumpida");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error al interrumpir sesión: " + e.getMessage());
        }
    }

}
