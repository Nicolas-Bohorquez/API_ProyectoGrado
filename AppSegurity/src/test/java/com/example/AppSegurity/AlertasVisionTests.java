package com.example.AppSegurity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.AppSegurity.Enums.ClaseAlerta;
import com.example.AppSegurity.Enums.NivelRiesgo;
import com.example.AppSegurity.Enums.TipoEvidenciaVision;
import com.example.AppSegurity.Models.Alerta.AlertaVision;
import com.example.AppSegurity.Models.AlertaEvidencia;
import com.example.AppSegurity.Models.Examen;
import com.example.AppSegurity.Models.SesionSupervision;
import com.example.AppSegurity.Repositorys.AlertaEvidenciaRepository;
import com.example.AppSegurity.Repositorys.ExamenRepository;
import com.example.AppSegurity.Repositorys.SesionSupervisionRepository;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato cliente <-> backend para las alertas del motor IA.
 * Usa una base de datos SEPARADA (app_supervision_test): nunca toca los datos de desarrollo.
 * alertas_cliente.json lo genera la app del estudiante (AppSupervision/pruebas/generar_fixture_backend.py).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"SEEDERS_ENABLED=false",
		"spring.mongodb.uri=mongodb://localhost:27017/app_supervision_test",
		"spring.data.mongodb.uri=mongodb://localhost:27017/app_supervision_test"})
@AutoConfigureMockMvc
class AlertasVisionTests {

	private static final String SESION = "SES-ALERTAS-TEST";
	private static final String EXAMEN = "EXM-ALERTAS-TEST";
	// Las alertas solo las puede enviar el estudiante DUEÑO de la sesión: el usuario autenticado es ese estudiante
	private static final String CORREO = "alertas.test@test.co";
	private static final String ESTUDIANTE = "EST-TEST";
	private static final String CORREO_DOCENTE = "docente.alertas@test.co";
	private static final String DOCENTE = "PROF-ALERTAS-TEST";

	@Autowired private MockMvc mvc;
	@Autowired private ExamenRepository examenes;
	@Autowired private SesionSupervisionRepository sesiones;
	@Autowired private AlertaEvidenciaRepository alertas;
	@Autowired private com.example.AppSegurity.Repositorys.EstudianteRepository estudiantes;
	@Autowired private com.example.AppSegurity.Repositorys.ProfesorRepository profesores;

	@BeforeEach
	void preparar() {
		limpiar();
		Examen examen = new Examen();
		examen.setCodigoExamen(EXAMEN);
		examen.setProfesorId(DOCENTE);
		examenes.save(examen);
		com.example.AppSegurity.Models.Profesor docente = new com.example.AppSegurity.Models.Profesor();
		docente.setCodigoProfesor(DOCENTE);
		docente.setEmailInstitucional(CORREO_DOCENTE);
		docente.setPasswordHash("x");
		profesores.save(docente);
		SesionSupervision sesion = new SesionSupervision();
		sesion.setSesionId(SESION);
		sesion.setExamenId(EXAMEN);
		sesion.setEstudianteId(ESTUDIANTE);
		sesiones.save(sesion);
		com.example.AppSegurity.Models.Estudiante dueno = new com.example.AppSegurity.Models.Estudiante();
		dueno.setEstudianteId(ESTUDIANTE);
		dueno.setEmail(CORREO);
		dueno.setPasswordHash("x");
		estudiantes.save(dueno);
	}

	@AfterEach
	void limpiar() {
		alertas.deleteAll(alertas.findBySesionIdOrderByHoraCapturaAsc(SESION));
		sesiones.deleteById(SESION);
		examenes.deleteById(EXAMEN);
		estudiantes.deleteById(ESTUDIANTE);
		profesores.deleteById(DOCENTE);
	}

	private int enviar(String json) throws Exception {
		return mvc.perform(post("/api/supervision/" + SESION + "/alerta").contentType("application/json").content(json))
				.andReturn().getResponse().getStatus();
	}

	private String cuerpoDe(String json) throws Exception {
		return mvc.perform(post("/api/supervision/" + SESION + "/alerta").contentType("application/json").content(json))
				.andReturn().getResponse().getContentAsString();
	}

	@Test
	@WithMockUser(username = CORREO, roles = "ESTUDIANTE")
	void todasLasAlertasQueArmaLaAppDelEstudianteSeGuardan() throws Exception {
		JsonNode cargas;
		try (InputStream in = getClass().getResourceAsStream("/alertas_cliente.json")) {
			cargas = JsonMapper.builder().build().readTree(in);
		}
		assertTrue(cargas.size() > 50, "El archivo de contrato debe traer todas las alertas del motor");

		for (JsonNode carga : cargas) {
			assertEquals(200, enviar(carga.toString()), "Rechazada por el backend: " + carga);
		}
		assertEquals(cargas.size(), alertas.findBySesionIdOrderByHoraCapturaAsc(SESION).size());
	}

	@Test
	@WithMockUser(username = CORREO, roles = "ESTUDIANTE")
	void laSeveridadYElNombreDeLaAlertaSeConservan() throws Exception {
		String carga = "{\"claseAlerta\":\"VISION\",\"nivelRiesgo\":\"CRITICO\",\"tipoEvidenciaVision\":\"SUPLANTACION_IDENTIDAD\","
				+ "\"objetoDetectado\":\"SUPLANTACION_ROSTRO_ESTATICO\",\"cantidadRostros\":0,\"urlFotoWebcam\":\"AAAA\"}";
		assertEquals(200, enviar(carga));
		AlertaEvidencia guardada = alertas.findBySesionIdOrderByHoraCapturaAsc(SESION).get(0);
		assertEquals(ClaseAlerta.VISION, guardada.getClaseAlerta());
		assertEquals(NivelRiesgo.CRITICO, guardada.getNivelRiesgo());
		AlertaVision vision = (AlertaVision) guardada;
		assertEquals(TipoEvidenciaVision.SUPLANTACION_IDENTIDAD, vision.getTipoEvidencia());
		assertEquals("SUPLANTACION_ROSTRO_ESTATICO", vision.getObjetoDetectado());
	}

	@Test
	@WithMockUser(username = CORREO, roles = "ESTUDIANTE")
	void unEnumDesconocidoYaNoRechazaLaAlertaCompleta() throws Exception {
		// Una app más nueva que el servidor no debe perder alertas: el valor desconocido toma el valor por defecto
		String carga = "{\"claseAlerta\":\"VISION\",\"nivelRiesgo\":\"GRAVISIMO\",\"tipoEvidenciaVision\":\"TIPO_FUTURO\","
				+ "\"objetoDetectado\":\"MIRADA_ABAJO\"}";
		assertEquals(200, enviar(carga));
		List<AlertaEvidencia> guardadas = alertas.findBySesionIdOrderByHoraCapturaAsc(SESION);
		assertEquals(1, guardadas.size());
		assertEquals(NivelRiesgo.MEDIO, guardadas.get(0).getNivelRiesgo());
		assertEquals(TipoEvidenciaVision.WEBCAM_OBJETO, ((AlertaVision) guardadas.get(0)).getTipoEvidencia());
	}

	@Test
	@WithMockUser(username = CORREO, roles = "ESTUDIANTE")
	void unaClaseDeAlertaInvalidaSeRechazaConUnMensajeClaro() throws Exception {
		String cuerpo = cuerpoDe("{\"claseAlerta\":\"CLASE_QUE_NO_EXISTE\",\"nivelRiesgo\":\"ALTO\"}");
		assertTrue(cuerpo.contains("claseAlerta"), "El 400 debe explicar el motivo, fue: '" + cuerpo + "'");
		assertEquals(400, enviar("{\"nivelRiesgo\":\"ALTO\"}"));
		assertTrue(alertas.findBySesionIdOrderByHoraCapturaAsc(SESION).isEmpty());
	}

	@Test
	@WithMockUser(username = CORREO, roles = "ESTUDIANTE")
	void sinNivelDeRiesgoSeUsaMedio() throws Exception {
		assertEquals(200, enviar("{\"claseAlerta\":\"VISION\",\"objetoDetectado\":\"MIRADA_ABAJO\"}"));
		assertEquals(NivelRiesgo.MEDIO, alertas.findBySesionIdOrderByHoraCapturaAsc(SESION).get(0).getNivelRiesgo());
	}

	@Test
	@WithMockUser(username = CORREO, roles = "ESTUDIANTE")
	void unaEvidenciaGrandeSeAcepta() throws Exception {
		String imagenGrande = "A".repeat(3 * 1024 * 1024);          // ~3 MB de base64, más que un fotograma HD
		assertEquals(200, enviar("{\"claseAlerta\":\"VISION\",\"nivelRiesgo\":\"ALTO\",\"objetoDetectado\":\"OBJETO_SOSPECHOSO_CELULAR\","
				+ "\"urlFotoWebcam\":\"" + imagenGrande + "\"}"));
	}

	@Test
	@WithMockUser(username = CORREO, roles = "ESTUDIANTE")
	void unaRafagaDeAlertasSeGuardaCompleta() throws Exception {
		for (int i = 0; i < 150; i++) {
			assertEquals(200, enviar("{\"claseAlerta\":\"VISION\",\"nivelRiesgo\":\"BAJO\",\"objetoDetectado\":\"CABEZA_INCLINADA_DERECHA\"}"));
		}
		assertEquals(150, alertas.findBySesionIdOrderByHoraCapturaAsc(SESION).size());
	}

	@Test
	@WithMockUser(username = CORREO_DOCENTE, roles = "PROFESOR")
	void elDocenteLeeLasAlertasConSuSeveridad() throws Exception {
		enviarComoDocente("{\"claseAlerta\":\"OBJETO\",\"nivelRiesgo\":\"ALTO\",\"objetoDetectado\":\"OBJETO_SOSPECHOSO_CELULAR\",\"confianzaIa\":0.71}");
		String json = mvc.perform(get("/api/supervision/" + SESION + "/alertas")).andReturn().getResponse().getContentAsString();
		assertTrue(json.contains("OBJETO_SOSPECHOSO_CELULAR"), json);
		assertTrue(json.contains("\"ALTO\""), json);
		assertFalse(json.contains("0.99"));
	}

	private void enviarComoDocente(String json) throws Exception {
		assertEquals(200, enviar(json));
	}
}
