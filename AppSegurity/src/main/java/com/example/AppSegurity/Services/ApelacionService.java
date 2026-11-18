package com.example.AppSegurity.Services;

import com.example.AppSegurity.Enums.ApelacionSolicitada;
import com.example.AppSegurity.Enums.EstadoApelacion;
import com.example.AppSegurity.Enums.EstadoSesion;
import com.example.AppSegurity.Models.Examen;
import com.example.AppSegurity.Models.SesionSupervision;
import com.example.AppSegurity.Models.Estudiante;
import com.example.AppSegurity.Repositorys.ExamenRepository;
import com.example.AppSegurity.Repositorys.SesionSupervisionRepository;
import com.example.AppSegurity.Repositorys.EstudianteRepository;
import com.example.AppSegurity.Security.ErrorNegocioException;
import com.example.AppSegurity.Sub_Clases.Proceso_apelacion;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ApelacionService {

    /* Metodos a utilizar en el service de apelación
    
    solicitarApelacion() El estudiante podra solicitar una apelación
    resolverApelacion() Se resolvera la apelación 
    obtenerApelacionesPendientes() traera todas las apelaciones aun pendientes
     */
    //Parametro de la clase
    @Autowired
    private SesionSupervisionRepository sesionSupervisionRepository;
    
    @Autowired
    private ExamenRepository examenRepository;
    
    @Autowired
    private EstudianteRepository estudianteRepository;
    
    @Autowired
    private EmailService emailService;

    public void solicitarApelacion(String sesionId, String argumentoEstudiante) {
        // Se busca el id de la sesión donde el estudiante hizo la apelación
        SesionSupervision sesionSupervision = sesionSupervisionRepository.findById(sesionId)
                .orElseThrow(() -> new ErrorNegocioException("No se encontró la sesión"));

        if (sesionSupervision.getApelacion() != null
                && sesionSupervision.getApelacion().getApelacionSolicitada() == ApelacionSolicitada.SOLICITADA) {
            throw new ErrorNegocioException("Ya existe una apelación registrada para esta sesión.");
        }

        //Crear el proceso de apelación del estudiante
        Proceso_apelacion procesoApelacion = new Proceso_apelacion();

        // Se llena los datos necesarios para realizarla
        //1. Solicitu de la apelación
        procesoApelacion.setApelacionSolicitada(ApelacionSolicitada.SOLICITADA);

        //2. Fecha de la solicitud
        procesoApelacion.setFechaSolicitud(LocalDateTime.now());

        //3. Argumento del estudiante para hacer la apelación
        procesoApelacion.setArgumentoEstudiante(argumentoEstudiante);

        //4. Estado de la apleación
        procesoApelacion.setEstadoApelacion(EstadoApelacion.EN_REVISION);

        //5. Se añade la apelación a la sesion de supervisión
        sesionSupervision.setApelacion(procesoApelacion);

        //6. Actualizar el estado de la sesion de supervision del estudiante
        sesionSupervision.setEstadoSesion(EstadoSesion.APELACION_CURSO);

        //7. Guardar la sesionSupervision con todos los cambios
        sesionSupervisionRepository.save(sesionSupervision);
    }

    public List<SesionSupervision> obtenerApelacionesPendientes(String profesorId) {

        //1. Buscar todos los examenes que ha creado el profesor
        List<Examen> examenesProfesor = examenRepository.findByProfesorId(profesorId);

        //2. Se extrae los Ids de los examenes obtenidos de la base de datos
        List<String> idsExamenes = examenesProfesor.stream().map(Examen::getCodigoExamen).toList();

        //En caso de que el profesor sea nuevo y no tenga examenes registrados aun se devolvera una lista vacia
        if (idsExamenes.isEmpty()) {
            return new ArrayList<>();
        }

        List<SesionSupervision> todas = sesionSupervisionRepository.findByExamenIdIn(idsExamenes);
        List<SesionSupervision> sesiones = new ArrayList<>();
        for (SesionSupervision s : todas) {
            if (s.getApelacion() != null
                    && s.getApelacion().getApelacionSolicitada() == ApelacionSolicitada.SOLICITADA) {
                sesiones.add(s);
            }
        }
        
        if (sesiones.isEmpty()) {
            return sesiones;
        }

        // Consultar todos los estudiantes en un único lote para evitar consultas N+1
        java.util.Set<String> estudianteIds = new java.util.HashSet<>();
        for (SesionSupervision s : sesiones) {
            if (s.getEstudianteId() != null) {
                estudianteIds.add(s.getEstudianteId());
            }
        }
        java.util.Map<String, Estudiante> estudiantesPorId = new java.util.HashMap<>();
        if (!estudianteIds.isEmpty()) {
            for (Estudiante est : estudianteRepository.findAllById(estudianteIds)) {
                estudiantesPorId.put(est.getEstudianteId(), est);
            }
        }

        for (SesionSupervision s : sesiones) {
            Estudiante est = s.getEstudianteId() != null ? estudiantesPorId.get(s.getEstudianteId()) : null;
            if (est != null) {
                s.setNombreEstudiante(est.getNombre() + " " + est.getApellidos());
            }
        }
        
        return sesiones;
    }
    
    public void terminarApelacion(String sesionId, String resolucionComite, EstadoApelacion estadoApelacion) {
        if (estadoApelacion != EstadoApelacion.APROBADA_A_FAVOR
                && estadoApelacion != EstadoApelacion.RECHAZADA_FRAUDE_MANTENIDO) {
            throw new ErrorNegocioException("El estado de resolución debe ser APROBADA_A_FAVOR o RECHAZADA_FRAUDE_MANTENIDO.");
        }

        // Buscamos la sesión donde ocurrió la apelación
        SesionSupervision sesionSupervision = sesionSupervisionRepository.findById(sesionId)
                .orElseThrow(() -> new ErrorNegocioException("La sesión no existe"));
        
        // Verificamos que sí tenga un proceso de apelación activo
        if (sesionSupervision.getApelacion() != null) {
            EstadoApelacion estadoActual = sesionSupervision.getApelacion().getEstadoApelacion();
            if (estadoActual == EstadoApelacion.APROBADA_A_FAVOR
                    || estadoActual == EstadoApelacion.RECHAZADA_FRAUDE_MANTENIDO) {
                throw new ErrorNegocioException("Esta apelación ya fue resuelta previamente.");
            }
            
            // Le guardamos el comentario o resolución del comité
            sesionSupervision.getApelacion().setResolucionComite(resolucionComite);
            
            // Le cambiamos el estado
            sesionSupervision.getApelacion().setEstadoApelacion(estadoApelacion); 
            
            if (estadoApelacion == EstadoApelacion.APROBADA_A_FAVOR) {
                sesionSupervision.setEstadoSesion(EstadoSesion.FINALIZADA);
            } else {
                sesionSupervision.setEstadoSesion(EstadoSesion.ANULADA);
            }
            
            // Guardamos en la base de datos
            sesionSupervisionRepository.save(sesionSupervision);

            // Buscar al estudiante y enviarle un correo
            Estudiante est = estudianteRepository.findById(sesionSupervision.getEstudianteId()).orElse(null);
            if(est != null) {
                boolean esAprobada = (estadoApelacion == EstadoApelacion.APROBADA_A_FAVOR);
                emailService.enviarResolucionApelacion(est.getEmail(), est.getNombre() + " " + est.getApellidos(), sesionSupervision.getExamenId(), esAprobada, resolucionComite);
            }
            
        } else {
            throw new ErrorNegocioException("Esta sesión no tiene ninguna apelación solicitada.");
        }
    }
}
