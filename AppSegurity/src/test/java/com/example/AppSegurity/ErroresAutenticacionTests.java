package com.example.AppSegurity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.AppSegurity.Security.IntentosExcedidosException;
import com.example.AppSegurity.Services.AutenticacionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Los errores de autenticación no deben revelar detalles internos (nombres de usuario de la base de datos, clases, rutas…):
 * eso queda en el log del servidor. Los mensajes pensados para el usuario (validaciones 400/409 y el bloqueo 429 con los
 * minutos) se conservan tal cual, porque las apps los muestran.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"SEEDERS_ENABLED=false",
		"spring.mongodb.uri=mongodb://localhost:27017/app_supervision_test",
		"spring.data.mongodb.uri=mongodb://localhost:27017/app_supervision_test"})
@AutoConfigureMockMvc
class ErroresAutenticacionTests {

	private static final String SECRETO = "MongoCredential{userName='SECRETO_INTERNO', source='admin'}";

	@Autowired private MockMvc mockMvc;
	@MockitoBean private AutenticacionService servicio;
	private final ObjectMapper json = new ObjectMapper();

	private MvcResult llamar(String ruta, String cuerpo) throws Exception {
		return mockMvc.perform(post("/api/auth/" + ruta).contentType("application/json").content(cuerpo)).andReturn();
	}

	private static final String LOGIN = "{\"email\":\"a@test.co\",\"password\":\"x\"}";

	// ───────── login ─────────
	@Test
	void unFalloInternoEnElLoginNoSeFiltraAlCliente() throws Exception {
		when(servicio.loginProfesor(anyString(), anyString())).thenThrow(new RuntimeException(SECRETO));
		when(servicio.loginEstudiante(anyString(), anyString())).thenThrow(new RuntimeException(SECRETO));
		for (String ruta : new String[] {"login/profesor", "login/estudiante"}) {
			MvcResult r = llamar(ruta, LOGIN);
			String cuerpo = r.getResponse().getContentAsString();
			assertEquals(401, r.getResponse().getStatus(), ruta);
			assertFalse(cuerpo.contains("SECRETO_INTERNO") || cuerpo.contains("MongoCredential"), ruta + " filtró: " + cuerpo);
			assertTrue(cuerpo.contains("Credenciales incorrectas"), ruta + ": " + cuerpo);
		}
	}

	@Test
	void unaCuentaInactivaTampocoSeDistingueDeUnaClaveMala() throws Exception {
		when(servicio.loginProfesor(anyString(), anyString())).thenThrow(new RuntimeException("Solo los profesores activos pueden iniciar sesión"));
		String cuerpo = llamar("login/profesor", LOGIN).getResponse().getContentAsString();
		assertFalse(cuerpo.contains("activos"), cuerpo);
		assertTrue(cuerpo.contains("Credenciales incorrectas"), cuerpo);
	}

	@Test
	void elBloqueoPorIntentosConservaSuMensajeConLosMinutos() throws Exception {
		String aviso = "Demasiados intentos fallidos. Intenta de nuevo en 14 minuto(s).";
		when(servicio.loginProfesor(anyString(), anyString())).thenThrow(new IntentosExcedidosException(aviso, 840_000L));
		when(servicio.loginEstudiante(anyString(), anyString())).thenThrow(new IntentosExcedidosException(aviso, 840_000L));
		for (String ruta : new String[] {"login/profesor", "login/estudiante"}) {
			MvcResult r = llamar(ruta, LOGIN);
			assertEquals(429, r.getResponse().getStatus(), ruta);
			assertTrue(r.getResponse().getContentAsString().contains("14 minuto"), r.getResponse().getContentAsString());
		}
	}

	@Test
	void unLoginCorrectoSigueDevolviendoElToken() throws Exception {
		when(servicio.loginProfesor(anyString(), anyString())).thenReturn("token-de-prueba");
		MvcResult r = llamar("login/profesor", LOGIN);
		assertEquals(200, r.getResponse().getStatus());
		assertEquals("token-de-prueba", json.readTree(r.getResponse().getContentAsString()).get("token").asText());
	}

	// ───────── registro y recuperación de clave ─────────
	private interface Preparar { void accion(AutenticacionService s, Throwable t); }

	private void revisarEndpoint(String ruta, Preparar preparar) throws Exception {
		// 1. Fallo interno: mensaje genérico, sin el detalle
		preparar.accion(servicio, new RuntimeException(SECRETO));
		MvcResult interno = llamar(ruta, "{}");
		String cuerpo = interno.getResponse().getContentAsString();
		assertEquals(500, interno.getResponse().getStatus(), ruta);
		assertFalse(cuerpo.contains("SECRETO_INTERNO") || cuerpo.contains("MongoCredential"), ruta + " filtró: " + cuerpo);
		JsonNode j = json.readTree(cuerpo);
		assertFalse(j.get("exito").asBoolean(), ruta);
		assertFalse(j.get("mensaje").asText().isBlank(), ruta);

		// 2. Validación pensada para el usuario: se conserva el texto
		preparar.accion(servicio, new IllegalArgumentException("Cédula no registrada en la lista institucional"));
		MvcResult validacion = llamar(ruta, "{}");
		assertEquals(400, validacion.getResponse().getStatus(), ruta);
		assertTrue(validacion.getResponse().getContentAsString().contains("Cédula no registrada"), ruta);

		// 3. Bloqueo por intentos: se conserva el texto y el 429
		preparar.accion(servicio, new IntentosExcedidosException("Demasiados intentos. Espera 9 minuto(s).", 540_000L));
		MvcResult bloqueo = llamar(ruta, "{}");
		assertEquals(429, bloqueo.getResponse().getStatus(), ruta);
		assertTrue(bloqueo.getResponse().getContentAsString().contains("9 minuto"), ruta);
	}

	@Test
	void solicitarCodigoDeRegistro() throws Exception {
		revisarEndpoint("registro/solicitar-codigo", (s, t) -> doThrow(t).when(s).solicitarCodigoRegistro(any()));
	}

	@Test
	void confirmarRegistroDeEstudiante() throws Exception {
		revisarEndpoint("registro/confirmar-estudiante", (s, t) -> doThrow(t).when(s).confirmarRegistroEstudiante(any()));
	}

	@Test
	void confirmarRegistroDeProfesor() throws Exception {
		revisarEndpoint("registro/confirmar-profesor", (s, t) -> doThrow(t).when(s).confirmarRegistroProfesor(any()));
	}

	@Test
	void solicitarCodigoDeRecuperacion() throws Exception {
		revisarEndpoint("recuperar/solicitar-codigo", (s, t) -> doThrow(t).when(s).solicitarCodigoRecuperacion(any()));
	}

	@Test
	void confirmarRecuperacionDeClave() throws Exception {
		revisarEndpoint("recuperar/confirmar", (s, t) -> doThrow(t).when(s).confirmarRecuperacionPassword(any()));
	}

	@Test
	void unEstadoIlegalSigueConservandoSuCodigoYSuMensaje() throws Exception {
		doThrow(new IllegalStateException("Ya existe una cuenta con ese correo")).when(servicio).solicitarCodigoRegistro(any());
		MvcResult r = llamar("registro/solicitar-codigo", "{}");
		assertEquals(409, r.getResponse().getStatus());
		assertTrue(r.getResponse().getContentAsString().contains("Ya existe una cuenta"));
	}
}
