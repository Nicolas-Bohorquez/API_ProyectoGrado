package com.example.AppSegurity.Services;

import com.example.AppSegurity.Enums.EstadoUsuario;
import com.example.AppSegurity.Models.Estudiante;
import com.example.AppSegurity.Models.Profesor;
import com.example.AppSegurity.Repositorys.EstudianteRepository;
import com.example.AppSegurity.Repositorys.ExamenRepository;
import com.example.AppSegurity.Repositorys.ProfesorRepository;
import com.example.AppSegurity.Sub_Clases.Auditoria;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AdminService.class);
    private static final String ROL_PROFESOR = "PROFESOR";
    private static final String ROL_SUPERADMIN = "SUPERADMIN";
    private static final String ALFABETO_CLAVE = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private final SecureRandom random = new SecureRandom();

    @Autowired
    private ProfesorRepository profesorRepository;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private ExamenRepository examenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Map<String, Object> obtenerResumen() {
        Map<String, Object> resumen = new HashMap<>();
        long totalProfes = profesorRepository.count();
        long totalEstudiantes = estudianteRepository.count();
        long totalExamenes = examenRepository.count();

        long profesActivos = profesorRepository.findAll().stream()
                .filter(p -> p.getEstado() == EstadoUsuario.ACTIVO)
                .count();
        long estudiantesActivos = estudianteRepository.findAll().stream()
                .filter(e -> e.getEstadoUsuario() == EstadoUsuario.ACTIVO)
                .count();

        resumen.put("totalProfesores", totalProfes);
        resumen.put("totalEstudiantes", totalEstudiantes);
        resumen.put("totalExamenes", totalExamenes);
        resumen.put("profesoresActivos", profesActivos);
        resumen.put("estudiantesActivos", estudiantesActivos);
        return resumen;
    }

    public List<Profesor> listarProfesores() {
        return profesorRepository.findAll();
    }

    public List<Estudiante> listarEstudiantes() {
        return estudianteRepository.findAll();
    }

    public void cambiarEstadoProfesor(String codigoProfesor, String nuevoEstado) {
        Profesor prof = obtenerProfesor(codigoProfesor);
        if (esSuperAdmin(prof)) {
            throw new IllegalArgumentException("No se puede modificar el estado de una cuenta SuperADMIN.");
        }
        prof.setEstado(EstadoUsuario.valueOf(nuevoEstado.toUpperCase().trim()));
        tocarAuditoria(prof);
        profesorRepository.save(prof);
    }

    /**
     * Promueve un docente a SUPERADMIN o lo degrada a PROFESOR dejando rastro de auditoría.
     * Impide dejar el sistema sin al menos un SUPERADMIN y evita que un administrador se quite su propio rol.
     */
    public void cambiarRolProfesor(String codigoProfesor, String nuevoRol, String actorEmail) {
        String rolNormalizado = nuevoRol == null ? "" : nuevoRol.trim().toUpperCase();
        if (!ROL_PROFESOR.equals(rolNormalizado) && !ROL_SUPERADMIN.equals(rolNormalizado)) {
            throw new IllegalArgumentException("El rol debe ser PROFESOR o SUPERADMIN.");
        }
        Profesor prof = obtenerProfesor(codigoProfesor);
        String rolAnterior = (prof.getRol() == null || prof.getRol().isBlank()) ? ROL_PROFESOR : prof.getRol().trim().toUpperCase();
        if (rolNormalizado.equals(rolAnterior)) {
            return;
        }
        if (ROL_SUPERADMIN.equals(rolNormalizado) && prof.getEstado() != EstadoUsuario.ACTIVO) {
            throw new IllegalArgumentException("Solo se puede promover a SuperADMIN una cuenta activa.");
        }
        if (ROL_PROFESOR.equals(rolNormalizado) && esSuperAdmin(prof)) {
            if (actorEmail != null && actorEmail.equalsIgnoreCase(prof.getEmailInstitucional())) {
                throw new IllegalArgumentException("No puedes quitarte el rol SuperADMIN a tu propia cuenta.");
            }
            long totalSuperAdmins = profesorRepository.findAll().stream()
                    .filter(p -> esSuperAdmin(p) && p.getEstado() == EstadoUsuario.ACTIVO)
                    .count();
            if (totalSuperAdmins <= 1) {
                throw new IllegalArgumentException("Debe existir al menos una cuenta SuperADMIN en el sistema.");
            }
        }
        prof.setRol(rolNormalizado);
        tocarAuditoria(prof);
        profesorRepository.save(prof);
        LOG.warn("[ADMIN-AUDITORIA] {} cambió el rol de {} ({}) de {} a {}",
                actorEmail != null ? actorEmail : "desconocido",
                prof.getCodigoProfesor(),
                prof.getEmailInstitucional(),
                rolAnterior,
                rolNormalizado);
    }

    public void cambiarEstadoEstudiante(String estudianteId, String nuevoEstado) {
        Estudiante est = obtenerEstudiante(estudianteId);
        est.setEstadoUsuario(EstadoUsuario.valueOf(nuevoEstado.toUpperCase().trim()));
        tocarAuditoria(est);
        estudianteRepository.save(est);
    }

    // ---------------------------------------------------------------- Edición

    public void editarProfesor(String codigoProfesor, Map<String, String> datos) {
        Profesor prof = obtenerProfesor(codigoProfesor);
        if (esSuperAdmin(prof)) {
            throw new IllegalArgumentException("No se puede editar una cuenta SuperADMIN.");
        }
        String nombre = requerido(datos, "nombre");
        String apellidos = requerido(datos, "apellidos");
        String cedula = requerido(datos, "cedula");
        String email = validarCorreo(requerido(datos, "email"));

        profesorRepository.findByCedula(cedula).ifPresent(otro -> {
            if (!otro.getCodigoProfesor().equals(codigoProfesor)) {
                throw new IllegalArgumentException("Ya existe otro docente con la cédula " + cedula + ".");
            }
        });
        profesorRepository.findByEmailInstitucional(email).ifPresent(otro -> {
            if (!otro.getCodigoProfesor().equals(codigoProfesor)) {
                throw new IllegalArgumentException("El correo " + email + " ya está en uso por otro docente.");
            }
        });

        prof.setNombre(nombre);
        prof.setApellidos(apellidos);
        prof.setCedula(cedula);
        prof.setEmailInstitucional(email);
        tocarAuditoria(prof);
        profesorRepository.save(prof);
    }

    public void editarEstudiante(String estudianteId, Map<String, String> datos) {
        Estudiante est = obtenerEstudiante(estudianteId);
        String nombre = requerido(datos, "nombre");
        String apellidos = requerido(datos, "apellidos");
        String cedula = requerido(datos, "cedula");
        String email = validarCorreo(requerido(datos, "email"));

        estudianteRepository.findByCedula(cedula).ifPresent(otro -> {
            if (!otro.getEstudianteId().equals(estudianteId)) {
                throw new IllegalArgumentException("Ya existe otro estudiante con la cédula " + cedula + ".");
            }
        });
        estudianteRepository.findByEmailIgnoreCase(email).forEach(otro -> {
            if (!otro.getEstudianteId().equals(estudianteId)) {
                throw new IllegalArgumentException("El correo " + email + " ya está en uso por otro estudiante.");
            }
        });

        est.setNombre(nombre);
        est.setApellidos(apellidos);
        est.setCedula(cedula);
        est.setEmail(email);
        tocarAuditoria(est);
        estudianteRepository.save(est);
    }

    // --------------------------------------------------------------- Creación

    public Map<String, String> crearProfesor(Map<String, String> datos) {
        String nombre = requerido(datos, "nombre");
        String apellidos = requerido(datos, "apellidos");
        String cedula = requerido(datos, "cedula");
        String email = validarCorreo(requerido(datos, "email"));

        if (profesorRepository.findByCedula(cedula).isPresent()) {
            throw new IllegalArgumentException("Ya existe un docente con la cédula " + cedula + ".");
        }
        if (profesorRepository.findByEmailInstitucional(email).isPresent()) {
            throw new IllegalArgumentException("Ya existe un docente con el correo " + email + ".");
        }

        String codigo = "PROF-" + cedula;
        String claveTemporal = generarClaveTemporal();

        Profesor nuevo = new Profesor();
        nuevo.setCodigoProfesor(codigo);
        nuevo.setNombre(nombre);
        nuevo.setApellidos(apellidos);
        nuevo.setCedula(cedula);
        nuevo.setEmailInstitucional(email);
        nuevo.setPasswordHash(passwordEncoder.encode(claveTemporal));
        nuevo.setEstado(EstadoUsuario.ACTIVO);
        nuevo.setMaterias(new ArrayList<>());
        nuevo.setRol("PROFESOR");
        nuevo.setAuditoria(new Auditoria(LocalDateTime.now()));
        profesorRepository.save(nuevo);

        Map<String, String> res = new HashMap<>();
        res.put("codigo", codigo);
        res.put("claveTemporal", claveTemporal);
        return res;
    }

    public Map<String, String> crearEstudiante(Map<String, String> datos) {
        String nombre = requerido(datos, "nombre");
        String apellidos = requerido(datos, "apellidos");
        String cedula = requerido(datos, "cedula");
        String email = validarCorreo(requerido(datos, "email"));

        if (estudianteRepository.findByCedula(cedula).isPresent()) {
            throw new IllegalArgumentException("Ya existe un estudiante con la cédula " + cedula + ".");
        }
        if (!estudianteRepository.findByEmailIgnoreCase(email).isEmpty()) {
            throw new IllegalArgumentException("Ya existe un estudiante con el correo " + email + ".");
        }

        String codigo = "EST-" + cedula;
        String claveTemporal = generarClaveTemporal();

        Estudiante nuevo = new Estudiante();
        nuevo.setEstudianteId(codigo);
        nuevo.setNombre(nombre);
        nuevo.setApellidos(apellidos);
        nuevo.setCedula(cedula);
        nuevo.setEmail(email);
        nuevo.setPasswordHash(passwordEncoder.encode(claveTemporal));
        nuevo.setEstadoUsuario(EstadoUsuario.ACTIVO);
        nuevo.setMateriasInscritas(new ArrayList<>());
        nuevo.setAuditoria(new Auditoria(LocalDateTime.now()));
        estudianteRepository.save(nuevo);

        Map<String, String> res = new HashMap<>();
        res.put("codigo", codigo);
        res.put("claveTemporal", claveTemporal);
        return res;
    }

    // ------------------------------------------------- Restablecer contraseña

    public String restablecerClaveProfesor(String codigoProfesor) {
        Profesor prof = obtenerProfesor(codigoProfesor);
        String clave = generarClaveTemporal();
        prof.setPasswordHash(passwordEncoder.encode(clave));
        tocarAuditoria(prof);
        profesorRepository.save(prof);
        return clave;
    }

    public String restablecerClaveEstudiante(String estudianteId) {
        Estudiante est = obtenerEstudiante(estudianteId);
        String clave = generarClaveTemporal();
        est.setPasswordHash(passwordEncoder.encode(clave));
        tocarAuditoria(est);
        estudianteRepository.save(est);
        return clave;
    }

    // -------------------------------------------------------------- Eliminar

    public void eliminarProfesor(String codigoProfesor) {
        Profesor prof = obtenerProfesor(codigoProfesor);
        if (esSuperAdmin(prof)) {
            throw new IllegalArgumentException("No se puede eliminar una cuenta SuperADMIN.");
        }
        profesorRepository.delete(prof);
    }

    public void eliminarEstudiante(String estudianteId) {
        estudianteRepository.delete(obtenerEstudiante(estudianteId));
    }

    // --------------------------------------------------------------- Helpers

    private Profesor obtenerProfesor(String codigo) {
        return profesorRepository.findByCodigoProfesor(codigo)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el docente con código: " + codigo));
    }

    private Estudiante obtenerEstudiante(String id) {
        return estudianteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el estudiante con ID: " + id));
    }

    private boolean esSuperAdmin(Profesor prof) {
        return ROL_SUPERADMIN.equalsIgnoreCase(prof.getRol());
    }

    private String requerido(Map<String, String> datos, String campo) {
        String valor = datos == null ? null : datos.get(campo);
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("El campo '" + campo + "' es obligatorio.");
        }
        return valor.trim();
    }

    private String validarCorreo(String email) {
        String limpio = email.trim().toLowerCase();
        if (!limpio.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("El correo electrónico no tiene un formato válido.");
        }
        return limpio;
    }

    private String generarClaveTemporal() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(ALFABETO_CLAVE.charAt(random.nextInt(ALFABETO_CLAVE.length())));
        }
        return sb.toString();
    }

    private void tocarAuditoria(Profesor prof) {
        if (prof.getAuditoria() == null) {
            prof.setAuditoria(new Auditoria());
        }
        prof.getAuditoria().setFechaActualizacion(LocalDateTime.now());
    }

    private void tocarAuditoria(Estudiante est) {
        if (est.getAuditoria() == null) {
            est.setAuditoria(new Auditoria());
        }
        est.getAuditoria().setFechaActualizacion(LocalDateTime.now());
    }
}
