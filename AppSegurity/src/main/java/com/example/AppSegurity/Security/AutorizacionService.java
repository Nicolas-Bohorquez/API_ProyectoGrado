package com.example.AppSegurity.Security;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import com.example.AppSegurity.Models.Estudiante;
import com.example.AppSegurity.Models.Examen;
import com.example.AppSegurity.Models.Profesor;
import com.example.AppSegurity.Models.SesionSupervision;
import com.example.AppSegurity.Repositorys.EstudianteRepository;
import com.example.AppSegurity.Repositorys.ExamenRepository;
import com.example.AppSegurity.Repositorys.ProfesorRepository;
import com.example.AppSegurity.Repositorys.SesionSupervisionRepository;

/**
 * Autorización por PROPIEDAD de los datos (no solo por rol).
 *
 * El rol dice "es estudiante" o "es docente"; esto comprueba además que la sesión sea DE ESE estudiante y que el examen
 * sea DE ESE docente. Sin esto, cualquier estudiante autenticado podía leer, enviar o cerrar sesiones ajenas y cualquier
 * docente podía ver los exámenes y evidencias de otros.
 *
 * Regla común: si el recurso NO existe no se rechaza aquí (el servicio responde "no existe" como siempre); solo se
 * rechaza cuando existe y pertenece a otra persona. Los administradores (ROLE_ADMIN / ROLE_SUPERADMIN) pueden todo.
 */
@Service
public class AutorizacionService {

    @Autowired
    private ProfesorRepository profesorRepository;
    @Autowired
    private EstudianteRepository estudianteRepository;
    @Autowired
    private ExamenRepository examenRepository;
    @Autowired
    private SesionSupervisionRepository sesionSupervisionRepository;

    // ───────────── Roles ─────────────
    private static boolean tieneRol(Authentication a, String rol) {
        if (a == null || a.getAuthorities() == null) return false;
        for (GrantedAuthority g : a.getAuthorities()) {
            if (rol.equals(g.getAuthority())) return true;
        }
        return false;
    }

    public static boolean esAdmin(Authentication a) {
        return tieneRol(a, "ROLE_ADMIN") || tieneRol(a, "ROLE_SUPERADMIN");
    }

    public static boolean esEstudiante(Authentication a) {
        return tieneRol(a, "ROLE_ESTUDIANTE");
    }

    public static boolean esProfesor(Authentication a) {
        return tieneRol(a, "ROLE_PROFESOR");
    }

    // ───────────── Identidad (sale del JWT, nunca del cuerpo de la petición) ─────────────
    public Optional<Estudiante> estudianteActual(Authentication a) {
        if (a == null || a.getName() == null) return Optional.empty();
        List<Estudiante> candidatos = estudianteRepository.findByEmailIgnoreCase(a.getName());
        for (Estudiante e : candidatos) {
            if (e.getEstudianteId() != null && !e.getEstudianteId().contains(" ")) return Optional.of(e);
        }
        return candidatos.stream().findFirst();
    }

    public Optional<Profesor> profesorActual(Authentication a) {
        if (a == null || a.getName() == null) return Optional.empty();
        return profesorRepository.findByEmailInstitucional(a.getName());
    }

    private AccessDeniedException denegado(String motivo) {
        return new AccessDeniedException(motivo);
    }

    // ───────────── Exámenes (docente) ─────────────
    /** El examen debe ser del docente autenticado (o el usuario es administrador). Si el examen no existe, no rechaza. */
    public void exigirDocenteDeExamen(Authentication a, String examenId) {
        if (esAdmin(a)) return;
        if (examenId == null) return;
        Optional<Examen> examen = examenRepository.findById(examenId);
        if (examen.isEmpty()) return;
        exigirDocenteDe(a, examen.get());
    }

    private void exigirDocenteDe(Authentication a, Examen examen) {
        if (esAdmin(a)) return;
        Optional<Profesor> profesor = esProfesor(a) ? profesorActual(a) : Optional.empty();
        if (profesor.isEmpty() || examen.getProfesorId() == null
                || !examen.getProfesorId().equals(profesor.get().getCodigoProfesor())) {
            throw denegado("El examen no pertenece al docente autenticado");
        }
    }

    /** Para rutas /profesor/{profesorId}/...: solo el propio docente (o un administrador). */
    public void exigirMismoProfesor(Authentication a, String profesorId) {
        if (esAdmin(a)) return;
        Optional<Profesor> profesor = esProfesor(a) ? profesorActual(a) : Optional.empty();
        if (profesor.isEmpty() || profesorId == null || !profesorId.equals(profesor.get().getCodigoProfesor())) {
            throw denegado("No puede consultar los datos de otro docente");
        }
    }

    // ───────────── Sesiones (estudiante o docente) ─────────────
    /**
     * Acceso a una sesión: el estudiante dueño, el docente dueño del examen de esa sesión, o un administrador.
     * Si la sesión no existe no rechaza (el servicio responderá que no existe).
     */
    public void exigirAccesoASesion(Authentication a, String sesionId) {
        if (esAdmin(a)) return;
        if (sesionId == null) return;
        Optional<SesionSupervision> sesion = sesionSupervisionRepository.findById(sesionId);
        if (sesion.isEmpty()) return;
        exigirAccesoASesion(a, sesion.get());
    }

    public void exigirAccesoASesion(Authentication a, SesionSupervision sesion) {
        if (esAdmin(a)) return;
        if (esEstudiante(a)) {
            Optional<Estudiante> est = estudianteActual(a);
            if (est.isEmpty() || sesion.getEstudianteId() == null
                    || !sesion.getEstudianteId().equals(est.get().getEstudianteId())) {
                throw denegado("La sesión no pertenece al estudiante autenticado");
            }
            return;
        }
        Optional<Examen> examen = sesion.getExamenId() == null ? Optional.empty() : examenRepository.findById(sesion.getExamenId());
        if (examen.isEmpty()) {
            throw denegado("La sesión no está asociada a un examen del docente");
        }
        exigirDocenteDe(a, examen.get());
    }

    // ───────────── Variantes ESTRICTAS (WebSocket): el recurso debe existir ─────────────
    /** Como exigirAccesoASesion pero rechazando también sesiones que no existen (un destino STOMP inventado no es válido). */
    public void exigirAccesoASesionExistente(Authentication a, String sesionId) {
        Optional<SesionSupervision> sesion = sesionId == null ? Optional.empty() : sesionSupervisionRepository.findById(sesionId);
        if (sesion.isEmpty()) {
            throw denegado("La sesión no existe");
        }
        exigirAccesoASesion(a, sesion.get());
    }

    public void exigirDocenteDeExamenExistente(Authentication a, String examenId) {
        Optional<Examen> examen = examenId == null ? Optional.empty() : examenRepository.findById(examenId);
        if (examen.isEmpty()) {
            throw denegado("El examen no existe");
        }
        exigirDocenteDe(a, examen.get());
    }

    // ───────────── Evidencias en disco ─────────────
    /**
     * Una ruta de evidencia es "materia/estudiante/sesionId/categoria/archivo". Se autoriza por la sesión que aparece en la
     * ruta YA NORMALIZADA (`rutaRelativa` sale de `base.relativize(archivo.normalize())`): los trucos con '..' no sirven.
     * Si ningún tramo de la ruta es una sesión existente, solo un administrador puede descargar.
     */
    public void exigirEvidencia(Authentication a, Path rutaRelativa) {
        if (esAdmin(a)) return;
        if (rutaRelativa != null) {
            for (Path tramo : rutaRelativa) {
                Optional<SesionSupervision> sesion = sesionSupervisionRepository.findById(tramo.toString());
                if (sesion.isPresent()) {
                    exigirAccesoASesion(a, sesion.get());
                    return;
                }
            }
        }
        throw denegado("La evidencia solicitada no corresponde a una sesión de sus exámenes");
    }
}
