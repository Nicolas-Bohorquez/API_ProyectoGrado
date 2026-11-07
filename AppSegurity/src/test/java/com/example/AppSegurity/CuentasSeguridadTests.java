package com.example.AppSegurity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.AppSegurity.Enums.EstadoUsuario;
import com.example.AppSegurity.Enums.Estado_pin;
import com.example.AppSegurity.Models.Estudiante;
import com.example.AppSegurity.Models.Examen;
import com.example.AppSegurity.Models.Profesor;
import com.example.AppSegurity.Repositorys.EstudianteRepository;
import com.example.AppSegurity.Repositorys.ExamenRepository;
import com.example.AppSegurity.Repositorys.ProfesorRepository;
import com.example.AppSegurity.Security.JwtProveedor;
import com.example.AppSegurity.Security.LimitadorIntentos;
import com.example.AppSegurity.Services.EmailService;
import com.example.AppSegurity.Sub_Clases.ControlAcceso;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Contraseñas, OTP, límite de intentos, refresh de token e inscripción masiva de estudiantes (base de pruebas). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"SEEDERS_ENABLED=false",
		"spring.mongodb.uri=mongodb://localhost:27017/app_supervision_test",
		"spring.data.mongodb.uri=mongodb://localhost:27017/app_supervision_test"})
@AutoConfigureMockMvc
class CuentasSeguridadTests {

	@Autowired private MockMvc mockMvc;
	private final ObjectMapper json = new ObjectMapper();
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private LimitadorIntentos limitador;
	@Autowired private JwtProveedor jwtProveedor;
	@Autowired private EstudianteRepository estudianteRepository;
	@Autowired private ProfesorRepository profesorRepository;
	@Autowired private ExamenRepository examenRepository;
	@MockitoBean private EmailService emailService;           // nunca se envían correos reales

	private final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	private String emailEst, estId, emailProf, profId, examen;
	private final List<String> estudiantesCreados = new ArrayList<>();
	private final List<String> correosLimitador = new ArrayList<>();
	private String ultimaRespuesta = "";

	@BeforeEach
	void datos() {
		emailEst = "est." + sufijo + "@test.co"; estId = "TE" + sufijo;
		emailProf = "prof." + sufijo + "@test.co"; profId = "TP" + sufijo; examen = "TX" + sufijo;
		Estudiante e = new Estudiante();
		e.setEstudianteId(estId); e.setEmail(emailEst); e.setNombre("Ana"); e.setApellidos("Prueba");
		e.setPasswordHash(passwordEncoder.encode("ClaveBuena123")); e.setEstadoUsuario(EstadoUsuario.ACTIVO);
		estudianteRepository.save(e);
		Profesor p = new Profesor();
		p.setCodigoProfesor(profId); p.setEmailInstitucional(emailProf); p.setNombre("Pro"); p.setApellidos("Fe");
		p.setPasswordHash(passwordEncoder.encode("ClaveProf123")); p.setEstado(EstadoUsuario.ACTIVO); p.setRol("PROFESOR");
		profesorRepository.save(p);
		Examen x = new Examen();
		x.setCodigoExamen(examen); x.setProfesorId(profId); x.setMateriaCodigo("MAT" + sufijo);
		ControlAcceso ca = new ControlAcceso(); ca.setPinSesion("PIN" + sufijo); ca.setEstadoPin(Estado_pin.FINALIZADO);
		x.setControlAcceso(ca);
		examenRepository.save(x);
		correosLimitador.addAll(List.of(emailEst, emailProf));
	}

	@AfterEach
	void limpiar() {
		estudianteRepository.deleteById(estId); profesorRepository.deleteById(profId); examenRepository.deleteById(examen);
		estudiantesCreados.forEach(estudianteRepository::deleteById);
		for (String c : correosLimitador) { limitador.registrarExito("login:" + c); limitador.registrarExito("otp:" + c); limitador.registrarExito("cambio:" + c); }
	}

	private MvcResult login(String ruta, String email, String clave) throws Exception {
		return mockMvc.perform(post("/api/auth/login/" + ruta).contentType("application/json")
				.content(json.writeValueAsString(Map.of("email", email, "password", clave)))).andReturn();
	}

	// ───────── límite de intentos de login ─────────
	@Test
	void tras5ClavesErroneasElCorreoQuedaBloqueadoAunqueLaClaveSeaLaCorrecta() throws Exception {
		for (int i = 0; i < 5; i++) {
			assertEquals(401, login("estudiante", emailEst, "mala" + i).getResponse().getStatus());
		}
		MvcResult bloqueado = login("estudiante", emailEst, "ClaveBuena123");
		assertEquals(429, bloqueado.getResponse().getStatus(), "debe responder 429 (demasiados intentos)");
		assertTrue(bloqueado.getResponse().getContentAsString().contains("minuto"), bloqueado.getResponse().getContentAsString());
		// otro usuario no se ve afectado
		assertEquals(200, login("profesor", emailProf, "ClaveProf123").getResponse().getStatus());
	}

	@Test
	void unLoginCorrectoNoSumaFallosYBorraLosAnteriores() throws Exception {
		for (int i = 0; i < 3; i++) login("profesor", emailProf, "mala");
		assertEquals(200, login("profesor", emailProf, "ClaveProf123").getResponse().getStatus());
		for (int i = 0; i < 4; i++) assertEquals(401, login("profesor", emailProf, "mala").getResponse().getStatus());
		assertEquals(200, login("profesor", emailProf, "ClaveProf123").getResponse().getStatus(), "el contador volvió a cero tras el acierto");
	}

	// ───────── OTP ─────────
	private String solicitarCodigo(String email, String cedula) throws Exception {
		MvcResult r = mockMvc.perform(post("/api/auth/registro/solicitar-codigo").contentType("application/json")
				.content(json.writeValueAsString(Map.of("email", email, "cedula", cedula, "rol", "ESTUDIANTE", "nombre", "Nuevo")))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
		verify(emailService, atLeastOnce()).enviarCodigoVerificacion(org.mockito.ArgumentMatchers.eq(email), anyString(), codigo.capture(), anyString());
		return codigo.getValue();
	}

	private int confirmar(String email, String cedula, String codigo) throws Exception {
		MvcResult r = mockMvc.perform(post("/api/auth/registro/confirmar-estudiante").contentType("application/json")
				.content(json.writeValueAsString(Map.of("email", email, "codigoOtp", codigo, "nombre", "Nuevo", "apellidos", "Usuario",
						"cedula", cedula, "password", "OtraClave123")))).andReturn();
		ultimaRespuesta = r.getResponse().getContentAsString();
		return r.getResponse().getStatus();
	}

	@Test
	void elCodigoOtpTiene6DigitosYNoSePuedeAdivinarPorFuerzaBruta() throws Exception {
		String email = "otp." + sufijo + "@gmail.com";
		correosLimitador.add(email);
		String cedula = "9" + Math.abs(sufijo.hashCode() % 100000000);
		String correcto = solicitarCodigo(email, cedula);
		assertTrue(correcto.matches("\\d{6}"), "OTP de 6 dígitos: " + correcto);

		String incorrecto = correcto.equals("000000") ? "000001" : "000000";
		List<Integer> estados = new ArrayList<>();
		for (int i = 0; i < 7; i++) estados.add(confirmar(email, cedula, incorrecto));
		// 5 intentos erróneos (400); el quinto invalida el código y bloquea el correo; después nada vuelve a pasar por la validación
		assertEquals(List.of(400, 400, 400, 400, 400), estados.subList(0, 5), "estados: " + estados);
		// el sexto intento ya no llega a comparar: el código se invalidó (o el correo está bloqueado)
		assertTrue(ultimaRespuesta.contains("No hay una solicitud de código pendiente") || ultimaRespuesta.contains("Demasiados intentos"),
				"tras 5 errores el código debe quedar invalidado: " + ultimaRespuesta);
		// aunque ahora se acierte, el código ya se invalidó/bloqueó: no se puede registrar
		assertNotEquals(200, confirmar(email, cedula, correcto), "con el límite superado ni el código correcto sirve");
		assertTrue(estudianteRepository.findByEmailIgnoreCase(email).isEmpty(), "no debe haberse creado la cuenta");
	}

	@Test
	void unOtpCorrectoSiPermiteRegistrarse() throws Exception {
		String email = "ok." + sufijo + "@gmail.com";
		correosLimitador.add(email);
		String cedula = "8" + Math.abs(sufijo.hashCode() % 100000000);
		String codigo = solicitarCodigo(email, cedula);
		assertEquals(200, confirmar(email, cedula, codigo));
		estudianteRepository.findByEmailIgnoreCase(email).forEach(e -> estudiantesCreados.add(e.getEstudianteId()));
		assertFalse(estudiantesCreados.isEmpty(), "la cuenta se creó");
	}

	@Test
	void noSePuedenPedirCodigosSeguidosAlMismoCorreo() throws Exception {
		String email = "spam." + sufijo + "@gmail.com";
		correosLimitador.add(email);
		String cedula = "7" + Math.abs(sufijo.hashCode() % 100000000);
		solicitarCodigo(email, cedula);
		MvcResult segundo = mockMvc.perform(post("/api/auth/registro/solicitar-codigo").contentType("application/json")
				.content(json.writeValueAsString(Map.of("email", email, "cedula", cedula, "rol", "ESTUDIANTE", "nombre", "Nuevo")))).andReturn();
		assertEquals(409, segundo.getResponse().getStatus(), "el segundo código seguido debe esperar");
		assertTrue(segundo.getResponse().getContentAsString().contains("minuto"));
	}

	// ───────── inscripción masiva: claves aleatorias, no "123456" ─────────
	@Test
	void laInscripcionMasivaCreaClavesTemporalesAleatoriasYDistintas() throws Exception {
		String c1 = "6" + Math.abs((sufijo + "a").hashCode() % 100000000), c2 = "5" + Math.abs((sufijo + "b").hashCode() % 100000000);
		String cuerpo = json.writeValueAsString(List.of(
				Map.of("cedula", c1, "nombre", "Uno", "apellidos", "Nuevo", "email", "uno." + sufijo + "@test.co"),
				Map.of("cedula", c2, "nombre", "Dos", "apellidos", "Nuevo", "email", "dos." + sufijo + "@test.co")));
		estudiantesCreados.add("EST-" + c1); estudiantesCreados.add("EST-" + c2);
		MvcResult r = mockMvc.perform(post("/api/examenes/" + examen + "/estudiantes/bulk").with(user(emailProf).roles("PROFESOR"))
				.contentType("application/json").content(cuerpo)).andReturn();
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		JsonNode creds = json.readTree(r.getResponse().getContentAsString()).get("credenciales");
		assertEquals(2, creds.size());
		Set<String> claves = new HashSet<>();
		for (JsonNode c : creds) {
			String clave = c.get("claveTemporal").asText();
			claves.add(clave);
			assertEquals(10, clave.length());
			assertNotEquals("123456", clave);
			Estudiante guardado = estudianteRepository.findById(c.get("estudianteId").asText()).orElseThrow();
			assertTrue(passwordEncoder.matches(clave, guardado.getPasswordHash()), "la clave entregada es la que quedó guardada");
			assertFalse(passwordEncoder.matches("123456", guardado.getPasswordHash()), "ya no queda la clave por defecto conocida");
			assertTrue(guardado.isClaveTemporal());
		}
		assertEquals(2, claves.size(), "cada estudiante recibe una clave distinta");

		// Reenviar la misma lista no vuelve a crear cuentas ni a entregar claves
		MvcResult otra = mockMvc.perform(post("/api/examenes/" + examen + "/estudiantes/bulk").with(user(emailProf).roles("PROFESOR"))
				.contentType("application/json").content(cuerpo)).andReturn();
		assertEquals(0, json.readTree(otra.getResponse().getContentAsString()).get("credenciales").size());
	}

	@Test
	void elPrimerIngresoConClaveTemporalObligaACambiarlaYLaViejaDejaDeServir() throws Exception {
		Estudiante e = estudianteRepository.findById(estId).orElseThrow();
		e.setPasswordHash(passwordEncoder.encode("Temporal123")); e.setClaveTemporal(true);
		estudianteRepository.save(e);

		JsonNode resp = json.readTree(login("estudiante", emailEst, "Temporal123").getResponse().getContentAsString());
		assertEquals("true", resp.get("claveTemporal").asText(), "el login avisa que debe cambiar la clave");
		assertNotNull(resp.get("token"));

		var auth = user(emailEst).roles("ESTUDIANTE");
		java.util.function.BiFunction<String, String, MvcResult> cambiar = (actual, nueva) -> {
			try {
				return mockMvc.perform(post("/api/estudiante/cambiar-clave").with(auth).contentType("application/json")
						.content(json.writeValueAsString(Map.of("claveActual", actual, "claveNueva", nueva)))).andReturn();
			} catch (Exception ex) { throw new RuntimeException(ex); }
		};
		assertEquals(400, cambiar.apply("equivocada", "NuevaClave123").getResponse().getStatus(), "clave actual incorrecta");
		assertEquals(400, cambiar.apply("Temporal123", "corta1").getResponse().getStatus(), "muy corta");
		assertEquals(400, cambiar.apply("Temporal123", "sololetrasss").getResponse().getStatus(), "sin números");
		assertEquals(400, cambiar.apply("Temporal123", "12345678").getResponse().getStatus(), "trivial");
		assertEquals(400, cambiar.apply("Temporal123", "Temporal123").getResponse().getStatus(), "igual a la actual");
		assertEquals(200, cambiar.apply("Temporal123", "MiNuevaClave2026").getResponse().getStatus());

		assertEquals(401, login("estudiante", emailEst, "Temporal123").getResponse().getStatus(), "la temporal ya no sirve");
		JsonNode nuevo = json.readTree(login("estudiante", emailEst, "MiNuevaClave2026").getResponse().getContentAsString());
		assertEquals("false", nuevo.get("claveTemporal").asText());
	}

	@Test
	void cambiarClaveExigeEstarAutenticadoComoEstudiante() throws Exception {
		int sinToken = mockMvc.perform(post("/api/estudiante/cambiar-clave").contentType("application/json").content("{}")).andReturn().getResponse().getStatus();
		assertTrue(sinToken == 401 || sinToken == 403, "sin token: " + sinToken);
		assertEquals(403, mockMvc.perform(post("/api/estudiante/cambiar-clave").with(user(emailProf).roles("PROFESOR"))
				.contentType("application/json").content("{}")).andReturn().getResponse().getStatus(), "un docente no usa esta ruta");
	}

	// ───────── refresh de token ─────────
	private String tokenExpiradoHace(String email, long horas) {
		String secreto = (String) ReflectionTestUtils.getField(jwtProveedor, "JWT_SECRET");
		Date emision = new Date(System.currentTimeMillis() - (horas + 1) * 3600_000L);
		Date expira = new Date(System.currentTimeMillis() - horas * 3600_000L);
		return io.jsonwebtoken.Jwts.builder().setSubject(email).setIssuedAt(emision).setExpiration(expira)
				.signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(secreto.getBytes()), io.jsonwebtoken.SignatureAlgorithm.HS256).compact();
	}

	private int refrescar(String token) throws Exception {
		return mockMvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
	}

	@Test
	void unTokenExpiradoRecienteSeRefrescaPeroUnoViejoNo() throws Exception {
		assertEquals(200, refrescar(tokenExpiradoHace(emailProf, 1)), "expiró hace 1 h: sesión continua, se renueva");
		assertEquals(403, refrescar(tokenExpiradoHace(emailProf, 24)), "expiró hace 24 h: debe iniciar sesión de nuevo");
		assertEquals(403, refrescar(tokenExpiradoHace(emailProf, 24 * 90)), "un token robado de hace meses no revive");
	}

	@Test
	void unaCuentaSuspendidaNoPuedeSeguirRenovandoSuToken() throws Exception {
		String token = tokenExpiradoHace(emailProf, 1);
		Profesor p = profesorRepository.findById(profId).orElseThrow();
		p.setEstado(EstadoUsuario.SUSPENDIDO); profesorRepository.save(p);
		assertEquals(403, refrescar(token));
		Estudiante e = estudianteRepository.findById(estId).orElseThrow();
		e.setEstadoUsuario(EstadoUsuario.INACTIVO); estudianteRepository.save(e);
		assertEquals(403, refrescar(tokenExpiradoHace(emailEst, 1)));
	}

	@Test
	void unTokenConFirmaFalsaNuncaSeRefresca() throws Exception {
		String falso = io.jsonwebtoken.Jwts.builder().setSubject(emailProf).setExpiration(new Date(System.currentTimeMillis() - 1000))
				.signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor("OtraLlaveTotalmenteDistinta_de_32_o_mas_caracteres!!".getBytes()),
						io.jsonwebtoken.SignatureAlgorithm.HS256).compact();
		assertEquals(403, refrescar(falso));
	}

	// ───────── configuración segura por defecto ─────────
	@Test
	void losSeedersConClavesConocidasEstanApagadosPorDefecto() throws Exception {
		String propiedades = java.nio.file.Files.readString(java.nio.file.Paths.get("src/main/resources/application.properties"));
		assertTrue(propiedades.contains("app.seeders.enabled=${SEEDERS_ENABLED:false}"), "por defecto deben estar apagados");
	}

	@Test
	void elHashDeLaClaveNuncaSaleEnElJson() throws Exception {
		Estudiante e = estudianteRepository.findById(estId).orElseThrow();
		String salida = json.writeValueAsString(e);
		assertFalse(salida.contains("passwordHash"), salida);
		assertFalse(salida.contains("$argon2") || salida.contains("$2a$"), "el hash no debe aparecer en la respuesta");
		Profesor p = profesorRepository.findById(profId).orElseThrow();
		assertFalse(json.writeValueAsString(p).contains("passwordHash"));
		// y con el serializador REAL de la aplicación: el listado de estudiantes del administrador
		MvcResult lista = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/estudiantes")
				.with(user("admin." + sufijo + "@test.co").roles("PROFESOR", "ADMIN", "SUPERADMIN"))).andReturn();
		assertEquals(200, lista.getResponse().getStatus());
		String cuerpo = lista.getResponse().getContentAsString();
		assertTrue(cuerpo.contains(estId), "el listado debe incluir al estudiante de prueba");
		assertFalse(cuerpo.contains("passwordHash") || cuerpo.contains("$argon2") || cuerpo.contains("$2a$"), "el listado expuso un hash de clave");
	}
}
