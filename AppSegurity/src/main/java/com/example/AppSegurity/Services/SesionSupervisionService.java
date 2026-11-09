package com.example.AppSegurity.Services;

import com.example.AppSegurity.DTO.NuevaAlertaRequest;
import com.example.AppSegurity.Enums.ClaseAlerta;
import com.example.AppSegurity.Enums.EstadoSesion;
import com.example.AppSegurity.Enums.NivelRiesgo;
import com.example.AppSegurity.Models.Alerta.AlertaAudio;
import com.example.AppSegurity.Models.Alerta.AlertaProceso;
import com.example.AppSegurity.Models.Alerta.AlertaTeclado;
import com.example.AppSegurity.Models.Alerta.AlertaVision;
import com.example.AppSegurity.Models.AlertaEvidencia;
import com.example.AppSegurity.Models.Estudiante;
import com.example.AppSegurity.Models.Examen;
import com.example.AppSegurity.Models.SesionSupervision;
import com.example.AppSegurity.Repositorys.AlertaEvidenciaRepository;
import com.example.AppSegurity.Repositorys.EstudianteRepository;
import com.example.AppSegurity.Repositorys.ExamenRepository;
import com.example.AppSegurity.Repositorys.SesionSupervisionRepository;
import com.example.AppSegurity.Sub_Clases.AnalisisGlobal_IA;
import com.example.AppSegurity.Sub_Clases.Conexion;
import com.example.AppSegurity.Sub_Clases.Materia;
import com.example.AppSegurity.Sub_Clases.ReporteFinal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
public class SesionSupervisionService {

    @Autowired
    private SesionSupervisionRepository sesionSupervisionRepository;
    @Autowired
    private ExamenRepository examenRepository;
    @Autowired
    private AlertaEvidenciaRepository alertaEvidenciaRepository;
    @Autowired
    private EstudianteRepository estudianteRepository;
    @Autowired
    private SimpMessagingTemplate webSocketMessagingTemplate;
    

    public SesionSupervision iniciarSesion(String idEstudiante, String pinExamen, Conexion conexionInfo) {

        //Se busca y se extrae el PIN del examen
        Examen examen = examenRepository.findByControlAccesoPinSesion(pinExamen)
                .orElseThrow(() -> new RuntimeException("El PIN ingresado no existe o es incorrecto"));

        // Se trae el estudiante de la base de datos para ver las materias que tiene
        Estudiante estudiante = estudianteRepository.findById(idEstudiante)
                .orElseGet(() -> estudianteRepository.findByEmailIgnoreCase(idEstudiante).stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Estudiante no encontrado (ID o Email: " + idEstudiante + ")")));

        // Validamos que el profesor tenga el examen abierto (Estado_pin == ACTIVO)
        if (examen.getControlAcceso() == null || 
            examen.getControlAcceso().getEstadoPin() != com.example.AppSegurity.Enums.Estado_pin.ACTIVO) {
            throw new RuntimeException("El examen no está activo en este momento (Cerrado por el profesor)");
        }
        
        // Validación estricta de tiempo
        if (examen.getFechaExamen() != null) {
            java.time.LocalDateTime ahora = java.time.LocalDateTime.now();
            java.time.LocalDateTime horaInicio = examen.getFechaExamen().getHoraInicio();
            java.time.LocalDateTime horaFin = examen.getFechaExamen().getHoraFin();
            
            if (horaInicio != null && ahora.isBefore(horaInicio)) {
                throw new RuntimeException("El examen aún no ha comenzado. Inicia a las " + horaInicio.toString());
            }
            if (horaFin != null && ahora.isAfter(horaFin)) {
                throw new RuntimeException("El examen ya ha finalizado. Terminó a las " + horaFin.toString());
            }
        }

        //Validamos que el estudiante tenga la materia inscrita
        Boolean esta_inscrito = false;
        for (Materia materia : estudiante.getMateriasInscritas()) {
            if (materia.getCodigoMateria().equals(examen.getMateriaCodigo())) {
                esta_inscrito = true;
                break;
            }
        }

        //En caso de que no este inscrito
        if (!esta_inscrito) {
            throw new RuntimeException("No esta inscrito en la materia para hacer este examen");
        }

        //Validamos la información de la conexión
        //Bloqueamos VPNs o Proxies que puedan enmascarar al estudiante
        if (conexionInfo.getVpnDetectada() != false) {
            throw new RuntimeException("Acceso denegado: Se ha detectado una conexión VPN activa."
                    + " Por favor desactivarla para hacer el examen");
        }

        // Bloqueo por Sistema Operativo ya que nuestra App de Python solo corre en Windows
        if (conexionInfo.getSistemaOperativo() != null && !conexionInfo.getSistemaOperativo().toLowerCase().contains("windows")) {
            throw new RuntimeException("Acceso denegado: El sistema de supervisión actualmente solo es compatible con Windows.");
        }
        
        // --- NUEVAS VALIDACIONES: FECHAS ---
        if (examen.getFechaExamen() != null) {
            java.time.LocalDateTime ahora = java.time.LocalDateTime.now();
            if (examen.getFechaExamen().getHoraInicio() != null && ahora.isBefore(examen.getFechaExamen().getHoraInicio())) {
                throw new RuntimeException("El examen aún no ha comenzado. Empieza a las: " + examen.getFechaExamen().getHoraInicio());
            }
            if (examen.getFechaExamen().getHoraFin() != null && ahora.isAfter(examen.getFechaExamen().getHoraFin())) {
                throw new RuntimeException("El tiempo límite para iniciar el examen ha concluido (" + examen.getFechaExamen().getHoraFin() + ").");
            }
        }

        // Revisar si ya tiene una sesión iniciada para no duplicar tarjetas en el panel del profesor
        List<SesionSupervision> sesionesPrevias = sesionSupervisionRepository.findByExamenId(examen.getCodigoExamen());
        int intentosCompletados = 0;
        boolean accesoBloqueado = false;
        String motivoBloqueo = "";
        
        for (SesionSupervision s : sesionesPrevias) {
            if (s.getEstudianteId().equals(estudiante.getEstudianteId())) {
                if (s.getEstadoSesion() == EstadoSesion.INICIADA || s.getEstadoSesion() == EstadoSesion.INTERRUMPIDA) {
                    String devExistente = (s.getConexion() != null) ? s.getConexion().getDeviceId() : null;
                    String devEntrante = (conexionInfo != null) ? conexionInfo.getDeviceId() : null;

                    // Si ambos tienen deviceId y NO coinciden -> Intento de sesión simultánea en segundo equipo
                    if (devExistente != null && devEntrante != null && !devExistente.equalsIgnoreCase(devEntrante)) {
                        AlertaProceso alertaDup = new AlertaProceso(
                            0,
                            "INTENTO_SESION_DUPLICADA",
                            com.example.AppSegurity.Enums.CategoriaProceso.CONTROL_REMOTO,
                            com.example.AppSegurity.Enums.AccionTomada.BLOQUEADO,
                            null
                        );
                        alertaDup.setSesionId(s.getSesionId());
                        alertaDup.setClaseAlerta(ClaseAlerta.SESION_DUPLICADA);
                        alertaDup.setNivelRiesgo(NivelRiesgo.CRITICO);
                        alertaDup.setHoraCaptura(LocalDateTime.now());
                        alertaDup.setNombreEstudiante(estudiante.getNombre() + " " + estudiante.getApellidos());
                        alertaEvidenciaRepository.save(alertaDup);

                        try {
                            java.util.Map<String, Object> evDup = new java.util.HashMap<>();
                            evDup.put("tipoEvento", "SESION_DUPLICADA_BLOQUEADA");
                            evDup.put("sesionId", s.getSesionId());
                            evDup.put("estudianteId", s.getEstudianteId());
                            evDup.put("nombreEstudiante", estudiante.getNombre() + " " + estudiante.getApellidos());
                            evDup.put("ipIntruso", conexionInfo != null ? conexionInfo.getIpEstudiante() : "Desconocida");
                            evDup.put("macIntruso", conexionInfo != null ? conexionInfo.getDireccionMac() : "Desconocida");
                            evDup.put("mensaje", "Intento de inicio de sesión simultáneo en otro dispositivo bloqueado.");
                            webSocketMessagingTemplate.convertAndSend((String) ("/topic/alertas/" + s.getExamenId()), (Object) evDup);
                        } catch (Exception ignored) {}

                        throw new RuntimeException("Acceso denegado: Ya existe una sesión activa para este examen en otro equipo. Por motivos de integridad académica no se permite el acceso simultáneo desde múltiples dispositivos.");
                    }

                    // Mismo dispositivo: reconexión autorizada
                    s.setEstadoSesion(EstadoSesion.INICIADA);
                    if (conexionInfo != null) {
                        s.setConexion(conexionInfo);
                    }
                    s.asegurarClaveSesion();            // una sesión anterior a este cambio recibe su clave al reanudarse
                    sesionSupervisionRepository.save(s);

                    try {
                        java.util.Map<String, String> evento = new java.util.HashMap<>();
                        evento.put("tipoEvento", "ESTUDIANTE_UNIDO");
                        evento.put("sesionId", s.getSesionId());
                        evento.put("estudianteId", s.getEstudianteId());
                        webSocketMessagingTemplate.convertAndSend("/topic/alertas/" + s.getExamenId(), evento);
                    } catch (Exception e) {}
                    return s; // Reutiliza la sesion si ya estaba adentro en el mismo equipo
                } else if (s.getEstadoSesion() == EstadoSesion.FINALIZADA || s.getEstadoSesion() == EstadoSesion.ANULADA || s.getEstadoSesion() == EstadoSesion.APELACION_CURSO) {
                    intentosCompletados++;
                    
                    // Si fue anulada por fraude o esta en revision, activamos la bandera de bloqueo
                    if (s.getEstadoSesion() == EstadoSesion.ANULADA) {
                        accesoBloqueado = true;
                        motivoBloqueo = "Tu examen previo fue anulado por faltas al reglamento.";
                    } else if (s.getEstadoSesion() == EstadoSesion.APELACION_CURSO) {
                        accesoBloqueado = true;
                        motivoBloqueo = "Tu solicitud de Segunda Revision se encuentra en proceso.";
                    }
                }
            }
        }
        
        // --- NUEVAS VALIDACIONES: REINTENTOS Y BLOQUEOS DE SEGURIDAD ---
        if (accesoBloqueado) {
             throw new RuntimeException("Acceso denegado: " + motivoBloqueo);
        }
        
        if (examen.getConfiguracionExamen() != null && examen.getConfiguracionExamen().getPermitirReintentos() != null) {
             if (intentosCompletados >= examen.getConfiguracionExamen().getPermitirReintentos()) {
                 throw new RuntimeException("Has alcanzado el limite maximo de intentos permitidos.");
             }
        }
        SesionSupervision nuevaSesion = new SesionSupervision();
        nuevaSesion.setExamenId(examen.getCodigoExamen());
        nuevaSesion.setEstudianteId(estudiante.getEstudianteId());
        nuevaSesion.setConexion(conexionInfo);
        nuevaSesion.setEstadoSesion(EstadoSesion.INICIADA);
        nuevaSesion.asegurarClaveSesion();          // clave propia de esta sesión (ya no hay una clave fija para todos)

        //Se guarda todo en la base de datos
        SesionSupervision guardada = sesionSupervisionRepository.save(nuevaSesion);
        
        try {
            java.util.Map<String, String> evento = new java.util.HashMap<>();
            evento.put("tipoEvento", "ESTUDIANTE_UNIDO");
            evento.put("sesionId", guardada.getSesionId());
            evento.put("estudianteId", guardada.getEstudianteId());
            webSocketMessagingTemplate.convertAndSend(
                "/topic/alertas/" + guardada.getExamenId(),
                evento
            );
        } catch (Exception e) {}
        
        return guardada;
    }
    
    public SesionSupervision obtenerSesion(String sesionId) {
        SesionSupervision sesion = sesionSupervisionRepository.findById(sesionId).orElseThrow(() -> new RuntimeException("No existe"));
        estudianteRepository.findById(sesion.getEstudianteId()).ifPresent(est -> {
            sesion.setNombreEstudiante(est.getNombre() + " " + est.getApellidos());
            sesion.setCedula(est.getCedula());
        });
        return sesion;
    }
    
    public com.example.AppSegurity.DTO.ReglasExamenResponse obtenerReglasExamen(String sesionId) {
        SesionSupervision sesion = sesionSupervisionRepository.findById(sesionId)
                .orElseThrow(() -> new RuntimeException("La sesión no existe"));
        Examen examen = examenRepository.findById(sesion.getExamenId())
                .orElseThrow(() -> new RuntimeException("El examen no existe"));
                
        String sensibilidad = "MEDIA";
        Integer duracion = 120;
        if (examen.getConfiguracionExamen() != null) {
            if (examen.getConfiguracionExamen().getSensibilidadIA() != null) {
                sensibilidad = examen.getConfiguracionExamen().getSensibilidadIA().toString();
            }
            if (examen.getConfiguracionExamen().getDuracionExamen() != null) {
                duracion = examen.getConfiguracionExamen().getDuracionExamen();
            }
        }
        
        return new com.example.AppSegurity.DTO.ReglasExamenResponse(
            examen.getConfiguracionExamen() != null ? examen.getConfiguracionExamen().getProcesosPermitidos() : new java.util.ArrayList<>(),
            examen.getConfiguracionExamen() != null ? examen.getConfiguracionExamen().getUrlsPermitidas() : new java.util.ArrayList<>(),
            sensibilidad,
            duracion
        );
    }

    public void registrarAlerta(String sesionId, NuevaAlertaRequest alertaRequest) {
        //La clase decide cómo se guarda la alerta: sin ella (o con un valor desconocido) se rechaza con un mensaje claro
        if (alertaRequest.getClaseAlerta() == null) {
            throw new RuntimeException("claseAlerta ausente o desconocida");
        }
        //El nivel de riesgo es opcional para el cliente: por defecto MEDIO
        if (alertaRequest.getNivelRiesgo() == null) {
            alertaRequest.setNivelRiesgo(com.example.AppSegurity.Enums.NivelRiesgo.MEDIO);
        }

        //Validamos primero que nada que la sesionSupervision exista en la base de datos
        SesionSupervision sesionSupervision = sesionSupervisionRepository.findById(sesionId)
                .orElseThrow(() -> new RuntimeException("La sesión no existe"));

        com.example.AppSegurity.Models.Examen examen = examenRepository.findById(sesionSupervision.getExamenId())
                .orElseThrow(() -> new RuntimeException("El examen no existe"));

        String nombreEstudiante = "Desconocido";
        var est = estudianteRepository.findById(sesionSupervision.getEstudianteId()).orElse(null);
        if (est != null) {
            nombreEstudiante = est.getNombre() + "_" + est.getApellidos();
            nombreEstudiante = nombreEstudiante.replaceAll("[^a-zA-Z0-9_-]", "_");
        }

        AlertaEvidencia alertaGuardar = null;

        if (alertaRequest.getClaseAlerta() == ClaseAlerta.VISION || alertaRequest.getClaseAlerta() == ClaseAlerta.OBJETO || alertaRequest.getClaseAlerta() == ClaseAlerta.CAMARA_OBSTRUIDA) {
            String rutaWebcam = "E2EE_WEBCAM";
            String rutaPantalla = "E2EE_PANTALLA";
            
            alertaGuardar = new AlertaVision(
                    alertaRequest.getTipoEvidenciaVision() != null ? alertaRequest.getTipoEvidenciaVision() : com.example.AppSegurity.Enums.TipoEvidenciaVision.WEBCAM_OBJETO,
                    alertaRequest.getCantidadRostros() != null ? alertaRequest.getCantidadRostros() : 0,
                    alertaRequest.getObjetoDetectado() != null ? alertaRequest.getObjetoDetectado() : "Alerta de Visión",
                    alertaRequest.getConfianzaIa() != null ? alertaRequest.getConfianzaIa() : 1.0,
                    rutaWebcam,
                    rutaPantalla);

        } else if (alertaRequest.getClaseAlerta() == ClaseAlerta.AUDIO) {
            String rutaAudio = "E2EE_AUDIO";
            alertaGuardar = new AlertaAudio(
                    alertaRequest.getTranscripcion(),
                    alertaRequest.getVocesDetectadas(),
                    alertaRequest.getConfianzaVoz(),
                    rutaAudio);

        } else if (alertaRequest.getClaseAlerta() == ClaseAlerta.PROCESO || alertaRequest.getClaseAlerta() == ClaseAlerta.ENTORNO || alertaRequest.getClaseAlerta() == ClaseAlerta.CONTROL_REMOTO || alertaRequest.getClaseAlerta() == ClaseAlerta.APP_TERMINADA || alertaRequest.getClaseAlerta() == ClaseAlerta.SESION_DUPLICADA || alertaRequest.getClaseAlerta() == ClaseAlerta.DESCONEXION_REINCIDENTE || alertaRequest.getClaseAlerta() == ClaseAlerta.DESCONEXION_PROLONGADA) {
            String rutaPantalla = "E2EE_PROCESO";
            
            alertaGuardar = new AlertaProceso(
                    alertaRequest.getPidProceso() != null ? alertaRequest.getPidProceso() : 0,
                    alertaRequest.getNombreProceso() != null ? alertaRequest.getNombreProceso() : alertaRequest.getClaseAlerta().name(),
                    alertaRequest.getCategoriaProceso() != null ? alertaRequest.getCategoriaProceso() : com.example.AppSegurity.Enums.CategoriaProceso.CONTROL_REMOTO,
                    alertaRequest.getAccionTomada() != null ? alertaRequest.getAccionTomada() : com.example.AppSegurity.Enums.AccionTomada.ADVERTENCIA_MOSTRADA,
                    rutaPantalla);

        } else if (alertaRequest.getClaseAlerta() == ClaseAlerta.TECLADO) {
            String rutaPantalla = "E2EE_TECLADO";
            alertaGuardar = new AlertaTeclado(
                    alertaRequest.getCombinacionTeclas(),
                    alertaRequest.getPatronSospechoso(),
                    rutaPantalla);
        } else {
            alertaGuardar = new AlertaEvidencia();
        }

        //Se llena en la clase padre los datos basicos que tendran todos los tipos de alertas
        if (alertaGuardar != null) {
            alertaGuardar.setHashWebcam(alertaRequest.getHashWebcam());
            alertaGuardar.setHashPantalla(alertaRequest.getHashPantalla());
            alertaGuardar.setHashAudio(alertaRequest.getHashAudio());
            // Explicación en texto: sin espacios sobrantes y con un tope, para que nadie llene la base con textos enormes
            String detalleAlerta = alertaRequest.getDetalle() == null ? "" : alertaRequest.getDetalle().strip();
            alertaGuardar.setDetalle(detalleAlerta.isEmpty() ? null : detalleAlerta.substring(0, Math.min(detalleAlerta.length(), 600)));
            alertaGuardar.setSesionId(sesionId);
            alertaGuardar.setClaseAlerta(alertaRequest.getClaseAlerta());
            alertaGuardar.setNivelRiesgo(alertaRequest.getNivelRiesgo());
            alertaGuardar.setHoraCaptura(LocalDateTime.now());
            //Por ultimo guardamos la alrta en la base de datos
            alertaEvidenciaRepository.save(alertaGuardar);
            
            // Enviar notificacion WebSocket al feed global del profesor
            // El profesor esta suscrito a /topic/alertas/{codigoExamen}
            try {
                alertaGuardar.setNombreEstudiante(nombreEstudiante.replace("_", " ")); // Formato bonito
                // Attach transient base64 for real-time bypass
                alertaGuardar.setBase64WebcamTransient(alertaRequest.getUrlFotoWebcam());
                alertaGuardar.setBase64PantallaTransient(alertaRequest.getUrlCapturaPantalla());
                alertaGuardar.setBase64AudioTransient(alertaRequest.getUrlAudio());
                
                webSocketMessagingTemplate.convertAndSend(
                    "/topic/alertas/" + sesionSupervision.getExamenId(),
                    alertaGuardar
                );
            } catch (Exception e) {
                // Ignorar error de WS
            }

        } //En caso de que venga null reportamos una excepción
        else {
            throw new RuntimeException("La alerta vino vacia");
        }

    }

    public void finalizarSesion(String idSesion) {
        //Traer la info de la sesión a finalizar
        SesionSupervision sesionSupervision = sesionSupervisionRepository.findById(idSesion)
                .orElseThrow(() -> new RuntimeException("Error al encontrar la sesión a finalizar"));

        //Se cambia el estado a finalizada
        sesionSupervision.setEstadoSesion(EstadoSesion.FINALIZADA);

        //Se guarda el cambio en la base de datos MongoDB
        sesionSupervisionRepository.save(sesionSupervision);
    }

    public List<AlertaEvidencia> obtenerHistorialAlertas(String sesionId) {
        //Se busca en alertaEvidenciaRepository todas las alertas que tengan ese id_sesion
        return alertaEvidenciaRepository.findBySesionIdOrderByHoraCapturaAsc(sesionId);
    }
    
    public List<AlertaEvidencia> obtenerAlertasExamen(String examenId) {
        // Obtenemos todas las sesiones de este examen
        List<SesionSupervision> sesiones = sesionSupervisionRepository.findByExamenId(examenId);
        List<String> sesionIds = new java.util.ArrayList<>();
        java.util.Map<String, String> mapNombres = new java.util.HashMap<>();
        
        for (SesionSupervision s : sesiones) {
            sesionIds.add(s.getSesionId());
            var est = estudianteRepository.findById(s.getEstudianteId()).orElse(null);
            if (est != null) {
                mapNombres.put(s.getSesionId(), est.getNombre() + " " + est.getApellidos());
            }
        }
        
        if (sesionIds.isEmpty()) return new java.util.ArrayList<>();
        
        List<AlertaEvidencia> alertas = alertaEvidenciaRepository.findBySesionIdInOrderByHoraCapturaDesc(sesionIds);
        for (AlertaEvidencia a : alertas) {
            a.setNombreEstudiante(mapNombres.getOrDefault(a.getSesionId(), "Desconocido"));
        }
        
        return alertas;
    }

    public void guardarVeredicto_Reporte(String sesionId, AnalisisGlobal_IA analisis, String veredictoDocente, String urlPDF) {
        //Se busca la sesionSupervision en la base de datos
        SesionSupervision sesionSupervision = sesionSupervisionRepository.findById(sesionId)
                .orElseThrow(() -> new RuntimeException("Error al encontrar la sesión"));

        //Se llena el elemento de analisis global de la ia 
        AnalisisGlobal_IA analisisGlobal_IA = analisis;

        //Se crea un reporte final y se llena con los datos necesarios
        ReporteFinal reporteFinal = new ReporteFinal();
        reporteFinal.setVeredictoProfesor(veredictoDocente);
        reporteFinal.setUrlPdfReporte(urlPDF);

        //Metemos ese reporte a la sesión 
        sesionSupervision.setAnalisisGlobalIA(analisisGlobal_IA);
        sesionSupervision.setReporteFinal(reporteFinal);

        //Al final se guarda todo en la base de datos
        sesionSupervisionRepository.save(sesionSupervision);
    }

    public List<SesionSupervision> obtenerSesionesPorExamen(String examenId, boolean incluirFinalizadas) {
        List<SesionSupervision> sesiones = sesionSupervisionRepository.findByExamenId(examenId);
        
        // Remover las que ya fueron cerradas/finalizadas si no queremos incluirlas
        if (!incluirFinalizadas) {
            sesiones.removeIf(s -> s.getEstadoSesion() != EstadoSesion.INICIADA);
        }
        
        // Agregar el nombre real del estudiante, su cédula oficial y métricas
        for (SesionSupervision s : sesiones) {
            estudianteRepository.findById(s.getEstudianteId()).ifPresent(est -> {
                s.setNombreEstudiante(est.getNombre() + " " + est.getApellidos());
                s.setCedula(est.getCedula());
            });
            
            List<com.example.AppSegurity.Models.AlertaEvidencia> alertas = alertaEvidenciaRepository.findBySesionIdOrderByHoraCapturaAsc(s.getSesionId());
            s.setCantidadAlertas(alertas.size());
            
            // Calcular integridad dinámica basada en las alertas
            int descuento = 0;
            for (com.example.AppSegurity.Models.AlertaEvidencia alerta : alertas) {
                if (alerta.getNivelRiesgo() == com.example.AppSegurity.Enums.NivelRiesgo.BAJO) {
                    descuento += 5;
                } else if (alerta.getNivelRiesgo() == com.example.AppSegurity.Enums.NivelRiesgo.MEDIO) {
                    descuento += 15;
                } else if (alerta.getNivelRiesgo() == com.example.AppSegurity.Enums.NivelRiesgo.ALTO) {
                    descuento += 30;
                }
            }
            int integridadReal = Math.max(0, 100 - descuento);
            s.setPorcentajeIntegridad(integridadReal);
        }
        
        return sesiones;
    }

    public SesionSupervision registrarHeartbeat(String sesionId, String deviceId) {
        SesionSupervision sesion = sesionSupervisionRepository.findById(sesionId)
                .orElseThrow(() -> new RuntimeException("La sesión no existe"));

        if (deviceId != null && sesion.getConexion() != null && sesion.getConexion().getDeviceId() != null) {
            if (!sesion.getConexion().getDeviceId().equalsIgnoreCase(deviceId)) {
                throw new RuntimeException("Dispositivo no autorizado para esta sesión");
            }
        }
        return sesion;
    }

    public void marcarSesionInterrumpida(String sesionId, String motivo) {
        sesionSupervisionRepository.findById(sesionId).ifPresent(sesion -> {
            if (sesion.getEstadoSesion() == EstadoSesion.INICIADA) {
                sesion.setEstadoSesion(EstadoSesion.INTERRUMPIDA);
                sesionSupervisionRepository.save(sesion);
            }
        });
    }

}
