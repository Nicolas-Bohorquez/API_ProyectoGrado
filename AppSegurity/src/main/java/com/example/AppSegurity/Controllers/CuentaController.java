package com.example.AppSegurity.Controllers;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.AppSegurity.DTO.CambiarClaveRequest;
import com.example.AppSegurity.Models.Estudiante;
import com.example.AppSegurity.Repositorys.EstudianteRepository;
import com.example.AppSegurity.Security.IntentosExcedidosException;
import com.example.AppSegurity.Security.LimitadorIntentos;
import jakarta.validation.Valid;

/** Cambio de contraseña del propio estudiante (obligatorio en el primer ingreso con una clave temporal). */
@RestController
@RequestMapping("/api/estudiante")
public class CuentaController {

    static final int LARGO_MINIMO = 8;

    @Autowired
    private EstudianteRepository estudianteRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private LimitadorIntentos limitador;

    @PostMapping("/cambiar-clave")
    public ResponseEntity<?> cambiarClave(@Valid @RequestBody CambiarClaveRequest cuerpo, Authentication authentication) {
        String clave = "cambio:" + authentication.getName();
        try {
            limitador.verificar(clave);
            String actual = cuerpo.getClaveActual();
            String nueva = cuerpo.getClaveNueva();
            List<Estudiante> cuentas = estudianteRepository.findByEmailIgnoreCase(authentication.getName());
            if (cuentas.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("exito", false, "mensaje", "Cuenta no encontrada."));
            }
            Estudiante est = cuentas.get(0);
            if (!passwordEncoder.matches(actual, est.getPasswordHash())) {
                limitador.registrarFallo(clave);
                return ResponseEntity.status(400).body(Map.of("exito", false, "mensaje", "La clave actual no es correcta."));
            }
            String problema = validarClaveNueva(nueva, actual);
            if (problema != null) {
                return ResponseEntity.status(400).body(Map.of("exito", false, "mensaje", problema));
            }
            est.setPasswordHash(passwordEncoder.encode(nueva));
            est.setClaveTemporal(false);
            if (est.getSeguridadJwt() == null) {
                est.setSeguridadJwt(new com.example.AppSegurity.Sub_Clases.SeguridadJwt());
            }
            est.getSeguridadJwt().incrementarVersionToken();
            estudianteRepository.save(est);
            limitador.registrarExito(clave);
            return ResponseEntity.ok(Map.of("exito", true, "mensaje", "Clave actualizada."));
        } catch (IntentosExcedidosException e) {
            return ResponseEntity.status(429).body(Map.of("exito", false, "mensaje", e.getMessage()));
        }
    }

    // Un handler local (no el de ManejadorErroresGlobal) para conservar la forma de respuesta
    // "exito"/"mensaje" que ya usa este controller en vez de la "error" de los demás.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> datosInvalidos(MethodArgumentNotValidException e) {
        String mensaje = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("Los datos enviados no son válidos.");
        return ResponseEntity.status(400).body(Map.of("exito", false, "mensaje", mensaje));
    }

    /** Política mínima: 8+ caracteres, distinta de la actual, con letras y números, y no una clave trivial. */
    static String validarClaveNueva(String nueva, String actual) {
        if (nueva.length() < LARGO_MINIMO) return "La nueva clave debe tener al menos " + LARGO_MINIMO + " caracteres.";
        if (nueva.equals(actual)) return "La nueva clave debe ser distinta de la actual.";
        if (!nueva.matches(".*[A-Za-z].*") || !nueva.matches(".*[0-9].*")) return "La nueva clave debe combinar letras y números.";
        String minuscula = nueva.toLowerCase();
        for (String trivial : new String[] {"12345678", "123456789", "password1", "contrasena1", "qwerty123", "abc12345"}) {
            if (minuscula.equals(trivial)) return "Esa clave es demasiado común. Elige otra.";
        }
        return null;
    }
}
