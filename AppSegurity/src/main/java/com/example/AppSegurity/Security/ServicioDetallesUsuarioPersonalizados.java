package com.example.AppSegurity.Security;

import com.example.AppSegurity.Models.Estudiante;
import com.example.AppSegurity.Models.Profesor;
import com.example.AppSegurity.Repositorys.EstudianteRepository;
import com.example.AppSegurity.Repositorys.ProfesorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Service
public class ServicioDetallesUsuarioPersonalizados implements UserDetailsService {

    @Autowired
    private ProfesorRepository profesorRepository;

    @Autowired
    private EstudianteRepository estudianteRepository;

    // Este es el famoso método que te marcaba en rojo
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        //1. Primero intentamos buscar si ese correo le pertenece a un Profesor
        Profesor profesor = profesorRepository.findByEmailInstitucional(email)
                .or(() -> profesorRepository.findByEmailInstitucionalIgnoreCase(email))
                .orElse(null);

        if (profesor != null) {
            if (profesor.getEstado() != null && profesor.getEstado() != com.example.AppSegurity.Enums.EstadoUsuario.ACTIVO) {
                throw new UsernameNotFoundException("La cuenta de docente no se encuentra activa: " + email);
            }
            //Si es un profesor, le devolvemos a Spring Security un usuario con sus datos.
            //Spring necesita saber el email, la contraseña (para verificarla luego) y una lista de roles.
            List<SimpleGrantedAuthority> roles = new ArrayList<>();
            roles.add(new SimpleGrantedAuthority("ROLE_PROFESOR")); //Etiqueta para el rol de profesor
            if ("SUPERADMIN".equalsIgnoreCase(profesor.getRol())) {
                roles.add(new SimpleGrantedAuthority("ROLE_SUPERADMIN"));
            }
            return new User(profesor.getEmailInstitucional(), profesor.getPasswordHash(), roles);
        }

        //2. Si no era un profesor, intentamos buscar si es un Estudiante
        java.util.List<Estudiante> estudiantes = estudianteRepository.findByEmailIgnoreCase(email);
        Estudiante estudiante = null;
        if (estudiantes.size() > 1) {
            for (Estudiante e : estudiantes) {
                if (e.getEstudianteId() != null && !e.getEstudianteId().contains(" ")) {
                    estudiante = e;
                    break;
                }
            }
            if (estudiante == null) {
                estudiante = estudiantes.get(0);
            }
        } else if (estudiantes.size() == 1) {
            estudiante = estudiantes.get(0);
        }

        if (estudiante != null) {
            if (estudiante.getEstadoUsuario() != null && estudiante.getEstadoUsuario() != com.example.AppSegurity.Enums.EstadoUsuario.ACTIVO) {
                throw new UsernameNotFoundException("La cuenta de estudiante no se encuentra activa: " + email);
            }
            //Si es estudiante, hacemos lo mismo
            List<SimpleGrantedAuthority> roles = new ArrayList<>();
            roles.add(new SimpleGrantedAuthority("ROLE_ESTUDIANTE")); //Etiqueta para el rol de estudiante
            return new User(estudiante.getEmail(), estudiante.getPasswordHash(), roles);
        }

        //3. Si no existe ni en profesores ni en estudiantes, entonces no está registrado
        throw new UsernameNotFoundException("Usuario no encontrado con el email: " + email);
    }
}
