package com.example.AppSegurity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.AppSegurity.Enums.EstadoSesion;
import com.example.AppSegurity.Enums.EstadoUsuario;
import com.example.AppSegurity.Enums.Estado_pin;
import com.example.AppSegurity.Models.Estudiante;
import com.example.AppSegurity.Models.Examen;
import com.example.AppSegurity.Models.Profesor;
import com.example.AppSegurity.Models.SesionSupervision;
import com.example.AppSegurity.Repositorys.EstudianteRepository;
import com.example.AppSegurity.Repositorys.ExamenRepository;
import com.example.AppSegurity.Repositorys.ProfesorRepository;
import com.example.AppSegurity.Repositorys.SesionSupervisionRepository;
import com.example.AppSegurity.Security.AESEncryptionUtil;
import com.example.AppSegurity.Security.GeneradorClaves;
import com.example.AppSegurity.Sub_Clases.ControlAcceso;
import com.example.AppSegurity.Sub_Clases.Materia;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Cada sesión tiene SU PROPIA clave AES (antes había una fija, igual para todos y publicada en los parches). La genera el
 * servidor, se guarda cifrada, y solo la reciben el estudiante dueño de la sesión y el docente dueño del examen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"SEEDERS_ENABLED=false",
		"spring.mongodb.uri=mongodb://localhost:27017/app_supervision_test",
		"spring.data.mongodb.uri=mongodb://localhost:27017/app_supervision_test"})
@AutoConfigureMockMvc
class ClaveSesionTests {

	@Autowired private MockMvc mockMvc;
	@Autowired private ProfesorRepository profesorRepository;
	@Autowired private EstudianteRepository estudianteRepository;
	@Autowired private ExamenRepository examenRepository;
	@Autowired private SesionSupervisionRepository sesionRepository;
	private final ObjectMapper json = new ObjectMapper();

	private final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	private String profA, profB, emailA, emailB, estE1, estE2, emailE1, emailE2, examen, materia, pin;
	private final List<String> sesiones = new ArrayList<>();

	@BeforeEach
	void datos() {
		profA = "KPA" + sufijo; profB = "KPB" + sufijo;
		emailA = "ka." + sufijo + "@test.co"; emailB = "kb." + sufijo + "@test.co";
		estE1 = "KE1" + sufijo; estE2 = "KE2" + sufijo;
		emailE1 = "ke1." + sufijo + "@test.co"; emailE2 = "ke2." + sufijo + "@test.co";
		examen = "KEX" + sufijo; materia = "KMAT" + sufijo; pin = "PIN" + sufijo;
		for (String[] p : new String[][] {{profA, emailA}, {profB, emailB}}) {
			Profesor pr = new Profesor();
			pr.setCodigoProfesor(p[0]); pr.setEmailInstitucional(p[1]); pr.setNombre("P"); pr.setApellidos("K");
			pr.setPasswordHash("x"); pr.setRol("PROFESOR"); pr.setEstado(EstadoUsuario.ACTIVO);
			profesorRepository.save(pr);
		}
		for (String[] e : new String[][] {{estE1, emailE1}, {estE2, emailE2}}) {
			Estudiante es = new Estudiante();
			es.setEstudianteId(e[0]); es.setEmail(e[1]); es.setNombre("E"); es.setApellidos("K"); es.setPasswordHash("x");
			es.setEstadoUsuario(EstadoUsuario.ACTIVO);
			Materia m = new Materia(); m.setCodigoMateria(materia); m.setNombreMateria("Materia");
			es.setMateriasInscritas(new ArrayList<>(List.of(m)));
			estudianteRepository.save(es);
		}
		Examen ex = new Examen();
		ex.setCodigoExamen(examen); ex.setProfesorId(profA); ex.setMateriaCodigo(materia);
		ControlAcceso ca = new ControlAcceso(); ca.setPinSesion(pin); ca.setEstadoPin(Estado_pin.ACTIVO);
		ex.setControlAcceso(ca);
		examenRepository.save(ex);
	}

	@AfterEach
	void limpiar() {
		profesorRepository.deleteById(profA); profesorRepository.deleteById(profB);
		estudianteRepository.deleteById(estE1); estudianteRepository.deleteById(estE2);
		examenRepository.deleteById(examen);
		sesionRepository.findByExamenId(examen).forEach(s -> sesionRepository.deleteById(s.getSesionId()));
		sesiones.forEach(sesionRepository::deleteById);
	}

	private MvcResult iniciar(String emailEstudiante, String dispositivo) throws Exception {
		String cuerpo = json.writeValueAsString(Map.of("pinExamen", pin,
				"conexion", Map.of("vpnDetectada", false, "sistemaOperativo", "Windows 11", "deviceId", dispositivo)));
		return mockMvc.perform(post("/api/supervision/iniciar").with(user(emailEstudiante).roles("ESTUDIANTE"))
				.contentType("application/json").content(cuerpo)).andReturn();
	}

	private static void esClaveFernetValida(String clave) {
		assertNotNull(clave, "la sesión debe traer su clave");
		assertEquals(44, clave.length(), "una clave Fernet son 44 caracteres");
		assertEquals(32, Base64.getUrlDecoder().decode(clave).length, "debe ser de 256 bits");
	}

	@Test
	void alIniciarSesionElEstudianteRecibeSuClaveYQuedaGuardadaCifrada() throws Exception {
		MvcResult r = iniciar(emailE1, "DEV-1");
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		JsonNode sesion = json.readTree(r.getResponse().getContentAsString());
		String clave = sesion.get("claveSesion").asText();
		esClaveFernetValida(clave);
		assertFalse(sesion.has("claveSesionCifrada"), "el valor cifrado interno no debe salir");

		SesionSupervision guardada = sesionRepository.findById(sesion.get("sesionId").asText()).orElseThrow();
		assertNotNull(guardada.getClaveSesionCifrada());
		assertNotEquals(clave, guardada.getClaveSesionCifrada(), "en la base de datos NO está en claro");
		assertFalse(guardada.getClaveSesionCifrada().contains(clave));
		assertEquals(clave, AESEncryptionUtil.desencriptarDatoSensible(guardada.getClaveSesionCifrada()));
	}

	@Test
	void cadaSesionTieneUnaClaveDistinta() throws Exception {
		Set<String> claves = new HashSet<>();
		claves.add(json.readTree(iniciar(emailE1, "DEV-1").getResponse().getContentAsString()).get("claveSesion").asText());
		claves.add(json.readTree(iniciar(emailE2, "DEV-2").getResponse().getContentAsString()).get("claveSesion").asText());
		assertEquals(2, claves.size(), "dos estudiantes no comparten clave");
		Set<String> muchas = new HashSet<>();
		for (int i = 0; i < 200; i++) muchas.add(GeneradorClaves.claveFernet());
		assertEquals(200, muchas.size(), "el generador no repite claves");
	}

	@Test
	void alReanudarEnElMismoEquipoLaClaveEsLaMisma() throws Exception {
		String primera = json.readTree(iniciar(emailE1, "DEV-1").getResponse().getContentAsString()).get("claveSesion").asText();
		MvcResult otra = iniciar(emailE1, "DEV-1");
		assertEquals(200, otra.getResponse().getStatus());
		assertEquals(primera, json.readTree(otra.getResponse().getContentAsString()).get("claveSesion").asText(),
				"si la app se reinicia, el docente y el estudiante deben seguir usando la misma clave");
	}

	@Test
	void unSegundoEquipoNoRecibeLaClaveNiAccede() throws Exception {
		String primera = json.readTree(iniciar(emailE1, "DEV-1").getResponse().getContentAsString()).get("claveSesion").asText();
		MvcResult intruso = iniciar(emailE1, "DEV-OTRO");
		assertEquals(403, intruso.getResponse().getStatus());
		assertFalse(intruso.getResponse().getContentAsString().contains(primera), "la respuesta de rechazo no debe filtrar la clave");
	}

	@Test
	void soloElDocenteDuenoLeeLaClaveDeLaSesion() throws Exception {
		JsonNode s = json.readTree(iniciar(emailE1, "DEV-1").getResponse().getContentAsString());
		String id = s.get("sesionId").asText(), clave = s.get("claveSesion").asText();

		MvcResult duenio = mockMvc.perform(get("/api/supervision/sesion/" + id).with(user(emailA).roles("PROFESOR"))).andReturn();
		assertEquals(200, duenio.getResponse().getStatus());
		assertEquals(clave, json.readTree(duenio.getResponse().getContentAsString()).get("claveSesion").asText());

		MvcResult lista = mockMvc.perform(get("/api/supervision/examen/" + examen + "/sesiones").with(user(emailA).roles("PROFESOR"))).andReturn();
		assertTrue(lista.getResponse().getContentAsString().contains(clave), "el docente ve la clave al listar las sesiones del examen");

		for (var ajeno : new org.springframework.test.web.servlet.request.RequestPostProcessor[] {
				user(emailB).roles("PROFESOR"), user(emailE2).roles("ESTUDIANTE")}) {
			MvcResult r = mockMvc.perform(get("/api/supervision/sesion/" + id).with(ajeno)).andReturn();
			assertEquals(403, r.getResponse().getStatus());
			assertFalse(r.getResponse().getContentAsString().contains(clave));
		}
	}

	@Test
	void unaSesionAnteriorSinClaveSeListaSinErrorYRecibeSuClaveAlReanudar() throws Exception {
		SesionSupervision vieja = new SesionSupervision();
		vieja.setSesionId("KOLD" + sufijo); vieja.setExamenId(examen); vieja.setEstudianteId(estE1);
		vieja.setEstadoSesion(EstadoSesion.INICIADA);
		vieja.setConexion(new com.example.AppSegurity.Sub_Clases.Conexion());
		vieja.getConexion().setDeviceId("DEV-1"); vieja.getConexion().setVpnDetectada(false); vieja.getConexion().setSistemaOperativo("Windows");
		sesionRepository.save(vieja); sesiones.add(vieja.getSesionId());

		MvcResult lista = mockMvc.perform(get("/api/supervision/sesion/" + vieja.getSesionId()).with(user(emailA).roles("PROFESOR"))).andReturn();
		assertEquals(200, lista.getResponse().getStatus(), "una sesión sin clave no debe romper el listado");
		assertTrue(json.readTree(lista.getResponse().getContentAsString()).get("claveSesion").isNull());

		MvcResult reanudada = iniciar(emailE1, "DEV-1");
		assertEquals(200, reanudada.getResponse().getStatus(), reanudada.getResponse().getContentAsString());
		esClaveFernetValida(json.readTree(reanudada.getResponse().getContentAsString()).get("claveSesion").asText());
	}

	@Test
	void siLaLlaveDelServidorCambiaLaSesionSigueListandoseSinClave() {
		SesionSupervision s = new SesionSupervision();
		s.setClaveSesionCifrada("esto-no-es-un-cifrado-valido");
		assertNull(s.getClaveSesion(), "no debe lanzar excepción (rompería el listado de sesiones del docente)");
	}
}
