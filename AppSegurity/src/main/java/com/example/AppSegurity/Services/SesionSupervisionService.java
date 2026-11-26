package com.example.AppSegurity.Services;

import com.example.AppSegurity.Config.RegistroSuscripcionesAlertas;
import com.example.AppSegurity.Config.StompAutorizacionInterceptor;
import com.example.AppSegurity.DTO.NuevaAlertaRequest;
import com.example.AppSegurity.DTO.RetransmitirEvidenciaRequest;
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
import com.example.AppSegurity.Security.ErrorNegocioException;
import com.example.AppSegurity.Sub_Clases.AnalisisGlobal_IA;
import com.example.AppSegurity.Sub_Clases.Conexion;
import com.example.AppSegurity.Sub_Clases.Materia;
import com.example.AppSegurity.Sub_Clases.ReporteFinal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.example.AppSegurity.Security.LimitadorIntentos;

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
    @Autowired
    private LimitadorIntentos limitadorPin;
    @Autowired
    private RegistroSuscripcionesAlertas registroSuscripcionesAlertas;
    @Autowired(required = false)
    private StompAutorizacionInterceptor stompAutorizacionInterceptor;

    private final Object[] locksInicioSesion = crearCandados(64);
    private final Object[] locksAlertaSesion = crearCandados(64);

    private static Object[] crearCandados(int n) {
        Object[] arr = new Object[n];
        for (int i = 0; i < n; i++) {
            arr[i] = new Object();
        }
        return arr;
    }

    private static Object candadoDe(Object[] tabla, String clave) {
        int idx = (clave == null ? 0 : clave.hashCode()) & (tabla.length - 1);
        return tabla[idx];
    }

    /**
     * Antes no había ningún freno aquí: a diferencia del login, el OTP o el cambio de clave, un estudiante autenticado
     * podía probar PINes sin límite. Solo cuenta como "fallo" un PIN que no corresponde a ningún examen (adivinanza);
     * un PIN correcto que luego se rechaza por otro motivo (examen cerrado, fuera de horario, límite de intentos,
     * sesión duplicada, VPN, etc.) NO debe penalizar al estudiante que sí tecleó bien el PIN.
     */
    public SesionSupervision iniciarSesion(String idEstudiante, String pinExamen, Conexion conexionInfo) {
        String clavePin = "pin:" + (idEstudiante == null ? "" : idEstudiante.trim().toLowerCase());
        limitadorPin.verificar(clavePin);

        Examen examen = examenRepository.findFirstByControlAccesoPinSesion(pinExamen).orElse(null);
        if (examen == null) {
            limitadorPin.registrarFallo(clavePin);
            throw new ErrorNegocioException("El PIN ingresado no existe o es incorrecto");
        }
        limitadorPin.registrarExito(clavePin);

        return iniciarSesionConExamenValido(idEstudiante, examen, conexionInfo);
    }

    private SesionSupervision iniciarSesionConExamenValido(String idEstudiante, Examen examen, Conexion conexionInfo) {

        // Se trae el estudiante de la base de datos para ver las materias que tiene
        Estudiante estudiante = estudianteRepository.findById(idEstudiante)
                .orElseGet(() -> estudianteRepository.findByEmailIgnoreCase(idEstudiante).stream().findFirst()
                .orElseThrow(() -> new ErrorNegocioException("Estudiante no encontrado (ID o Email: " + idEstudiante + ")")));

        // Validamos que el profesor tenga el examen abierto (Estado_pin == ACTIVO)
        if (examen.getControlAcceso() == null || 
            examen.getControlAcceso().getEstadoPin() != com.example.AppSegurity.Enums.Estado_pin.ACTIVO) {
            throw new ErrorNegocioException("El examen no está activo en este momento (Cerrado por el profesor)");
        }
        
        // Validación estricta de tiempo
        if (examen.getFechaExamen() != null) {
            java.time.LocalDateTime ahora = java.time.LocalDateTime.now();
            java.time.LocalDateTime horaInicio = examen.getFechaExamen().getHoraInicio();
            java.time.LocalDateTime horaFin = examen.getFechaExamen().getHoraFin();
            
            if (horaInicio != null && ahora.isBefore(horaInicio)) {
                throw new ErrorNegocioException("El examen aún no ha comenzado. Inicia a las " + horaInicio.toString());
            }
            if (horaFin != null && ahora.isAfter(horaFin)) {
                throw new ErrorNegocioException("El examen ya ha finalizado. Terminó a las " + horaFin.toString());
            }
        }

        //Validamos que el estudiante tenga la materia inscrita
        boolean esta_inscrito = false;
        if (estudiante.getMateriasInscritas() != null) {
            for (Materia materia : estudiante.getMateriasInscritas()) {
                if (materia != null && materia.getCodigoMateria() != null
                        && materia.getCodigoMateria().equals(examen.getMateriaCodigo())) {
                    esta_inscrito = true;
                    break;
                }
            }
        }

        //En caso de que no este inscrito
        if (!esta_inscrito) {
            throw new ErrorNegocioException("No esta inscrito en la materia para hacer este examen");
        }

        //Validamos la información de la conexión
        //Bloqueamos VPNs o Proxies que puedan enmascarar al estudiante
        if (conexionInfo != null && Boolean.TRUE.equals(conexionInfo.getVpnDetectada())) {
            throw new ErrorNegocioException("Acceso denegado: Se ha detectado una conexión VPN activa."
                    + " Por favor desactivarla para hacer el examen");
        }

        // Bloqueo por Sistema Operativo ya que nuestra App de Python solo corre en Windows
        if (conexionInfo != null && conexionInfo.getSistemaOperativo() != null
                && !conexionInfo.getSistemaOperativo().toLowerCase().contains("windows")) {
            throw new ErrorNegocioException("Acceso denegado: El sistema de supervisión actualmente solo es compatible con Windows.");
        }

        // Serializa peticiones /iniciar simultáneas del mismo estudiante en el mismo examen (evita doble sesión activa)
        synchronized (candadoDe(locksInicioSesion, examen.getCodigoExamen() + ":" + estudiante.getEstudianteId())) {
            // Revisar si ya tiene una sesión iniciada para no duplicar tarjetas en el panel del profesor
            List<SesionSupervision> sesionesPrevias = sesionSupervisionRepository.findByExamenId(examen.getCodigoExamen());
            int intentosCompletados = 0;
            boolean accesoBloqueado = false;
            String motivoBloqueo = "";
            
            for (SesionSupervision s : sesionesPrevias) {
                if (s.getEstudianteId() != null && s.getEstudianteId().equals(estudiante.getEstudianteId())) {
                    if (s.getEstadoSesion() == EstadoSesion.INICIADA || s.getEstadoSesion() == EstadoSesion.INTERRUMPIDA) {
                        String devExistente = (s.getConexion() != null) ? s.getConexion().getDeviceId() : null;
                        String devEntrante = (conexionInfo != null) ? conexionInfo.getDeviceId() : null;
                        boolean tieneDevExistente = devExistente != null && !devExistente.isBlank();
                        boolean faltaDevEntrante = devEntrante == null || devEntrante.isBlank();

                        // Si la sesión ya pertenece a un dispositivo identificado y el entrante no coincide o omite deviceId -> bloquear
                        if (tieneDevExistente && (faltaDevEntrante || !devExistente.equalsIgnoreCase(devEntrante))) {
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

                            throw new ErrorNegocioException("Acceso denegado: Ya existe una sesión activa para este examen en otro equipo. Por motivos de integridad académica no se permite el acceso simultáneo desde múltiples dispositivos.");
                        }

                        // Mismo dispositivo: reconexión autorizada
                        s.setEstadoSesion(EstadoSesion.INICIADA);
                        LocalDateTime ahoraReconexion = LocalDateTime.now();
                        if (s.getFechaInicio() == null) {
                            s.setFechaInicio(ahoraReconexion);
                        }
                        s.setUltimoHeartbeat(ahoraReconexion);
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
                    } else if ((s.getEstadoSesion() == EstadoSesion.FINALIZADA && !Boolean.TRUE.equals(s.getCerradaPorSalaDocente()))
                            || s.getEstadoSesion() == EstadoSesion.ANULADA
                            || s.getEstadoSesion() == EstadoSesion.APELACION_CURSO) {
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
                 throw new ErrorNegocioException("Acceso denegado: " + motivoBloqueo);
            }
            
            if (examen.getConfiguracionExamen() != null) {
                Integer maxIntentosCfg = examen.getConfiguracionExamen().getIntentosMaximos();
                Integer reintentosRedCfg = examen.getConfiguracionExamen().getPermitirReintentos();
                Integer limiteIntentos = null;
                if (maxIntentosCfg != null) {
                    limiteIntentos = Math.max(1, maxIntentosCfg);
                } else if (reintentosRedCfg != null) {
                    limiteIntentos = Math.max(1, reintentosRedCfg);
                }
                if (limiteIntentos != null && intentosCompletados >= limiteIntentos) {
                     throw new ErrorNegocioException("Has alcanzado el limite maximo de intentos permitidos.");
                }
            }
            // Sesiones anteriores del estudiante (de cualquier examen), antes de crear esta: para detectar primer examen / equipo nuevo
            List<SesionSupervision> sesionesDelEstudiante = sesionSupervisionRepository.findAllByEstudianteId(estudiante.getEstudianteId());
            SesionSupervision nuevaSesion = new SesionSupervision();
            nuevaSesion.setExamenId(examen.getCodigoExamen());
            nuevaSesion.setEstudianteId(estudiante.getEstudianteId());
            nuevaSesion.setConexion(conexionInfo);
            nuevaSesion.setEstadoSesion(EstadoSesion.INICIADA);
            LocalDateTime ahoraInicio = LocalDateTime.now();
            nuevaSesion.setFechaInicio(ahoraInicio);
            nuevaSesion.setUltimoHeartbeat(ahoraInicio);
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

            avisarEquipo(guardada.getSesionId(), evaluarEquipo(sesionesDelEstudiante, conexionInfo != null ? conexionInfo.getDeviceId() : null));
            
            return guardada;
        }
    }

    /**
     * Ayuda a detectar suplantaciones: el rostro de referencia lo registra quien esté frente al equipo la PRIMERA vez, y en un equipo
     * nuevo se registra otro. Devuelve "PRIMER_EXAMEN_DEL_ESTUDIANTE" si nunca había tenido una sesión,
     * "EQUIPO_NUEVO_PARA_EL_ESTUDIANTE" si ya tenía sesiones pero ninguna desde este equipo, o null (nada que avisar).
     */
    static String evaluarEquipo(List<SesionSupervision> previas, String deviceIdEntrante) {
        if (previas == null || previas.isEmpty()) {
            return "PRIMER_EXAMEN_DEL_ESTUDIANTE";
        }
        if (deviceIdEntrante == null || deviceIdEntrante.isBlank()) {
            return null;
        }
        boolean algunoIdentificado = false;
        for (SesionSupervision s : previas) {
            String dev = (s.getConexion() != null) ? s.getConexion().getDeviceId() : null;
            if (dev != null && !dev.isBlank()) {
                algunoIdentificado = true;
                if (dev.equalsIgnoreCase(deviceIdEntrante)) {
                    return null;
                }
            }
        }
        // Si ninguna sesión anterior tenía identificador de equipo no se puede afirmar que sea nuevo
        return algunoIdentificado ? "EQUIPO_NUEVO_PARA_EL_ESTUDIANTE" : null;
    }

    private void avisarEquipo(String sesionId, String evento) {
        if (evento == null) {
            return;
        }
        try {
            boolean nuevo = "EQUIPO_NUEVO_PARA_EL_ESTUDIANTE".equals(evento);
            NuevaAlertaRequest aviso = new NuevaAlertaRequest();
            aviso.setClaseAlerta(ClaseAlerta.ENTORNO);
            aviso.setNivelRiesgo(nuevo ? NivelRiesgo.MEDIO : NivelRiesgo.BAJO);
            aviso.setPidProceso(0);
            aviso.setNombreProceso(evento);
            aviso.setAccionTomada(com.example.AppSegurity.Enums.AccionTomada.ADVERTENCIA_MOSTRADA);
            aviso.setDetalle(nuevo
                    ? "El estudiante ya había presentado exámenes, pero desde un equipo distinto al de ahora. En un equipo nuevo se registra "
                      + "un rostro de referencia nuevo: conviene comprobar visualmente que es la misma persona."
                    : "Es el primer examen de este estudiante en el sistema: el rostro de referencia se registró hoy en este equipo. "
                      + "Conviene comprobar visualmente que es la persona matriculada.");
            registrarAlerta(sesionId, aviso);
        } catch (Exception e) {
            // Un aviso informativo nunca debe impedir que el estudiante entre a su examen
        }
    }
    
    public SesionSupervision obtenerSesion(String sesionId) {
        SesionSupervision sesion = sesionSupervisionRepository.findById(sesionId).orElseThrow(() -> new ErrorNegocioException("No existe"));
        estudianteRepository.findById(sesion.getEstudianteId()).ifPresent(est -> {
            sesion.setNombreEstudiante(est.getNombre() + " " + est.getApellidos());
            sesion.setCedula(est.getCedula());
        });
        return sesion;
    }
    
    public com.example.AppSegurity.DTO.ReglasExamenResponse obtenerReglasExamen(String sesionId) {
        SesionSupervision sesion = sesionSupervisionRepository.findById(sesionId)
                .orElseThrow(() -> new ErrorNegocioException("La sesión no existe"));
        Examen examen = examenRepository.findById(sesion.getExamenId())
                .orElseThrow(() -> new ErrorNegocioException("El examen no existe"));
                
        String sensibilidad = "MEDIA";
        Integer duracion = 120;
        Integer reintentosConexion = 3;
        Integer intentosMaximos = 3;
        if (examen.getConfiguracionExamen() != null) {
            if (examen.getConfiguracionExamen().getSensibilidadIA() != null) {
                sensibilidad = examen.getConfiguracionExamen().getSensibilidadIA().toString();
            }
            if (examen.getConfiguracionExamen().getDuracionExamen() != null) {
                duracion = examen.getConfiguracionExamen().getDuracionExamen();
            }
            if (examen.getConfiguracionExamen().getPermitirReintentos() != null) {
                reintentosConexion = Math.max(1, examen.getConfiguracionExamen().getPermitirReintentos());
            }
            if (examen.getConfiguracionExamen().getIntentosMaximos() != null) {
                intentosMaximos = Math.max(1, examen.getConfiguracionExamen().getIntentosMaximos());
            } else if (examen.getConfiguracionExamen().getPermitirReintentos() != null) {
                intentosMaximos = Math.max(1, examen.getConfiguracionExamen().getPermitirReintentos());
            }
        }
        
        com.example.AppSegurity.Sub_Clases.ConfiguracionExamen cfg = examen.getConfiguracionExamen();
        com.example.AppSegurity.DTO.ReglasExamenResponse resp = new com.example.AppSegurity.DTO.ReglasExamenResponse(
            cfg != null ? cfg.getProcesosPermitidos() : new java.util.ArrayList<>(),
            cfg != null ? cfg.getUrlsPermitidas() : new java.util.ArrayList<>(),
            sensibilidad,
            duracion,
            reintentosConexion
        );
        resp.setIntentos_maximos(intentosMaximos);
        if (cfg != null) {
            resp.setActivar_reconocimiento_facial(cfg.getActivarReconocimientoFacial() == null || cfg.getActivarReconocimientoFacial());
            resp.setActivar_deteccion_objetos(cfg.getActivarDeteccionObjetos() == null || cfg.getActivarDeteccionObjetos());
            resp.setActivar_analisis_audio(cfg.getActivarAnalisisAudio() == null || cfg.getActivarAnalisisAudio());
            resp.setActivar_monitoreo_procesos(cfg.getActivarMonitoreoProcesos() == null || cfg.getActivarMonitoreoProcesos());
            resp.setActivar_analisis_teclado(cfg.getActivarAnalisisTeclado() == null || cfg.getActivarAnalisisTeclado());
        }
        resp.setSegundos_restantes(calcularSegundosRestantes(sesion, examen, duracion));
        return resp;
    }

    private static int calcularSegundosRestantes(SesionSupervision sesion, Examen examen, Integer duracionMin) {
        long duracionSeg = (duracionMin != null && duracionMin > 0) ? duracionMin * 60L : 7200L;
        LocalDateTime ahora = LocalDateTime.now();
        long restantes = duracionSeg;
        if (sesion != null && sesion.getFechaInicio() != null) {
            long transcurridos = Math.max(0L, java.time.Duration.between(sesion.getFechaInicio(), ahora).getSeconds());
            restantes = Math.max(0L, duracionSeg - transcurridos);
        }
        if (examen != null && examen.getFechaExamen() != null && examen.getFechaExamen().getHoraFin() != null) {
            long hastaCierre = Math.max(0L, java.time.Duration.between(ahora, examen.getFechaExamen().getHoraFin()).getSeconds());
            restantes = Math.min(restantes, hastaCierre);
        }
        return (int) Math.min(restantes, Integer.MAX_VALUE);
    }

    private String formatearNombreEstudiante(String estudianteId) {
        if (estudianteId == null) {
            return "Desconocido";
        }
        var est = estudianteRepository.findById(estudianteId).orElse(null);
        if (est == null) {
            return "Desconocido";
        }
        String nombre = ((est.getNombre() != null ? est.getNombre() : "")
                + " "
                + (est.getApellidos() != null ? est.getApellidos() : "")).trim();
        nombre = nombre.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", " ").trim();
        return nombre.isEmpty() ? "Desconocido" : nombre;
    }

    public static class DispositivoNoAutorizadoException extends ErrorNegocioException {
        public DispositivoNoAutorizadoException(String mensaje) {
            super(mensaje);
        }
    }

    public record ResultadoRegistroAlerta(AlertaEvidencia alerta, boolean docenteNotificado) {}

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static boolean tieneMultimedia(NuevaAlertaRequest req) {
        return req != null && (tieneTexto(req.getUrlFotoWebcam()) || tieneTexto(req.getUrlCapturaPantalla()) || tieneTexto(req.getUrlAudio()));
    }

    private boolean dentroDeVentanaDeGracia(SesionSupervision sesion) {
        if (sesion == null || sesion.getEstadoSesion() != EstadoSesion.FINALIZADA || sesion.getFechaFinalizacion() == null) {
            return false;
        }
        long seg = java.time.Duration.between(sesion.getFechaFinalizacion(), LocalDateTime.now()).getSeconds();
        return seg <= 120L;
    }

    public AlertaEvidencia registrarAlerta(String sesionId, NuevaAlertaRequest alertaRequest) {
        return registrarAlertaConEntrega(sesionId, alertaRequest).alerta();
    }

    public ResultadoRegistroAlerta registrarAlertaConEntrega(String sesionId, NuevaAlertaRequest alertaRequest) {
        //La clase decide cómo se guarda la alerta: sin ella (o con un valor desconocido) se rechaza con un mensaje claro
        if (alertaRequest.getClaseAlerta() == null) {
            throw new ErrorNegocioException("claseAlerta ausente o desconocida");
        }
        //El nivel de riesgo es opcional para el cliente: por defecto MEDIO
        if (alertaRequest.getNivelRiesgo() == null) {
            alertaRequest.setNivelRiesgo(com.example.AppSegurity.Enums.NivelRiesgo.MEDIO);
        }

        //Validamos primero que nada que la sesionSupervision exista en la base de datos y no esté cerrada
        SesionSupervision sesionSupervision = sesionSupervisionRepository.findById(sesionId)
                .orElseThrow(() -> new ErrorNegocioException("La sesión no existe"));

        EstadoSesion estadoActual = sesionSupervision.getEstadoSesion();
        if (estadoActual == EstadoSesion.ANULADA
                || estadoActual == EstadoSesion.APELACION_CURSO
                || (estadoActual == EstadoSesion.FINALIZADA && !dentroDeVentanaDeGracia(sesionSupervision))) {
            throw new ErrorNegocioException("La sesión ya finalizó o fue cerrada");
        }

        String idCliente = alertaRequest.getIdCliente() == null ? null : alertaRequest.getIdCliente().strip();
        if (idCliente != null && !idCliente.isEmpty()) {
            synchronized (candadoDe(locksAlertaSesion, sesionId)) {
                var existente = alertaEvidenciaRepository.findFirstBySesionIdAndIdCliente(sesionId, idCliente);
                if (existente.isPresent()) {
                    AlertaEvidencia previa = existente.get();
                    if (previa.isEntregadaDocente()) {
                        return new ResultadoRegistroAlerta(previa, true);
                    }
                    boolean entregadaEnReintento = false;
                    if (tieneMultimedia(alertaRequest)
                            && registroSuscripcionesAlertas.hayReceptorActivo(sesionSupervision.getExamenId())) {
                        previa.setNombreEstudiante(formatearNombreEstudiante(sesionSupervision.getEstudianteId()));
                        previa.setEsRetransmision(true);
                        previa.setBase64WebcamTransient(alertaRequest.getUrlFotoWebcam());
                        previa.setBase64PantallaTransient(alertaRequest.getUrlCapturaPantalla());
                        previa.setBase64AudioTransient(alertaRequest.getUrlAudio());
                        try {
                            webSocketMessagingTemplate.convertAndSend(
                                    "/topic/alertas/" + sesionSupervision.getExamenId(),
                                    previa
                            );
                            entregadaEnReintento = true;
                        } catch (Exception ignored) {}
                    }
                    return new ResultadoRegistroAlerta(previa, entregadaEnReintento);
                }
                return persistirYNofificarAlerta(sesionId, alertaRequest, sesionSupervision, idCliente);
            }
        }
        return persistirYNofificarAlerta(sesionId, alertaRequest, sesionSupervision, null);
    }

    private ResultadoRegistroAlerta persistirYNofificarAlerta(
            String sesionId,
            NuevaAlertaRequest alertaRequest,
            SesionSupervision sesionSupervision,
            String idCliente) {
        examenRepository.findById(sesionSupervision.getExamenId())
                .orElseThrow(() -> new ErrorNegocioException("El examen no existe"));

        String nombreEstudianteBonito = formatearNombreEstudiante(sesionSupervision.getEstudianteId());

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
            double confianzaVoz = alertaRequest.getConfianzaVoz() != null ? alertaRequest.getConfianzaVoz() : 1.0;
            alertaGuardar = new AlertaAudio(
                    alertaRequest.getTranscripcion(),
                    alertaRequest.getVocesDetectadas(),
                    confianzaVoz,
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
            alertaGuardar.setIdCliente(idCliente);
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
            boolean receptorActivo = registroSuscripcionesAlertas.hayReceptorActivo(sesionSupervision.getExamenId());
            boolean enviadaWs = false;
            try {
                alertaGuardar.setNombreEstudiante(nombreEstudianteBonito); // Formato bonito
                alertaGuardar.setEsRetransmision(false);
                // Attach transient base64 for real-time bypass
                alertaGuardar.setBase64WebcamTransient(alertaRequest.getUrlFotoWebcam());
                alertaGuardar.setBase64PantallaTransient(alertaRequest.getUrlCapturaPantalla());
                alertaGuardar.setBase64AudioTransient(alertaRequest.getUrlAudio());
                
                webSocketMessagingTemplate.convertAndSend(
                    "/topic/alertas/" + sesionSupervision.getExamenId(),
                    alertaGuardar
                );
                enviadaWs = receptorActivo;
            } catch (Exception e) {
                // Ignorar error de WS
            }

            return new ResultadoRegistroAlerta(alertaGuardar, enviadaWs);

        } //En caso de que venga null reportamos una excepción
        else {
            throw new ErrorNegocioException("La alerta vino vacia");
        }

    }

    public boolean tieneReceptorActivoParaSesion(String sesionId) {
        SesionSupervision sesion = sesionSupervisionRepository.findById(sesionId)
                .orElseThrow(() -> new ErrorNegocioException("La sesión no existe"));
        return registroSuscripcionesAlertas.hayReceptorActivo(sesion.getExamenId());
    }

    public List<String> obtenerAlertasPendientesDocente(String sesionId) {
        List<AlertaEvidencia> alertas = alertaEvidenciaRepository.findBySesionIdOrderByHoraCapturaAsc(sesionId);
        List<String> pendientes = new java.util.ArrayList<>();
        if (alertas == null) {
            return pendientes;
        }
        for (AlertaEvidencia a : alertas) {
            if (!a.isEntregadaDocente()
                    && (tieneTexto(a.getHashWebcam()) || tieneTexto(a.getHashPantalla()) || tieneTexto(a.getHashAudio()))
                    && a.getIdAlerta() != null) {
                pendientes.add(a.getIdAlerta());
            }
        }
        return pendientes;
    }

    public boolean retransmitirEvidencia(String sesionId, String alertaId, RetransmitirEvidenciaRequest req) {
        SesionSupervision sesion = sesionSupervisionRepository.findById(sesionId)
                .orElseThrow(() -> new ErrorNegocioException("La sesión no existe"));
        AlertaEvidencia alerta = alertaEvidenciaRepository.findById(alertaId)
                .orElseThrow(() -> new ErrorNegocioException("La alerta no existe"));
        if (!sesionId.equals(alerta.getSesionId())) {
            throw new ErrorNegocioException("La alerta no pertenece a la sesión");
        }
        if (sesion.getEstadoSesion() == EstadoSesion.ANULADA) {
            throw new ErrorNegocioException("La sesión fue anulada");
        }
        if (!registroSuscripcionesAlertas.hayReceptorActivo(sesion.getExamenId())) {
            return false;
        }
        alerta.setNombreEstudiante(formatearNombreEstudiante(sesion.getEstudianteId()));
        alerta.setEsRetransmision(true);
        if (req != null) {
            alerta.setBase64WebcamTransient(req.getUrlFotoWebcam());
            alerta.setBase64PantallaTransient(req.getUrlCapturaPantalla());
            alerta.setBase64AudioTransient(req.getUrlAudio());
        }
        try {
            webSocketMessagingTemplate.convertAndSend("/topic/alertas/" + sesion.getExamenId(), alerta);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void finalizarSesion(String idSesion) {
        //Traer la info de la sesión a finalizar
        SesionSupervision sesionSupervision = sesionSupervisionRepository.findById(idSesion)
                .orElseThrow(() -> new ErrorNegocioException("Error al encontrar la sesión a finalizar"));

        if (sesionSupervision.getEstadoSesion() == EstadoSesion.ANULADA
                || sesionSupervision.getEstadoSesion() == EstadoSesion.APELACION_CURSO) {
            throw new ErrorNegocioException("No se puede finalizar una sesión anulada o en apelación");
        }

        //Se cambia el estado a finalizada
        sesionSupervision.setEstadoSesion(EstadoSesion.FINALIZADA);
        if (sesionSupervision.getFechaFinalizacion() == null) {
            sesionSupervision.setFechaFinalizacion(LocalDateTime.now());
        }
        if (sesionSupervision.getCerradaPorSalaDocente() == null) {
            sesionSupervision.setCerradaPorSalaDocente(Boolean.FALSE);
        }

        //Se guarda el cambio en la base de datos MongoDB
        sesionSupervisionRepository.save(sesionSupervision);
        if (stompAutorizacionInterceptor != null) {
            stompAutorizacionInterceptor.invalidarSesion(idSesion);
        }
    }

    public SesionSupervision marcarFraudeDocente(String sesionId, String motivo) {
        SesionSupervision sesion = sesionSupervisionRepository.findById(sesionId)
                .orElseThrow(() -> new ErrorNegocioException("Error al encontrar la sesión"));
        EstadoSesion estado = sesion.getEstadoSesion();
        // Un examen FINALIZADO también puede anularse (el docente revisa la evidencia después; antes bastaba con pulsar
        // "Terminar examen" para quedar protegido). Solo se bloquea mientras hay apelación en curso o si una apelación
        // ya se resolvió a favor del estudiante.
        if (estado == EstadoSesion.APELACION_CURSO) {
            throw new ErrorNegocioException("La sesión está en apelación");
        }
        if (estado == EstadoSesion.FINALIZADA && sesion.getApelacion() != null
                && sesion.getApelacion().getEstadoApelacion() == com.example.AppSegurity.Enums.EstadoApelacion.APROBADA_A_FAVOR) {
            throw new ErrorNegocioException("Una apelación ya resolvió esta sesión a favor del estudiante");
        }
        String motivoLimpio = (motivo == null || motivo.isBlank())
                ? "Fraude marcado por el docente"
                : motivo.strip();

        if (estado != EstadoSesion.ANULADA) {
            sesion.setEstadoSesion(EstadoSesion.ANULADA);
            sesion = sesionSupervisionRepository.save(sesion);
        }
        if (stompAutorizacionInterceptor != null) {
            stompAutorizacionInterceptor.invalidarSesion(sesionId);
        }
        try {
            java.util.Map<String, Object> cmd = new java.util.HashMap<>();
            cmd.put("comando", "FRAUDE");
            cmd.put("mensaje", motivoLimpio);
            webSocketMessagingTemplate.convertAndSend("/topic/comandos/" + sesionId, (Object) cmd);
        } catch (Exception ignored) {}
        try {
            java.util.Map<String, Object> evento = new java.util.HashMap<>();
            evento.put("tipoEvento", "SESION_ANULADA");
            evento.put("sesionId", sesion.getSesionId());
            evento.put("estudianteId", sesion.getEstudianteId());
            evento.put("motivo", motivoLimpio);
            webSocketMessagingTemplate.convertAndSend("/topic/alertas/" + sesion.getExamenId(), (Object) evento);
        } catch (Exception ignored) {}
        return sesion;
    }

    public List<AlertaEvidencia> obtenerHistorialAlertas(String sesionId) {
        //Se busca en alertaEvidenciaRepository todas las alertas que tengan ese id_sesion
        return alertaEvidenciaRepository.findBySesionIdOrderByHoraCapturaAsc(sesionId);
    }
    
    private java.util.Map<String, Estudiante> mapearEstudiantesPorId(List<SesionSupervision> sesiones) {
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
        return estudiantesPorId;
    }

    public List<AlertaEvidencia> obtenerAlertasExamen(String examenId) {
        // Obtenemos todas las sesiones de este examen
        List<SesionSupervision> sesiones = sesionSupervisionRepository.findByExamenId(examenId);
        if (sesiones.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        List<String> sesionIds = new java.util.ArrayList<>(sesiones.size());
        java.util.Map<String, Estudiante> estudiantesPorId = mapearEstudiantesPorId(sesiones);
        java.util.Map<String, String> mapNombres = new java.util.HashMap<>();

        for (SesionSupervision s : sesiones) {
            if (s.getSesionId() != null) {
                sesionIds.add(s.getSesionId());
                Estudiante est = s.getEstudianteId() != null ? estudiantesPorId.get(s.getEstudianteId()) : null;
                if (est != null) {
                    mapNombres.put(s.getSesionId(), est.getNombre() + " " + est.getApellidos());
                }
            }
        }

        if (sesionIds.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        List<AlertaEvidencia> alertas = alertaEvidenciaRepository.findBySesionIdInOrderByHoraCapturaDesc(sesionIds);
        for (AlertaEvidencia a : alertas) {
            a.setNombreEstudiante(mapNombres.getOrDefault(a.getSesionId(), "Desconocido"));
        }

        return alertas;
    }

    public void guardarVeredicto_Reporte(String sesionId, AnalisisGlobal_IA analisis, String veredictoDocente, String urlPDF) {
        //Se busca la sesionSupervision en la base de datos
        SesionSupervision sesionSupervision = sesionSupervisionRepository.findById(sesionId)
                .orElseThrow(() -> new ErrorNegocioException("Error al encontrar la sesión"));

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
        
        // En la sala en vivo (incluirFinalizadas=false) conservar INICIADA e INTERRUMPIDA para no ocultar desconexiones
        if (!incluirFinalizadas) {
            sesiones.removeIf(s -> s.getEstadoSesion() != EstadoSesion.INICIADA
                    && s.getEstadoSesion() != EstadoSesion.INTERRUMPIDA);
        }
        if (sesiones.isEmpty()) {
            return sesiones;
        }

        java.util.Map<String, Estudiante> estudiantesPorId = mapearEstudiantesPorId(sesiones);
        List<String> sesionIds = new java.util.ArrayList<>(sesiones.size());
        for (SesionSupervision s : sesiones) {
            if (s.getSesionId() != null) {
                sesionIds.add(s.getSesionId());
            }
        }

        java.util.Map<String, List<AlertaEvidencia>> alertasPorSesion = new java.util.HashMap<>();
        if (!sesionIds.isEmpty()) {
            for (AlertaEvidencia a : alertaEvidenciaRepository.findBySesionIdInOrderByHoraCapturaDesc(sesionIds)) {
                alertasPorSesion.computeIfAbsent(a.getSesionId(), k -> new java.util.ArrayList<>()).add(a);
            }
        }
        
        // Agregar el nombre real del estudiante, su cédula oficial y métricas en memoria (sin consultas N+1)
        for (SesionSupervision s : sesiones) {
            Estudiante est = s.getEstudianteId() != null ? estudiantesPorId.get(s.getEstudianteId()) : null;
            if (est != null) {
                s.setNombreEstudiante(est.getNombre() + " " + est.getApellidos());
                s.setCedula(est.getCedula());
            }
            
            List<AlertaEvidencia> alertas = alertasPorSesion.getOrDefault(s.getSesionId(), java.util.Collections.emptyList());
            s.setCantidadAlertas(alertas.size());
            
            // Calcular integridad dinámica basada en las alertas reales (sin penalizar avisos informativos ni resúmenes)
            int descuento = 0;
            for (AlertaEvidencia alerta : alertas) {
                if (!cuentaParaIntegridad(alerta)) {
                    continue;
                }
                if (alerta.getNivelRiesgo() == NivelRiesgo.BAJO) {
                    descuento += 5;
                } else if (alerta.getNivelRiesgo() == NivelRiesgo.MEDIO) {
                    descuento += 15;
                } else if (alerta.getNivelRiesgo() == NivelRiesgo.ALTO || alerta.getNivelRiesgo() == NivelRiesgo.CRITICO) {
                    descuento += 30;
                }
            }
            int integridadReal = Math.max(0, 100 - descuento);
            s.setPorcentajeIntegridad(integridadReal);
        }
        
        return sesiones;
    }

    private static final java.util.Set<String> EVENTOS_INFORMATIVOS_INTEGRIDAD = java.util.Set.of(
            "PRIMER_EXAMEN_DEL_ESTUDIANTE",
            "EQUIPO_NUEVO_PARA_EL_ESTUDIANTE",
            "CONEXION_PERDIDA",
            "CONEXION_RESTABLECIDA",
            "RESUMEN_COMPORTAMIENTO"
    );

    private static boolean cuentaParaIntegridad(AlertaEvidencia alerta) {
        if (alerta instanceof AlertaProceso proc) {
            String nombre = proc.getNombreProceso();
            if (nombre != null && EVENTOS_INFORMATIVOS_INTEGRIDAD.contains(nombre.toUpperCase())) {
                return false;
            }
        }
        return true;
    }

    public SesionSupervision registrarHeartbeat(String sesionId, String deviceId) {
        SesionSupervision sesion = sesionSupervisionRepository.findById(sesionId)
                .orElseThrow(() -> new ErrorNegocioException("La sesión no existe"));

        if (deviceId != null && sesion.getConexion() != null && sesion.getConexion().getDeviceId() != null) {
            if (!sesion.getConexion().getDeviceId().equalsIgnoreCase(deviceId)) {
                throw new DispositivoNoAutorizadoException("Dispositivo no autorizado para esta sesión");
            }
        }
        sesion.setUltimoHeartbeat(LocalDateTime.now());
        sesionSupervisionRepository.save(sesion);
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
