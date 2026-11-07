package com.example.AppSegurity.Controllers;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.AppSegurity.Models.Estudiante;
import com.example.AppSegurity.Repositorys.EstudianteRepository;
import com.example.AppSegurity.Security.IntentosExcedidosException;
import com.example.AppSegurity.Security.LimitadorIntentos;

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
    public ResponseEntity<?> cambiarClave(@RequestBody Map<String, String> cuerpo, Authentication authentication) {
        String clave = "cambio:" + authentication.getName();
        try {
            limitador.verificar(clave);
            String actual = cuerpo.get("claveActual");
            String nueva = cuerpo.get("claveNueva");
            if (actual == null || nueva == null) {
                return ResponseEntity.status(400).body(Map.of("exito", false, "mensaje", "Faltan la clave actual o la nueva."));
            }
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
            estudianteRepository.save(est);
            limitador.registrarExito(clave);
            return ResponseEntity.ok(Map.of("exito", true, "mensaje", "Clave actualizada."));
        } catch (IntentosExcedidosException e) {
            return ResponseEntity.status(429).body(Map.of("exito", false, "mensaje", e.getMessage()));
        }
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
