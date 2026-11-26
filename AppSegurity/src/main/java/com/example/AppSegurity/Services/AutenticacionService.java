package com.example.AppSegurity.Services;

import com.example.AppSegurity.Enums.EstadoUsuario;
import com.example.AppSegurity.Models.Estudiante;
import com.example.AppSegurity.Models.Profesor;
import com.example.AppSegurity.Repositorys.EstudianteRepository;
import com.example.AppSegurity.Repositorys.ProfesorRepository;
import com.example.AppSegurity.Security.JwtProveedor;
import com.example.AppSegurity.Security.ServicioDetallesUsuarioPersonalizados;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import com.example.AppSegurity.DTO.RegistroSolicitudRequest;
import com.example.AppSegurity.DTO.RegistroConfirmarRequest;
import com.example.AppSegurity.DTO.RecuperarSolicitudRequest;
import com.example.AppSegurity.DTO.RecuperarConfirmarRequest;
import com.example.AppSegurity.Sub_Clases.Auditoria;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AutenticacionService {

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private ProfesorRepository profesorRepository;

    @Autowired
    private JwtProveedor jwtProveedor;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private ServicioDetallesUsuarioPersonalizados userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private com.example.AppSegurity.Security.LimitadorIntentos limitador;

    @Autowired
    private com.example.AppSegurity.Security.LimitadorIntentosPorIp limitadorPorIp;

    // Los códigos OTP deben ser impredecibles: SecureRandom, no java.util.Random
    private static final java.security.SecureRandom OTP_ALEATORIO = new java.security.SecureRandom();
    private static final int MAX_INTENTOS_OTP = 5;                 // tras 5 códigos erróneos el OTP se invalida
    private static final long ENFRIAMIENTO_OTP_SEGUNDOS = 60;       // mínimo entre dos solicitudes de código al mismo correo
    // Cuánto tiempo después de expirar un token de acceso aún se acepta refrescarlo (sesión de examen continua)
    private static final long VENTANA_REFRESH_HORAS = resolverVentanaRefresh();
    // Tope absoluto de vida de una cadena de refresh sin volver a autenticar con contraseña
    private static final long MAX_CADENA_REFRESH_HORAS = Math.max(VENTANA_REFRESH_HORAS, 24L);

    private static long resolverVentanaRefresh() {
        try {
            long horas = Long.parseLong(System.getenv().getOrDefault("JWT_REFRESH_HORAS", "12"));
            return horas > 0 ? horas : 12;
        } catch (NumberFormatException e) {
            return 12;
        }
    }

    // --- CACHÉ DE CÓDIGOS OTP PARA REGISTRO TEMPORAL (10 MINUTOS) ---
    private static class OtpInfo {
        String codigo;
        LocalDateTime expiracion;
        String rol;
        int intentos;

        OtpInfo(String codigo, LocalDateTime expiracion, String rol) {
            this.codigo = codigo;
            this.expiracion = expiracion;
            this.rol = rol;
        }
    }

    private final java.util.concurrent.ConcurrentHashMap<String, OtpInfo> cacheOtp = new java.util.concurrent.ConcurrentHashMap<>();

    /** Evita que se use el envío de correos como arma: un código nuevo por correo cada ENFRIAMIENTO_OTP_SEGUNDOS. */
    private void exigirEnfriamientoOtp(String email) {
        OtpInfo previo = cacheOtp.get(email);
        if (previo != null && LocalDateTime.now().isBefore(previo.expiracion.minusMinutes(10).plusSeconds(ENFRIAMIENTO_OTP_SEGUNDOS))) {
            throw new IllegalStateException("Ya se envió un código hace instantes. Espera un minuto antes de pedir otro.");
        }
    }

    /**
     * Compara el código en tiempo constante y cuenta los errores: tras 5 fallos el código se invalida (hay que pedir otro) y
     * el correo queda bloqueado un rato. Un OTP de 6 dígitos sin este límite se adivina en minutos.
     */
    private boolean otpCoincide(String email, OtpInfo otp, String ingresado) {
        String clave = "otp:" + email;
        limitador.verificar(clave);
        boolean coincide = java.security.MessageDigest.isEqual(
                otp.codigo.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                ingresado.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (coincide) {
            limitador.registrarExito(clave);
            return true;
        }
        limitador.registrarFallo(clave);
        otp.intentos++;
        if (otp.intentos >= MAX_INTENTOS_OTP) {
            cacheOtp.remove(email);
        }
        return false;
    }

    public void solicitarCodigoRegistro(RegistroSolicitudRequest req) {
        if (req == null || req.getEmail() == null || req.getCedula() == null || req.getRol() == null) {
            throw new IllegalArgumentException("Todos los campos obligatorios deben ser diligenciados.");
        }

        String email = req.getEmail().trim().toLowerCase();
        String cedula = req.getCedula().trim();
        String rol = req.getRol().trim().toUpperCase();

        // 1. Validar dominio institucional (unicamente @unicesar.edu.co)
        if (!email.matches("^[A-Za-z0-9._%+-]+@unicesar\\.edu\\.co$")) {
            throw new IllegalArgumentException("El correo debe pertenecer al dominio institucional (@unicesar.edu.co).");
        }

        // 2. Validar que la cédula sea numérica y válida
        if (!cedula.matches("^[0-9]{6,12}$")) {
            throw new IllegalArgumentException("La cédula debe contener entre 6 y 12 dígitos numéricos.");
        }

        // 3. Validar no duplicados en BD según el rol y unicidad cruzada de correo
        if ("ESTUDIANTE".equals(rol)) {
            if (!estudianteRepository.findByEmailIgnoreCase(email).isEmpty()) {
                throw new IllegalStateException("El correo institucional ya se encuentra registrado para un estudiante.");
            }
            if (profesorRepository.findByEmailInstitucional(email).isPresent()
                    || profesorRepository.findByEmailInstitucionalIgnoreCase(email).isPresent()) {
                throw new IllegalStateException("El correo institucional ya se encuentra registrado para un docente.");
            }
            if (estudianteRepository.findByCedula(cedula).isPresent()) {
                throw new IllegalStateException("La cédula ya se encuentra registrada en el sistema.");
            }
        } else if ("PROFESOR".equals(rol)) {
            if (profesorRepository.findByEmailInstitucional(email).isPresent()
                    || profesorRepository.findByEmailInstitucionalIgnoreCase(email).isPresent()) {
                throw new IllegalStateException("El correo institucional ya se encuentra registrado para un docente.");
            }
            if (!estudianteRepository.findByEmailIgnoreCase(email).isEmpty()) {
                throw new IllegalStateException("El correo institucional ya se encuentra registrado para un estudiante.");
            }
            if (profesorRepository.findByCedula(cedula).isPresent()) {
                throw new IllegalStateException("La cédula ya se encuentra registrada en el sistema.");
            }
        } else {
            throw new IllegalArgumentException("Rol no válido. Debe ser ESTUDIANTE o PROFESOR.");
        }

        // 4. Generar código de 6 dígitos numéricos
        exigirEnfriamientoOtp(email);
        String codigoOtp = String.format("%06d", OTP_ALEATORIO.nextInt(1000000));

        // 5. Guardar en caché con expiración de 10 minutos
        cacheOtp.put(email, new OtpInfo(codigoOtp, LocalDateTime.now().plusMinutes(10), rol));

        // 6. Enviar correo electrónico institucional
        emailService.enviarCodigoVerificacion(email, req.getNombre(), codigoOtp, rol);
    }

    public void confirmarRegistroEstudiante(RegistroConfirmarRequest req) {
        if (req == null || req.getEmail() == null || req.getCodigoOtp() == null || req.getPassword() == null
                || req.getNombre() == null || req.getNombre().isBlank()
                || req.getApellidos() == null || req.getApellidos().isBlank()
                || req.getCedula() == null || req.getCedula().isBlank()) {
            throw new IllegalArgumentException("Todos los campos son requeridos.");
        }

        String email = req.getEmail().trim().toLowerCase();
        String codigoIngresado = req.getCodigoOtp().trim();

        // 1. Validar OTP en caché

        OtpInfo otpInfo = cacheOtp.get(email);

            if (otpInfo == null) {
                throw new IllegalStateException("No hay una solicitud de código pendiente para este correo o el código ya venció.");
            }
            if (LocalDateTime.now().isAfter(otpInfo.expiracion)) {
                cacheOtp.remove(email);
                throw new IllegalStateException("El código de verificación ha expirado. Solicita uno nuevo.");
            }
            if (!otpCoincide(email, otpInfo, codigoIngresado)) {
                throw new IllegalArgumentException("El código de verificación de 6 dígitos es incorrecto.");
            }
            if (otpInfo.rol != null && !"ESTUDIANTE".equals(otpInfo.rol)) {
                throw new IllegalArgumentException("El código de verificación no corresponde a un registro de estudiante.");
            }

        // 2. Validar contraseña mínima
        if (req.getPassword().length() < 6) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres.");
        }

        // 3. Evitar duplicados o sobrescritura de cuentas existentes
        String cedulaLimpia = req.getCedula().trim();
        String estId = "EST-" + cedulaLimpia;
        if (estudianteRepository.existsById(estId)
                || estudianteRepository.findByCedula(cedulaLimpia).isPresent()
                || !estudianteRepository.findByEmailIgnoreCase(email).isEmpty()
                || profesorRepository.findByEmailInstitucional(email).isPresent()
                || profesorRepository.findByEmailInstitucionalIgnoreCase(email).isPresent()) {
            throw new IllegalStateException("El estudiante, cédula o correo ya se encuentra registrado en el sistema.");
        }

        // 4. Crear el nuevo Estudiante
        Estudiante nuevo = new Estudiante();
        nuevo.setEstudianteId(estId);
        nuevo.setNombre(req.getNombre().trim());
        nuevo.setApellidos(req.getApellidos().trim());
        nuevo.setCedula(cedulaLimpia);
        nuevo.setEmail(email);
        nuevo.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        nuevo.setEstadoUsuario(EstadoUsuario.ACTIVO);
        nuevo.setMateriasInscritas(new java.util.ArrayList<>());

        Auditoria aud = new Auditoria();
        aud.setFechaRegistro(LocalDateTime.now());
        nuevo.setAuditoria(aud);

        estudianteRepository.save(nuevo);
        cacheOtp.remove(email);
        System.out.println("[REGISTRO EXITOSO] Estudiante creado: " + email + " (" + nuevo.getEstudianteId() + ")");
    }

    public void confirmarRegistroProfesor(RegistroConfirmarRequest req) {
        if (req == null || req.getEmail() == null || req.getCodigoOtp() == null || req.getPassword() == null
                || req.getNombre() == null || req.getNombre().isBlank()
                || req.getApellidos() == null || req.getApellidos().isBlank()
                || req.getCedula() == null || req.getCedula().isBlank()) {
            throw new IllegalArgumentException("Todos los campos son requeridos.");
        }

        String email = req.getEmail().trim().toLowerCase();
        String codigoIngresado = req.getCodigoOtp().trim();

        // 1. Validar OTP en caché

        OtpInfo otpInfo = cacheOtp.get(email);

            if (otpInfo == null) {
                throw new IllegalStateException("No hay una solicitud de código pendiente para este correo o el código ya venció.");
            }
            if (LocalDateTime.now().isAfter(otpInfo.expiracion)) {
                cacheOtp.remove(email);
                throw new IllegalStateException("El código de verificación ha expirado. Solicita uno nuevo.");
            }
            if (!otpCoincide(email, otpInfo, codigoIngresado)) {
                throw new IllegalArgumentException("El código de verificación de 6 dígitos es incorrecto.");
            }
            if (otpInfo.rol != null && !"PROFESOR".equals(otpInfo.rol)) {
                throw new IllegalArgumentException("El código de verificación no corresponde a un registro de docente.");
            }

        // 2. Validar contraseña mínima
        if (req.getPassword().length() < 6) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres.");
        }

        // 3. Código profesor y verificación anti-sobrescritura
        String cedulaLimpia = req.getCedula().trim();
        String codProf = (req.getCodigoProfesor() != null && !req.getCodigoProfesor().trim().isEmpty())
                ? req.getCodigoProfesor().trim()
                : "PROF-" + cedulaLimpia;

        if (profesorRepository.existsById(codProf)
                || profesorRepository.findByCedula(cedulaLimpia).isPresent()
                || profesorRepository.findByEmailInstitucional(email).isPresent()
                || profesorRepository.findByEmailInstitucionalIgnoreCase(email).isPresent()
                || !estudianteRepository.findByEmailIgnoreCase(email).isEmpty()) {
            throw new IllegalStateException("El código de docente, cédula o correo ya se encuentra registrado en el sistema.");
        }

        // 4. Crear el nuevo Profesor
        Profesor nuevo = new Profesor();
        nuevo.setCodigoProfesor(codProf);
        nuevo.setNombre(req.getNombre().trim());
        nuevo.setApellidos(req.getApellidos().trim());
        nuevo.setCedula(cedulaLimpia);
        nuevo.setEmailInstitucional(email);
        nuevo.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        nuevo.setEstado(EstadoUsuario.ACTIVO);
        nuevo.setRol("PROFESOR");
        nuevo.setMaterias(new java.util.ArrayList<>());

        Auditoria aud = new Auditoria();
        aud.setFechaRegistro(LocalDateTime.now());
        nuevo.setAuditoria(aud);

        profesorRepository.save(nuevo);
        cacheOtp.remove(email);
        System.out.println("[REGISTRO EXITOSO] Docente creado: " + email + " (" + nuevo.getCodigoProfesor() + ")");
    }

    public void solicitarCodigoRecuperacion(RecuperarSolicitudRequest req) {
        if (req == null || req.getEmail() == null || req.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("El correo institucional es obligatorio.");
        }

        String email = req.getEmail().trim().toLowerCase();

        // 1. Buscar si el usuario existe como Estudiante o Profesor
        String nombreUsuario = null;
        boolean existe = false;

        var estudiantes = estudianteRepository.findByEmailIgnoreCase(email);
        if (!estudiantes.isEmpty()) {
            nombreUsuario = estudiantes.get(0).getNombre() + " " + estudiantes.get(0).getApellidos();
            existe = true;
        } else {
            var profOpt = profesorRepository.findByEmailInstitucional(email);
            if (profOpt.isPresent()) {
                nombreUsuario = profOpt.get().getNombre() + " " + profOpt.get().getApellidos();
                existe = true;
            }
        }

        if (!existe) {
            throw new IllegalArgumentException("No se encontró ninguna cuenta asociada al correo institucional: " + email);
        }

        // 2. Generar código OTP de 6 dígitos numéricos
        exigirEnfriamientoOtp(email);
        String codigoOtp = String.format("%06d", OTP_ALEATORIO.nextInt(1000000));

        // 3. Guardar en caché con expiración de 10 minutos
        cacheOtp.put(email, new OtpInfo(codigoOtp, LocalDateTime.now().plusMinutes(10), "RECUPERACION"));

        // 4. Enviar correo de restablecimiento
        emailService.enviarCodigoRecuperacion(email, nombreUsuario, codigoOtp);
    }

    public void confirmarRecuperacionPassword(RecuperarConfirmarRequest req) {
        if (req == null || req.getEmail() == null || req.getCodigoOtp() == null || req.getNuevaPassword() == null) {
            throw new IllegalArgumentException("Todos los campos son requeridos.");
        }

        String email = req.getEmail().trim().toLowerCase();
        String codigoIngresado = req.getCodigoOtp().trim();

        // 1. Validar OTP en caché

        OtpInfo otpInfo = cacheOtp.get(email);

            if (otpInfo == null) {
                throw new IllegalStateException("No hay una solicitud de código pendiente para este correo o el código ya venció.");
            }
            if (LocalDateTime.now().isAfter(otpInfo.expiracion)) {
                cacheOtp.remove(email);
                throw new IllegalStateException("El código de verificación ha expirado. Solicita uno nuevo.");
            }
            if (!otpCoincide(email, otpInfo, codigoIngresado)) {
                throw new IllegalArgumentException("El código de verificación de 6 dígitos es incorrecto.");
            }

        // 2. Validar contraseña mínima (8 caracteres)
        if (req.getNuevaPassword().length() < 8) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres.");
        }

        // 3. Actualizar contraseña en la BD
        String nuevoHash = passwordEncoder.encode(req.getNuevaPassword());
        boolean actualizada = false;

        var estudiantes = estudianteRepository.findByEmailIgnoreCase(email);
        if (!estudiantes.isEmpty()) {
            for (Estudiante est : estudiantes) {
                est.setPasswordHash(nuevoHash);
                if (est.getSeguridadJwt() == null) {
                    est.setSeguridadJwt(new com.example.AppSegurity.Sub_Clases.SeguridadJwt());
                }
                est.getSeguridadJwt().incrementarVersionToken();
                estudianteRepository.save(est);
            }
            actualizada = true;
            System.out.println("[RECUPERACIÓN EXITOSA] Contraseña actualizada para estudiante: " + email);
        } else {
            var profOpt = profesorRepository.findByEmailInstitucional(email);
            if (profOpt.isPresent()) {
                Profesor prof = profOpt.get();
                prof.setPasswordHash(nuevoHash);
                if (prof.getSeguridadJwt() == null) {
                    prof.setSeguridadJwt(new com.example.AppSegurity.Sub_Clases.SeguridadJwt());
                }
                prof.getSeguridadJwt().incrementarVersionToken();
                profesorRepository.save(prof);
                actualizada = true;
                System.out.println("[RECUPERACIÓN EXITOSA] Contraseña actualizada para docente: " + email);
            }
        }

        if (!actualizada) {
            throw new IllegalStateException("No se pudo localizar el usuario para actualizar sus credenciales.");
        }

        cacheOtp.remove(email);
    }

    public String loginEstudiante(String email, String passwordTextoPlano) {
        return conLimiteDeIntentos(email, () -> loginEstudianteInterno(email, passwordTextoPlano));
    }

    public String loginProfesor(String email, String passwordTextoPlano) {
        return conLimiteDeIntentos(email, () -> loginProfesorInterno(email, passwordTextoPlano));
    }

    /**
     * Tras 5 contraseñas erróneas seguidas para un correo, ese correo queda bloqueado 15 minutos. Además, para frenar
     * "credential spraying" (una misma clave probada contra muchas cuentas distintas, que nunca dispara el límite por
     * cuenta), se cuentan también los fallos por IP con un umbral mucho más alto (ver LimitadorIntentosPorIp).
     */
    private String conLimiteDeIntentos(String email, java.util.function.Supplier<String> inicio) {
        String clave = "login:" + email;
        String ip = ipDelClienteActual();
        limitador.verificar(clave);
        limitadorPorIp.verificar(ip);
        try {
            String token = inicio.get();
            limitador.registrarExito(clave);
            return token;
        } catch (org.springframework.security.core.AuthenticationException e) {
            limitador.registrarFallo(clave);
            limitadorPorIp.registrarFallo(ip);
            throw e;
        }
    }

    /**
     * IP real del cliente para la petición HTTP en curso. Railway (y cualquier proxy delante del backend) hace que
     * todas las peticiones lleguen desde la MISMA IP interna si se usa request.getRemoteAddr() a secas — eso volvería
     * este límite global por accidente. Por eso se lee X-Forwarded-For.
     *
     * Se toma el ÚLTIMO valor de la lista, no el primero: cualquier cliente puede mandar ese encabezado con el
     * contenido que quiera, y Railway (el único proxy de confianza delante del backend hoy) AGREGA la IP real al
     * final de lo que ya traiga la petición en vez de reemplazarlo. Tomar el primer valor (como se hacía antes)
     * dejaba que cualquiera falsificara su IP con solo mandar "X-Forwarded-For: lo-que-sea" y rotarlo en cada
     * intento, anulando por completo el límite por IP contra "credential spraying" (ver LimitadorIntentosPorIp).
     * Si en el futuro se agrega OTRO proxy de confianza delante de Railway (un CDN, por ejemplo), hay que tomar el
     * valor "n-ésimo desde el final" según cuántos proxies de confianza haya, no volver al primero.
     */
    private static String ipDelClienteActual() {
        try {
            var attrs = org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
            var request = ((org.springframework.web.context.request.ServletRequestAttributes) attrs).getRequest();
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String[] partes = xff.split(",");
                return partes[partes.length - 1].trim();
            }
            return request.getRemoteAddr();
        } catch (IllegalStateException e) {
            // No hay una petición HTTP en curso (p. ej. se llamó directo desde una prueba): no se puede limitar por IP.
            return "sin-contexto-http";
        }
    }

    private String loginEstudianteInterno(String email, String passwordTextoPlano) {
        //
        Authentication auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, passwordTextoPlano));

        //Buscamos al estudiante por su email en la base de datos 
        java.util.List<Estudiante> estudiantes = estudianteRepository.findByEmailIgnoreCase(email);
        Estudiante estudiante = null;
        if (estudiantes.size() > 1) {
            for (Estudiante e : estudiantes) {
                if (e.getEstudianteId() != null && e.getEstudianteId().contains(" ")) {
                    estudianteRepository.delete(e);
                } else {
                    estudiante = e;
                }
            }
        } else if (estudiantes.size() == 1) {
            estudiante = estudiantes.get(0);
        }
        if (estudiante == null) {
            throw new RuntimeException("No se encontro el email del estudiante");
        }

        //Validamos que el estudiante en cuestión su estado no sea inactivo o pfu
        if (estudiante.getEstadoUsuario() != EstadoUsuario.ACTIVO) {
            throw new RuntimeException("Solo los estudiante activos pueden iniciar sesión");
        }

        //Ya con el estudiante validado generamos el token del estudiante
        int tokenVersion = estudiante.getSeguridadJwt() != null ? estudiante.getSeguridadJwt().versionActual() : 0;
        String token = jwtProveedor.generarToken(auth, tokenVersion, System.currentTimeMillis());

        //Actualizamos auditoria del estudiante para llevar el registro
        if (estudiante.getAuditoria() == null) {
            Auditoria aud = new Auditoria();
            aud.setFechaRegistro(LocalDateTime.now());
            estudiante.setAuditoria(aud);
        }
        estudiante.getAuditoria().setUltimoAcceso(LocalDateTime.now());

        //Guardamos los cambios del estudiante en su base de datos
        estudianteRepository.save(estudiante);

        //Retornamos el token generado para ese estudiante
        return token;
    }

    private String loginProfesorInterno(String email, String passwordTextoPlano) {
        //
        Authentication auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, passwordTextoPlano));

        //Buscamos al profesor por su email en la base de datos 
        Profesor profesor = profesorRepository.findByEmailInstitucional(email)
                .or(() -> profesorRepository.findByEmailInstitucionalIgnoreCase(email))
                .orElseThrow(() -> new RuntimeException("No se encontro el email del profesor"));

        //Validamos que el profesor en cuestión su estado no sea inactivo o suspendido
        if (profesor.getEstado() != EstadoUsuario.ACTIVO) {
            throw new RuntimeException("Solo los profesores activos pueden iniciar sesión");
        }

        //Ya con el profesor validado generamos el token del mismo
        int tokenVersion = profesor.getSeguridadJwt() != null ? profesor.getSeguridadJwt().versionActual() : 0;
        String token = jwtProveedor.generarToken(auth, tokenVersion, System.currentTimeMillis());

        //Actualizamos auditoria del profesor para llevar el registro
        if (profesor.getAuditoria() == null) {
            Auditoria aud = new Auditoria();
            aud.setFechaRegistro(LocalDateTime.now());
            profesor.setAuditoria(aud);
        }
        profesor.getAuditoria().setUltimoAcceso(LocalDateTime.now());

        //Guardamos los cambios del profesor en la base de datos
        profesorRepository.save(profesor);

        //Retornamos el token generado para ese profesor
        return token;
    }

    public String refrescarToken(String freshTokenViejo) {

        //Validamos que el token sigue siendo autentico
        if (!jwtProveedor.validarFirma(freshTokenViejo)) {
            throw new RuntimeException("El token proporcionado es invalido o fue alterado");
        }
        // Un token expirado solo se puede refrescar durante una ventana (por defecto 12 h): antes un token robado se renovaba
        // para siempre, aunque hubiera expirado hace meses.
        java.util.Date expiracion = jwtProveedor.extraerExpiracion(freshTokenViejo);
        if (expiracion != null && new java.util.Date().getTime() - expiracion.getTime() > VENTANA_REFRESH_HORAS * 3600_000L) {
            throw new RuntimeException("La sesión caducó: inicia sesión de nuevo");
        }

        //Extraemos el email y datos de revocación/origen del token viejo
        String email = jwtProveedor.extraerEmail(freshTokenViejo);
        int tokenVersionViejo = jwtProveedor.extraerTokenVersion(freshTokenViejo);
        Long origIatMs = jwtProveedor.extraerOrigenCadenaMs(freshTokenViejo);
        if (origIatMs != null && System.currentTimeMillis() - origIatMs > MAX_CADENA_REFRESH_HORAS * 3600_000L) {
            throw new RuntimeException("La sesión superó el tiempo máximo sin re-autenticación");
        }

        // Una cuenta suspendida o dada de baja no puede seguir renovando su token, ni tokens revocados tras cambio de clave
        int versionActual = 0;
        var profesorRefresh = profesorRepository.findByEmailInstitucional(email)
                .or(() -> profesorRepository.findByEmailInstitucionalIgnoreCase(email));
        if (profesorRefresh.isPresent()) {
            Profesor profRef = profesorRefresh.get();
            if (profRef.getEstado() != EstadoUsuario.ACTIVO) {
                throw new RuntimeException("La cuenta no está activa");
            }
            versionActual = profRef.getSeguridadJwt() != null ? profRef.getSeguridadJwt().versionActual() : 0;
        } else {
            var estudiantesRefresh = estudianteRepository.findByEmailIgnoreCase(email);
            if (!estudiantesRefresh.isEmpty()) {
                Estudiante estRef = estudiantesRefresh.get(0);
                if (estRef.getEstadoUsuario() != EstadoUsuario.ACTIVO) {
                    throw new RuntimeException("La cuenta no está activa");
                }
                versionActual = estRef.getSeguridadJwt() != null ? estRef.getSeguridadJwt().versionActual() : 0;
            }
        }
        if (tokenVersionViejo != versionActual) {
            throw new RuntimeException("El token fue revocado tras actualización de seguridad");
        }

        //Buscamos al usuario en la base de datos
        var detallesUsuario = userDetailsService.loadUserByUsername(email);

        //Creados una credencial manual temporal para el usuario (Profesor o estudiante)
        Authentication authTemporal = new UsernamePasswordAuthenticationToken(
                detallesUsuario, null, detallesUsuario.getAuthorities());

        //generamos el nuevo token conservando el origen de la cadena de refresh
        long origenEfectivo = origIatMs != null ? origIatMs : System.currentTimeMillis();
        return jwtProveedor.generarToken(authTemporal, versionActual, origenEfectivo);

    }

}
