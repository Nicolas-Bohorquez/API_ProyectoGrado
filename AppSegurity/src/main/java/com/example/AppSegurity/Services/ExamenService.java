package com.example.AppSegurity.Services;

import com.example.AppSegurity.Enums.Estado_pin;
import com.example.AppSegurity.Enums.Sensibilidad_IA;
import com.example.AppSegurity.Models.Examen;
import com.example.AppSegurity.Repositorys.ExamenRepository;
import com.example.AppSegurity.Security.ErrorNegocioException;
import com.example.AppSegurity.Sub_Clases.ConfiguracionExamen;
import com.example.AppSegurity.Sub_Clases.ControlAcceso;
import com.example.AppSegurity.Sub_Clases.FechaExamen;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.AppSegurity.Repositorys.SesionSupervisionRepository;

@Service
public class ExamenService {

    //Metodos
    /*
    IniciarExamen() 
    SincronizacionMoodle()
     */
    @Autowired
    private ExamenRepository examenRepository;

    @Autowired
    private com.example.AppSegurity.Repositorys.EstudianteRepository estudianteRepository;

    @Autowired
    private com.example.AppSegurity.Repositorys.ProfesorRepository profesorRepository;
    
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    /**
     * Inscribe estudiantes en el examen. A los que no tienen cuenta se les crea una con una clave temporal ALEATORIA y
     * distinta para cada uno (antes todos recibían "123456", así que cualquiera podía entrar como cualquier compañero).
     * Devuelve las credenciales de las cuentas NUEVAS para que el docente las entregue; el estudiante debe cambiarla al entrar.
     */
    public List<java.util.Map<String, String>> bulkInscribirEstudiantes(String codigoExamen, List<com.example.AppSegurity.Models.Estudiante> estudiantesCsv) {
        List<java.util.Map<String, String>> credencialesNuevas = new ArrayList<>();
        Examen examen = examenRepository.findById(codigoExamen).orElseThrow(() -> new ErrorNegocioException("Examen no encontrado"));
        if (estudiantesCsv == null || estudiantesCsv.isEmpty()) {
            return credencialesNuevas;
        }
        String materiaCodigo = examen.getMateriaCodigo();

        java.util.Map<String, String> correoPorCedulaEnLote = new java.util.HashMap<>();
        for (com.example.AppSegurity.Models.Estudiante est : estudiantesCsv) {
            String cedula = est.getCedula() == null ? "" : est.getCedula().strip();
            if (cedula.isEmpty()) {
                throw new ErrorNegocioException("Cada estudiante de la lista debe incluir su cédula.");
            }
            est.setCedula(cedula);
            String emailNorm = est.getEmail() == null ? "" : est.getEmail().strip().toLowerCase();
            if (!emailNorm.isEmpty()) {
                if (profesorRepository.findByEmailInstitucionalIgnoreCase(emailNorm).isPresent()) {
                    throw new ErrorNegocioException("El correo " + emailNorm + " pertenece a un docente registrado y no puede asignarse a un estudiante.");
                }
                for (com.example.AppSegurity.Models.Estudiante conMismoCorreo : estudianteRepository.findByEmailIgnoreCase(emailNorm)) {
                    if (!cedula.equals(conMismoCorreo.getCedula())) {
                        throw new ErrorNegocioException("El correo " + emailNorm + " ya está registrado para otro estudiante.");
                    }
                }
                String otraCedula = correoPorCedulaEnLote.putIfAbsent(emailNorm, cedula);
                if (otraCedula != null && !otraCedula.equals(cedula)) {
                    throw new ErrorNegocioException("El correo " + emailNorm + " está duplicado para distintas cédulas en la lista.");
                }
                est.setEmail(emailNorm);
            } else {
                est.setEmail(null);
            }
        }
        
        for (com.example.AppSegurity.Models.Estudiante est : estudiantesCsv) {
            java.util.Optional<com.example.AppSegurity.Models.Estudiante> existenteOpt = estudianteRepository.findByCedula(est.getCedula());
            if (existenteOpt.isPresent()) {
                com.example.AppSegurity.Models.Estudiante existente = existenteOpt.get();
                boolean tieneMateria = false;
                if (existente.getMateriasInscritas() == null) {
                    existente.setMateriasInscritas(new ArrayList<>());
                }
                for (com.example.AppSegurity.Sub_Clases.Materia m : existente.getMateriasInscritas()) {
                    if (m.getCodigoMateria().equals(materiaCodigo)) {
                        tieneMateria = true;
                        break;
                    }
                }
                if (!tieneMateria) {
                    com.example.AppSegurity.Sub_Clases.Materia m = new com.example.AppSegurity.Sub_Clases.Materia();
                    m.setCodigoMateria(materiaCodigo);
                    m.setNombreMateria("Matricula Masiva");
                    existente.getMateriasInscritas().add(m);
                    estudianteRepository.save(existente);
                }
            } else {
                est.setEstudianteId("EST-" + est.getCedula());
                String claveTemporal = com.example.AppSegurity.Security.GeneradorClaves.claveTemporal();
                est.setPasswordHash(passwordEncoder.encode(claveTemporal));
                est.setClaveTemporal(true);
                est.setEstadoUsuario(com.example.AppSegurity.Enums.EstadoUsuario.ACTIVO);
                
                com.example.AppSegurity.Sub_Clases.Materia m = new com.example.AppSegurity.Sub_Clases.Materia();
                m.setCodigoMateria(materiaCodigo);
                m.setNombreMateria("Matricula Masiva");
                
                List<com.example.AppSegurity.Sub_Clases.Materia> materias = new ArrayList<>();
                materias.add(m);
                est.setMateriasInscritas(materias);
                
                com.example.AppSegurity.Sub_Clases.Auditoria aud = new com.example.AppSegurity.Sub_Clases.Auditoria();
                aud.setFechaRegistro(java.time.LocalDateTime.now());
                est.setAuditoria(aud);
                
                estudianteRepository.save(est);

                java.util.Map<String, String> credencial = new java.util.LinkedHashMap<>();
                credencial.put("estudianteId", est.getEstudianteId());
                credencial.put("nombre", ((est.getNombre() == null ? "" : est.getNombre()) + " " + (est.getApellidos() == null ? "" : est.getApellidos())).trim());
                credencial.put("email", est.getEmail());
                credencial.put("cedula", est.getCedula());
                credencial.put("claveTemporal", claveTemporal);
                credencialesNuevas.add(credencial);
            }
        }
        return credencialesNuevas;
    }

    public Examen crearExamen(String codigoExamen, String codigoProfesor, String materiaCodigo, FechaExamen fecha) {
        // "codigoExamen" es el _id de Mongo y llega del cliente (un UUID nuevo en cada creación, nunca reutilizado
        // a propósito). Sin este chequeo, examenRepository.save() hace upsert por _id: si alguna vez colisionara con
        // el código de OTRO examen (de cualquier docente), lo sobrescribiría entero sin ningún aviso.
        if (examenRepository.existsById(codigoExamen)) {
            throw new IllegalStateException("Ya existe un examen con el código " + codigoExamen + ".");
        }

        //Generamos el PIN de manera aleatoria y comprobamos que no colisione con ningún examen existente
        String PIN_aleatorio = generarPinUnico();

        //Creamos control de acceso e introducimos el PIN recien generado
        ControlAcceso controlAcceso = new ControlAcceso();
        controlAcceso.setPinSesion(PIN_aleatorio);
        controlAcceso.setEstadoPin(Estado_pin.FINALIZADO); //Cerrado por defecto

        //Creamos una configuración por defecto para el examen en caso de que el profe se olvide
        ConfiguracionExamen configuracionExamenPorDefecto = new ConfiguracionExamen(
                true, // Reconocimiento Facial
                true, // Detección Objetos
                false, // Análisis de Audio
                true, // Monitoreo de Procesos
                true, // Análisis de Teclado
                Sensibilidad_IA.MEDIA,
                120,
                3,
                new ArrayList<String>(),
                new ArrayList<String>());
        configuracionExamenPorDefecto.setIntentosMaximos(3);

        //Creamos y armamos al examen con todos los datos listos
        Examen examen = new Examen(
                codigoExamen,
                codigoProfesor,
                materiaCodigo,
                controlAcceso,
                fecha,
                configuracionExamenPorDefecto
        );

        //Se lo enviamos a MongoDB pa que lo guarde
        return examenRepository.save(examen);

    }

    private String generarPinUnico() {
        java.security.SecureRandom rng = new java.security.SecureRandom();
        final String alfabeto = "0123456789ABCDEF";
        for (int intento = 0; intento < 25; intento++) {
            int longitud = intento < 15 ? 6 : 8;
            StringBuilder sb = new StringBuilder(longitud);
            for (int i = 0; i < longitud; i++) {
                sb.append(alfabeto.charAt(rng.nextInt(alfabeto.length())));
            }
            String candidato = sb.toString();
            if (examenRepository.findFirstByControlAccesoPinSesion(candidato).isEmpty()) {
                return candidato;
            }
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    public Examen configurarDesdeMapa(String codigoExamen, java.util.Map<String, Object> payload) {
        Examen examen = examenRepository.findById(codigoExamen)
                .orElseThrow(() -> new ErrorNegocioException("Error al traer la información del examen"));

        // Actualizar FechaExamen si viene en el payload
        if (payload.containsKey("fechaString") && payload.containsKey("horaInicioString")) {
            com.example.AppSegurity.Sub_Clases.FechaExamen fecha = examen.getFechaExamen();
            if (fecha == null) fecha = new com.example.AppSegurity.Sub_Clases.FechaExamen();
            
                String fechaStr = (String) payload.get("fechaString");
                String fechaFinStr = payload.containsKey("fechaFinString") ? (String) payload.get("fechaFinString") : fechaStr;
                String horaStr = (String) payload.get("horaInicioString");
                
                try {
                    // Parsear fecha y hora para inicio y fin
                    java.time.LocalDate date = java.time.LocalDate.parse(fechaStr);
                    java.time.LocalDate dateFin = java.time.LocalDate.parse(fechaFinStr);
                    
                    // "15:00" -> LocalTime
                    java.time.LocalTime time = java.time.LocalTime.parse(horaStr);
                    java.time.LocalDateTime inicio = java.time.LocalDateTime.of(date, time);
                    java.time.LocalDateTime inicioBaseFin = java.time.LocalDateTime.of(dateFin, time);
                    
                    fecha.setCreacion(java.time.LocalDateTime.now());
                    fecha.setHoraInicio(inicio);
                    
                    // Si viene duracion, calculamos horaFin
                    if (payload.containsKey("duracionExamen")) {
                        int duracion = Integer.parseInt(payload.get("duracionExamen").toString());
                        fecha.setHoraFin(inicioBaseFin.plusMinutes(duracion));
                }
                
                examen.setFechaExamen(fecha);
            } catch (Exception e) {
                System.out.println("Error parseando fechas desde mapa: " + e.getMessage());
            }
        }

        // Actualizar Configuración IA
        ConfiguracionExamen config = examen.getConfiguracionExamen();
        if (config == null) config = new ConfiguracionExamen();
        
        if (payload.containsKey("activarReconocimientoFacial")) config.setActivarReconocimientoFacial((Boolean) payload.get("activarReconocimientoFacial"));
        if (payload.containsKey("activarDeteccionObjetos")) config.setActivarDeteccionObjetos((Boolean) payload.get("activarDeteccionObjetos"));
        if (payload.containsKey("activarAnalisisAudio")) config.setActivarAnalisisAudio((Boolean) payload.get("activarAnalisisAudio"));
        if (payload.containsKey("activarMonitoreoProcesos")) config.setActivarMonitoreoProcesos((Boolean) payload.get("activarMonitoreoProcesos"));
        if (payload.containsKey("activarAnalisisTeclado")) config.setActivarAnalisisTeclado((Boolean) payload.get("activarAnalisisTeclado"));
        if (payload.containsKey("sensibilidadIA")) config.setSensibilidadIA(com.example.AppSegurity.Enums.Sensibilidad_IA.valueOf((String) payload.get("sensibilidadIA")));
        if (payload.containsKey("duracionExamen")) config.setDuracionExamen((Integer) payload.get("duracionExamen"));
        if (payload.containsKey("permitirReintentos")) config.setPermitirReintentos((Integer) payload.get("permitirReintentos"));
        if (payload.containsKey("intentosMaximos")) config.setIntentosMaximos((Integer) payload.get("intentosMaximos"));
        if (payload.containsKey("procesosPermitidos")) config.setProcesosPermitidos((java.util.List<String>) payload.get("procesosPermitidos"));
        if (payload.containsKey("urlsPermitidas")) config.setUrlsPermitidas((java.util.List<String>) payload.get("urlsPermitidas"));

        examen.setConfiguracionExamen(config);
        return examenRepository.save(examen);
    }

    @Autowired
    private SesionSupervisionRepository sesionSupervisionRepository;

    private void cerrarSesionesActivas(String codigoExamen) {
        List<com.example.AppSegurity.Models.SesionSupervision> sesiones =
                sesionSupervisionRepository.findByExamenId(codigoExamen);
        List<com.example.AppSegurity.Models.SesionSupervision> porCerrar = new ArrayList<>();
        java.time.LocalDateTime ahora = java.time.LocalDateTime.now();
        for (com.example.AppSegurity.Models.SesionSupervision sesion : sesiones) {
            if (sesion.getEstadoSesion() == com.example.AppSegurity.Enums.EstadoSesion.INICIADA
                    || sesion.getEstadoSesion() == com.example.AppSegurity.Enums.EstadoSesion.INTERRUMPIDA) {
                sesion.setEstadoSesion(com.example.AppSegurity.Enums.EstadoSesion.FINALIZADA);
                sesion.setFechaFinalizacion(ahora);
                sesion.setCerradaPorSalaDocente(Boolean.TRUE);
                porCerrar.add(sesion);
            }
        }
        if (!porCerrar.isEmpty()) {
            sesionSupervisionRepository.saveAll(porCerrar);
        }
    }

    public void abrirSalaExamen(String codigoExamen) {
        //Buscar y traer el examen de la base de datos
        Examen examen = examenRepository.findById(codigoExamen)
                .orElseThrow(() -> new ErrorNegocioException("Error al traer la información del examen"));

        // Limpiar sesiones previas (fantasmas) para que el examen inicie en blanco
        cerrarSesionesActivas(codigoExamen);

        //Cambiamos el estado del PIN del examen a activo
        examen.getControlAcceso().setEstadoPin(Estado_pin.ACTIVO);

        //Guardamos cambios
        examenRepository.save(examen);

    }

    public void cerrarSalaExamen(String codigoExamen) {
        //Buscamos y traemos la info del examen creado de la base de datos
        Examen examen = examenRepository.findById(codigoExamen)
                .orElseThrow(() -> new ErrorNegocioException("Error al traer la información del examen"));

        //Cambiamos el estado del PIN del examen a finalizado
        examen.getControlAcceso().setEstadoPin(Estado_pin.FINALIZADO);

        //Guardamos cambios del examen y finalizamos sesiones activas/interrumpidas pendientes
        examenRepository.save(examen);
        cerrarSesionesActivas(codigoExamen);
    }

    public List<Examen> obtenerExamenesProfesor(String profesorId) {
        //Busca y trae todos los examenes que tenga el ID del profesor
        return examenRepository.findByProfesorId(profesorId);
    }
    
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60000)
    public void verificarExamenesExpirados() {
        java.time.LocalDateTime ahora = java.time.LocalDateTime.now();
        List<Examen> expirados = examenRepository
                .findByControlAccesoEstadoPinAndFechaExamenHoraFinBefore(Estado_pin.ACTIVO, ahora);
        if (expirados.isEmpty()) {
            return;
        }
        for (Examen examen : expirados) {
            System.out.println("[CRON] Finalizando examen expirado: " + examen.getCodigoExamen());
            examen.getControlAcceso().setEstadoPin(Estado_pin.FINALIZADO);
            examenRepository.save(examen);
            cerrarSesionesActivas(examen.getCodigoExamen());
        }
    }

    // Obtener un examen por su código
    public Examen obtenerExamenPorId(String codigoExamen) {
        return examenRepository.findById(codigoExamen).orElse(null);
    }

    // Guardar/actualizar un examen existente
    public void guardarExamen(Examen examen) {
        examenRepository.save(examen);
    }

}
