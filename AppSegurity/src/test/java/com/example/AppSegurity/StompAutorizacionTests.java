package com.example.AppSegurity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.example.AppSegurity.Enums.EstadoSesion;
import com.example.AppSegurity.Enums.Estado_pin;
import com.example.AppSegurity.Models.Estudiante;
import com.example.AppSegurity.Models.Examen;
import com.example.AppSegurity.Models.Profesor;
import com.example.AppSegurity.Models.SesionSupervision;
import com.example.AppSegurity.Repositorys.EstudianteRepository;
import com.example.AppSegurity.Repositorys.ExamenRepository;
import com.example.AppSegurity.Repositorys.ProfesorRepository;
import com.example.AppSegurity.Repositorys.SesionSupervisionRepository;
import com.example.AppSegurity.Security.JwtProveedor;
import com.example.AppSegurity.Sub_Clases.ControlAcceso;

/**
 * WebSocket/STOMP de punta a punta con clientes reales y tokens JWT reales: cada destino solo lo usa su dueño.
 * Antes cualquier usuario autenticado (incluido cualquier estudiante) podía ver el video de otra sesión y mandar comandos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"SEEDERS_ENABLED=false",
		"spring.mongodb.uri=mongodb://localhost:27017/app_supervision_test",
		"spring.data.mongodb.uri=mongodb://localhost:27017/app_supervision_test"})
class StompAutorizacionTests {

	@Value("${local.server.port}") private int puerto;
	@Autowired private JwtProveedor jwt;
	@Autowired private SimpMessagingTemplate servidor;
	@Autowired private ProfesorRepository profesorRepository;
	@Autowired private EstudianteRepository estudianteRepository;
	@Autowired private ExamenRepository examenRepository;
	@Autowired private SesionSupervisionRepository sesionRepository;

	private final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	private String profA, profB, emailA, emailB, estE1, estE2, emailE1, emailE2, examen, sesion;
	private final java.util.ArrayList<WebSocketStompClient> clientes = new java.util.ArrayList<>();

	@BeforeEach
	void datos() {
		profA = "SPA" + sufijo; profB = "SPB" + sufijo;
		emailA = "pa." + sufijo + "@test.co"; emailB = "pb." + sufijo + "@test.co";
		estE1 = "SE1" + sufijo; estE2 = "SE2" + sufijo;
		emailE1 = "se1." + sufijo + "@test.co"; emailE2 = "se2." + sufijo + "@test.co";
		examen = "SEX" + sufijo; sesion = "SSES" + sufijo;
		for (String[] p : new String[][] {{profA, emailA}, {profB, emailB}}) {
			Profesor pr = new Profesor();
			pr.setCodigoProfesor(p[0]); pr.setEmailInstitucional(p[1]); pr.setNombre("P"); pr.setApellidos("T");
			pr.setPasswordHash("x"); pr.setRol("PROFESOR");
			profesorRepository.save(pr);
		}
		for (String[] e : new String[][] {{estE1, emailE1}, {estE2, emailE2}}) {
			Estudiante es = new Estudiante();
			es.setEstudianteId(e[0]); es.setEmail(e[1]); es.setNombre("E"); es.setApellidos("T"); es.setPasswordHash("x");
			estudianteRepository.save(es);
		}
		Examen ex = new Examen();
		ex.setCodigoExamen(examen); ex.setProfesorId(profA); ex.setMateriaCodigo("M");
		ControlAcceso ca = new ControlAcceso(); ca.setPinSesion("P" + sufijo); ca.setEstadoPin(Estado_pin.ACTIVO);
		ex.setControlAcceso(ca);
		examenRepository.save(ex);
		SesionSupervision s = new SesionSupervision();
		s.setSesionId(sesion); s.setExamenId(examen); s.setEstudianteId(estE1); s.setEstadoSesion(EstadoSesion.INICIADA);
		sesionRepository.save(s);
	}

	@AfterEach
	void limpiar() {
		clientes.forEach(c -> { try { c.stop(); } catch (Exception ignored) {} });
		profesorRepository.deleteById(profA); profesorRepository.deleteById(profB);
		estudianteRepository.deleteById(estE1); estudianteRepository.deleteById(estE2);
		examenRepository.deleteById(examen); sesionRepository.deleteById(sesion);
	}

	// ───────── cliente STOMP de prueba ─────────
	private String token(String email, String rol) {
		var autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + rol));
		return jwt.generarToken(new UsernamePasswordAuthenticationToken(new User(email, "x", autoridades), null, autoridades));
	}

	/** Conexión de un usuario. Devuelve null si el servidor rechaza el apretón de manos o el CONNECT. */
	private Cliente conectar(String token) throws Exception {
		WebSocketStompClient cliente = new WebSocketStompClient(new StandardWebSocketClient());
		cliente.setMessageConverter(new StringMessageConverter());
		clientes.add(cliente);
		Cliente c = new Cliente();
		WebSocketHttpHeaders cabeceras = new WebSocketHttpHeaders();
		if (token != null) cabeceras.add("Authorization", "Bearer " + token);
		StompSessionHandlerAdapter manejador = new StompSessionHandlerAdapter() {
			@Override public void handleFrame(StompHeaders headers, Object payload) { c.error.set(true); }
			@Override public void handleTransportError(StompSession s, Throwable ex) { c.error.set(true); }
			@Override public void handleException(StompSession s, org.springframework.messaging.simp.stomp.StompCommand cmd, StompHeaders h, byte[] p, Throwable ex) { c.error.set(true); }
		};
		try {
			c.sesion = cliente.connectAsync("ws://localhost:" + puerto + "/ws-supervision", cabeceras, new StompHeaders(), manejador).get(6, TimeUnit.SECONDS);
		} catch (ExecutionException | java.util.concurrent.TimeoutException e) {
			return null;
		}
		return c;
	}

	private static class Cliente {
		StompSession sesion;
		final BlockingQueue<String> recibidos = new LinkedBlockingQueue<>();
		final AtomicBoolean error = new AtomicBoolean(false);

		void suscribir(String destino) {
			sesion.subscribe(destino, new StompFrameHandler() {
				@Override public Type getPayloadType(StompHeaders h) { return String.class; }
				@Override public void handleFrame(StompHeaders h, Object p) { recibidos.add(String.valueOf(p)); }
			});
		}
		boolean vivo() { return sesion != null && sesion.isConnected() && !error.get(); }
	}

	/** ¿Quedó suscrito? El servidor publica una sonda: si llega, la suscripción fue aceptada. */
	private boolean suscripcionAceptada(Cliente c, String destino) throws Exception {
		c.suscribir(destino);
		Thread.sleep(600);                                     // dar tiempo a que el servidor rechace (ERROR + cierre)
		if (!c.vivo()) return false;
		servidor.convertAndSend(destino, "sonda");
		return "sonda".equals(c.recibidos.poll(2, TimeUnit.SECONDS));
	}

	/** ¿Se aceptó lo que envió el cliente? Un docente dueño escucha el destino y comprueba que llega. */
	private boolean envioAceptado(Cliente emisor, String destino, Cliente oyente) throws Exception {
		String mensaje = "hola-" + UUID.randomUUID();
		emisor.sesion.send(destino, mensaje);
		String recibido = oyente.recibidos.poll(2, TimeUnit.SECONDS);
		return mensaje.equals(recibido);
	}

	// ───────── pruebas ─────────
	@Test
	void sinTokenNoSePuedeAbrirElWebSocket() throws Exception {
		assertEquals(null, conectar(null), "el servidor debe rechazar conexiones sin token");
		assertEquals(null, conectar("token.falso.invalido"), "ni con un token inválido");
	}

	@Test
	void soloElDocenteDuenoVeElVideoDeLaSesion() throws Exception {
		String destino = "/topic/stream/" + sesion;
		Cliente duenio = conectar(token(emailA, "PROFESOR"));
		assertNotNull(duenio);
		assertTrue(suscripcionAceptada(duenio, destino), "el docente dueño debe poder ver el video");

		Cliente otroDocente = conectar(token(emailB, "PROFESOR"));
		assertNotNull(otroDocente);
		assertFalse(suscripcionAceptada(otroDocente, destino), "otro docente NO debe ver el video");

		Cliente estudianteAjeno = conectar(token(emailE2, "ESTUDIANTE"));
		assertNotNull(estudianteAjeno);
		assertFalse(suscripcionAceptada(estudianteAjeno, destino), "un estudiante ajeno NO debe ver el video");

		Cliente estudianteDuenio = conectar(token(emailE1, "ESTUDIANTE"));
		assertNotNull(estudianteDuenio);
		assertFalse(suscripcionAceptada(estudianteDuenio, destino), "ni siquiera el estudiante se suscribe a su propio stream");
	}

	@Test
	void soloElDocenteDuenoVeLasAlertasDelExamen() throws Exception {
		String destino = "/topic/alertas/" + examen;
		assertTrue(suscripcionAceptada(conectar(token(emailA, "PROFESOR")), destino));
		assertFalse(suscripcionAceptada(conectar(token(emailB, "PROFESOR")), destino), "otro docente");
		assertFalse(suscripcionAceptada(conectar(token(emailE1, "ESTUDIANTE")), destino), "un estudiante");
	}

	@Test
	void soloElEstudianteDuenoYSuDocenteOyenLosComandos() throws Exception {
		String destino = "/topic/comandos/" + sesion;
		assertTrue(suscripcionAceptada(conectar(token(emailE1, "ESTUDIANTE")), destino), "el estudiante dueño");
		assertTrue(suscripcionAceptada(conectar(token(emailA, "PROFESOR")), destino), "el docente dueño");
		assertFalse(suscripcionAceptada(conectar(token(emailE2, "ESTUDIANTE")), destino), "un estudiante ajeno");
		assertFalse(suscripcionAceptada(conectar(token(emailB, "PROFESOR")), destino), "otro docente");
	}

	@Test
	void soloElEstudianteDuenoPublicaSuVideo() throws Exception {
		String destino = "/topic/stream/" + sesion;
		Cliente docente = conectar(token(emailA, "PROFESOR"));
		assertTrue(suscripcionAceptada(docente, destino));
		docente.recibidos.clear();

		Cliente duenio = conectar(token(emailE1, "ESTUDIANTE"));
		assertTrue(envioAceptado(duenio, destino, docente), "el estudiante dueño debe poder enviar su video");

		Cliente intruso = conectar(token(emailE2, "ESTUDIANTE"));
		assertFalse(envioAceptado(intruso, destino, docente), "otro estudiante NO debe poder inyectar video");
		assertFalse(intruso.vivo(), "el servidor corta la conexión que intenta algo no permitido");

		Cliente docenteAjeno = conectar(token(emailB, "PROFESOR"));
		assertFalse(envioAceptado(docenteAjeno, destino, docente), "un docente no publica video");
	}

	@Test
	void soloElDocenteDuenoEnviaComandosYEnEspecialElDeFraude() throws Exception {
		String destino = "/topic/comandos/" + sesion;
		Cliente estudiante = conectar(token(emailE1, "ESTUDIANTE"));
		assertTrue(suscripcionAceptada(estudiante, destino));
		estudiante.recibidos.clear();

		assertTrue(envioAceptado(conectar(token(emailA, "PROFESOR")), destino, estudiante), "el docente dueño envía comandos");
		assertFalse(envioAceptado(conectar(token(emailB, "PROFESOR")), destino, estudiante), "otro docente no");
		assertFalse(envioAceptado(conectar(token(emailE2, "ESTUDIANTE")), destino, estudiante), "un estudiante ajeno no");
		Cliente propio = conectar(token(emailE1, "ESTUDIANTE"));
		assertFalse(envioAceptado(propio, destino, estudiante), "ni el propio estudiante se manda comandos");

		// El comando FRAUDE anula la sesión: un estudiante no puede mandarlo por la ruta de aplicación
		Cliente atacante = conectar(token(emailE2, "ESTUDIANTE"));
		atacante.sesion.send("/app/comando/" + sesion, "{\"comando\":\"FRAUDE\"}");
		Thread.sleep(800);
		assertEquals(EstadoSesion.INICIADA, sesionRepository.findById(sesion).get().getEstadoSesion(),
				"un estudiante ajeno NO debe poder anular la sesión de otro");
	}

	@Test
	void losDestinosDesconocidosSeRechazan() throws Exception {
		Cliente c = conectar(token(emailA, "PROFESOR"));
		assertFalse(suscripcionAceptada(c, "/topic/cualquier-cosa"), "destino no permitido");
		Cliente d = conectar(token(emailA, "PROFESOR"));
		assertFalse(suscripcionAceptada(d, "/topic/stream/NO_EXISTE_" + sufijo), "stream de una sesión inexistente");
		Cliente e = conectar(token(emailA, "PROFESOR"));
		assertFalse(suscripcionAceptada(e, "/topic/alertas/NO_EXISTE_" + sufijo), "alertas de un examen inexistente");
	}
}
