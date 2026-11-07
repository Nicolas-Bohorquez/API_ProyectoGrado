package com.example.AppSegurity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

// Puerto aleatorio: WebSocketConfig necesita un contenedor de servlets real
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"SEEDERS_ENABLED=false",
		"spring.mongodb.uri=mongodb://localhost:27017/app_supervision_test",
		"spring.data.mongodb.uri=mongodb://localhost:27017/app_supervision_test"})
@AutoConfigureMockMvc
class SeguridadRutasTests {

	@Autowired
	private MockMvc mockMvc;

	private int estado(org.springframework.test.web.servlet.RequestBuilder peticion) throws Exception {
		return mockMvc.perform(peticion).andReturn().getResponse().getStatus();
	}

	@Test
	void sinTokenNoSePuedeAccederALasRutasProtegidas() throws Exception {
		int estado = estado(post("/api/examenes/EXM-1/abrir"));
		assertTrue(estado == 401 || estado == 403, "Sin autenticar debe rechazarse, fue " + estado);
	}

	@Test
	@WithMockUser(roles = "ESTUDIANTE")
	void estudianteNoPuedeUsarRutasDeDocente() throws Exception {
		assertEquals(403, estado(post("/api/examenes/EXM-1/abrir")));
		assertEquals(403, estado(post("/api/examenes/EXM-1/cerrar")));
		assertEquals(403, estado(get("/api/supervision/examen/EXM-1/alertas")));
		assertEquals(403, estado(get("/api/supervision/evidencia/descargar").param("ruta", "x.png")));
		assertEquals(403, estado(put("/api/apelaciones/S1/resolver").param("resolucionComite", "x").param("estadoApelacion", "APROBADA")));
	}

	@Test
	@WithMockUser(roles = "ESTUDIANTE")
	void estudiantePuedeUsarLasRutasDelExamen() throws Exception {
		// El servicio puede responder 4xx por no existir la sesión, pero nunca 401/403 por rol
		assertNotEquals(403, estado(post("/api/supervision/S1/alerta").contentType("application/json").content("{}")));
		// heartbeat responde 403 por sí mismo cuando la sesión no existe: se distingue por el cuerpo, que Spring Security deja vacío
		String cuerpoHeartbeat = mockMvc.perform(post("/api/supervision/S1/heartbeat")).andReturn().getResponse().getContentAsString();
		assertTrue(cuerpoHeartbeat.contains("Error en heartbeat"), "El heartbeat debe llegar al controlador, cuerpo: " + cuerpoHeartbeat);
		assertNotEquals(403, estado(post("/api/supervision/S1/finalizar")));
		assertNotEquals(403, estado(get("/api/supervision/reglas/S1")));
		assertNotEquals(403, estado(post("/api/apelaciones/S1/solicitar")));
	}

	@Test
	@WithMockUser(roles = "PROFESOR")
	void docentePuedeUsarRutasDeDocente() throws Exception {
		assertNotEquals(403, estado(get("/api/supervision/examen/EXM-1/sesiones")));
		assertNotEquals(403, estado(post("/api/examenes/EXM-1/abrir")));
	}

	@Test
	@WithMockUser(roles = "PROFESOR")
	void descargarEvidenciaBloqueaPathTraversal() throws Exception {
		assertEquals(403, estado(get("/api/supervision/evidencia/descargar").param("ruta", "../../../../Windows/win.ini")));
		assertEquals(403, estado(get("/api/supervision/evidencia/descargar").param("ruta", "C:/Windows/win.ini")));
		// Una ruta que no corresponde a ninguna sesión de sus exámenes ya no se distingue de "no es suyo": 403, no 404
		assertEquals(403, estado(get("/api/supervision/evidencia/descargar").param("ruta", "no_existe/foto.png")));
	}
}
