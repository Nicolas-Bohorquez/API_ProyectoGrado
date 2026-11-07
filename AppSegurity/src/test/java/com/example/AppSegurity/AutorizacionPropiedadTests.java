package com.example.AppSegurity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MvcResult;

import com.example.AppSegurity.Enums.Estado_pin;
import com.example.AppSegurity.Enums.EstadoSesion;
import com.example.AppSegurity.Models.Estudiante;
import com.example.AppSegurity.Models.Examen;
import com.example.AppSegurity.Models.Profesor;
import com.example.AppSegurity.Models.SesionSupervision;
import com.example.AppSegurity.Repositorys.EstudianteRepository;
import com.example.AppSegurity.Repositorys.ExamenRepository;
import com.example.AppSegurity.Repositorys.ProfesorRepository;
import com.example.AppSegurity.Repositorys.SesionSupervisionRepository;
import com.example.AppSegurity.Sub_Clases.ControlAcceso;

/**
 * Autorización por PROPIEDAD: aunque el rol sea correcto, un estudiante no puede tocar la sesión de otro y un docente
 * no puede ver ni cambiar los exámenes de otro. Usa datos reales en la base de pruebas (app_supervision_test).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"SEEDERS_ENABLED=false",
		"spring.mongodb.uri=mongodb://localhost:27017/app_supervision_test",
		"spring.data.mongodb.uri=mongodb://localhost:27017/app_supervision_test"})
@AutoConfigureMockMvc
class AutorizacionPropiedadTests {

	@Autowired private MockMvc mockMvc;
	@Autowired private ProfesorRepository profesorRepository;
	@Autowired private EstudianteRepository estudianteRepository;
	@Autowired private ExamenRepository examenRepository;
	@Autowired private SesionSupervisionRepository sesionRepository;

	private final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	private String profA, profB, emailA, emailB, estE1, estE2, emailE1, emailE2, examen, sesion, materia;
	private final ArrayList<Path> aLimpiar = new ArrayList<>();

	@BeforeEach
	void datos() {
		profA = "TPA" + sufijo; profB = "TPB" + sufijo;
		emailA = "a." + sufijo + "@test.co"; emailB = "b." + sufijo + "@test.co";
		estE1 = "TE1" + sufijo; estE2 = "TE2" + sufijo;
		emailE1 = "e1." + sufijo + "@test.co"; emailE2 = "e2." + sufijo + "@test.co";
		examen = "TEX" + sufijo; sesion = "TSES" + sufijo; materia = "TMAT" + sufijo;

		for (String[] p : new String[][] {{profA, emailA}, {profB, emailB}}) {
			Profesor pr = new Profesor();
			pr.setCodigoProfesor(p[0]); pr.setEmailInstitucional(p[1]); pr.setNombre("Prof"); pr.setApellidos("Prueba");
			pr.setPasswordHash("x"); pr.setRol("PROFESOR");
			profesorRepository.save(pr);
		}
		for (String[] e : new String[][] {{estE1, emailE1}, {estE2, emailE2}}) {
			Estudiante es = new Estudiante();
			es.setEstudianteId(e[0]); es.setEmail(e[1]); es.setNombre("Est"); es.setApellidos("Prueba"); es.setPasswordHash("x");
			estudianteRepository.save(es);
		}
		Examen ex = new Examen();
		ex.setCodigoExamen(examen); ex.setProfesorId(profA); ex.setMateriaCodigo(materia);
		ControlAcceso ca = new ControlAcceso(); ca.setPinSesion("PIN" + sufijo); ca.setEstadoPin(Estado_pin.FINALIZADO);
		ex.setControlAcceso(ca);
		examenRepository.save(ex);
		SesionSupervision s = new SesionSupervision();
		s.setSesionId(sesion); s.setExamenId(examen); s.setEstudianteId(estE1); s.setEstadoSesion(EstadoSesion.INICIADA);
		s.setNombreEstudiante("Est Prueba");
		sesionRepository.save(s);
	}

	@AfterEach
	void limpiar() throws Exception {
		profesorRepository.deleteById(profA); profesorRepository.deleteById(profB);
		estudianteRepository.deleteById(estE1); estudianteRepository.deleteById(estE2);
		examenRepository.deleteById(examen); sesionRepository.deleteById(sesion);
		for (Path p : aLimpiar) Files.deleteIfExists(p);          // de adentro hacia afuera: archivo, carpetas, materia
	}

	// ───────── utilidades ─────────
	private RequestPostProcessor docente(String email) { return user(email).roles("PROFESOR"); }
	private RequestPostProcessor estudiante(String email) { return user(email).roles("ESTUDIANTE"); }
	private RequestPostProcessor admin() { return user("admin." + sufijo + "@test.co").roles("PROFESOR", "ADMIN", "SUPERADMIN"); }

	private MvcResult ejecutar(RequestBuilder r) throws Exception { return mockMvc.perform(r).andReturn(); }
	private int estado(RequestBuilder r) throws Exception { return ejecutar(r).getResponse().getStatus(); }
	private void rechazado(RequestBuilder r, String que) throws Exception {
		assertEquals(403, estado(r), que + " debía rechazarse con 403");
	}
	private void permitido(RequestBuilder r, String que) throws Exception {
		assertNotEquals(403, estado(r), que + " no debía rechazarse por autorización");
	}

	// ───────── estudiante: solo su propia sesión ─────────
	@Test
	void unEstudianteNoPuedeUsarLaSesionDeOtro() throws Exception {
		RequestPostProcessor intruso = estudiante(emailE2);
		rechazado(post("/api/supervision/" + sesion + "/alerta").with(intruso).contentType("application/json").content("{}"), "alerta ajena");
		rechazado(post("/api/supervision/" + sesion + "/heartbeat").with(intruso), "heartbeat ajeno");
		rechazado(post("/api/supervision/" + sesion + "/finalizar").with(intruso), "finalizar ajeno");
		rechazado(post("/api/supervision/" + sesion + "/interrumpir").with(intruso), "interrumpir ajeno");
		rechazado(get("/api/supervision/reglas/" + sesion).with(intruso), "reglas ajenas");
		rechazado(post("/api/apelaciones/" + sesion + "/solicitar").with(intruso).contentType("text/plain").content("quiero apelar"), "apelación ajena");
	}

	@Test
	void elEstudianteDuenoSiPuedeUsarSuSesion() throws Exception {
		RequestPostProcessor yo = estudiante(emailE1);
		MvcResult hb = ejecutar(post("/api/supervision/" + sesion + "/heartbeat").with(yo));
		assertEquals(200, hb.getResponse().getStatus(), hb.getResponse().getContentAsString());
		permitido(post("/api/supervision/" + sesion + "/alerta").with(yo).contentType("application/json").content("{}"), "alerta propia");
		permitido(get("/api/supervision/reglas/" + sesion).with(yo), "reglas propias");
	}

	@Test
	void unEstudianteNoPuedeIniciarSesionANombreDeOtro() throws Exception {
		String cuerpo = "{\"estudianteId\":\"" + estE2 + "\",\"pinExamen\":\"PIN" + sufijo + "\",\"conexion\":{}}";
		MvcResult r = ejecutar(post("/api/supervision/iniciar").with(estudiante(emailE1)).contentType("application/json").content(cuerpo));
		assertEquals(403, r.getResponse().getStatus());
		assertEquals("", r.getResponse().getContentAsString(), "debe rechazarlo la autorización (sin cuerpo), no la lógica del servicio");
	}

	@Test
	void alIniciarSesionLaIdentidadSaleDelTokenAunqueElCuerpoVengaVacio() throws Exception {
		// Sin estudianteId en el cuerpo: se usa el del token. El examen está cerrado, así que la lógica del servicio lo rechaza
		// con su propio mensaje: eso prueba que la autorización dejó pasar la petición.
		String cuerpo = "{\"pinExamen\":\"PIN" + sufijo + "\",\"conexion\":{}}";
		MvcResult r = ejecutar(post("/api/supervision/iniciar").with(estudiante(emailE1)).contentType("application/json").content(cuerpo));
		assertTrue(r.getResponse().getContentAsString().startsWith("Acceso denegado: "), r.getResponse().getContentAsString());
	}

	// ───────── docente: solo sus exámenes ─────────
	@Test
	void unDocenteNoPuedeVerLosDatosDeLaSesionOExamenDeOtro() throws Exception {
		RequestPostProcessor otro = docente(emailB);
		rechazado(get("/api/supervision/sesion/" + sesion).with(otro), "sesión ajena");
		rechazado(get("/api/supervision/" + sesion + "/alertas").with(otro), "alertas de sesión ajena");
		rechazado(get("/api/supervision/examen/" + examen + "/alertas").with(otro), "alertas de examen ajeno");
		rechazado(get("/api/supervision/examen/" + examen + "/sesiones").with(otro), "sesiones de examen ajeno");
		rechazado(post("/api/supervision/" + sesion + "/veredicto").with(otro).param("veredictoDocente", "x").param("urlPDF", "x")
				.contentType("application/json").content("{}"), "veredicto en sesión ajena");
	}

	@Test
	void elDocenteDuenoSiPuedeVerSusDatos() throws Exception {
		RequestPostProcessor yo = docente(emailA);
		assertEquals(200, estado(get("/api/supervision/sesion/" + sesion).with(yo)));
		assertEquals(200, estado(get("/api/supervision/" + sesion + "/alertas").with(yo)));
		assertEquals(200, estado(get("/api/supervision/examen/" + examen + "/alertas").with(yo)));
		assertEquals(200, estado(get("/api/supervision/examen/" + examen + "/sesiones").with(yo)));
	}

	@Test
	void unDocenteNoPuedeAdministrarElExamenDeOtro() throws Exception {
		RequestPostProcessor otro = docente(emailB);
		rechazado(post("/api/examenes/" + examen + "/abrir").with(otro), "abrir examen ajeno");
		rechazado(post("/api/examenes/" + examen + "/cerrar").with(otro), "cerrar examen ajeno");
		rechazado(put("/api/examenes/" + examen + "/configurar").with(otro).contentType("application/json").content("{}"), "configurar examen ajeno");
		rechazado(post("/api/examenes/" + examen + "/estudiantes/bulk").with(otro).contentType("application/json").content("[]"), "inscribir en examen ajeno");
		rechazado(get("/api/examenes/" + examen + "/analisis-ia").with(otro), "leer análisis ajeno");
		rechazado(put("/api/examenes/" + examen + "/analisis-ia").with(otro).contentType("application/json").content("{}"), "escribir análisis ajeno");
		rechazado(get("/api/examenes/profesor/" + profA).with(otro), "listar exámenes de otro docente");
		rechazado(get("/api/apelaciones/profesor/" + profA + "/pendientes").with(otro), "apelaciones de otro docente");
		rechazado(put("/api/apelaciones/" + sesion + "/resolver").with(otro).param("resolucionComite", "x").param("estadoApelacion", "APROBADA_A_FAVOR"), "resolver apelación ajena");
	}

	@Test
	void unDocenteNoPuedeCrearExamenesANombreDeOtro() throws Exception {
		String cuerpo = "{\"codigoExamen\":\"TNUEVO" + sufijo + "\",\"profesorId\":\"" + profA + "\",\"materiaCodigo\":\"M\"}";
		rechazado(post("/api/examenes/crearExamen").with(docente(emailB)).contentType("application/json").content(cuerpo), "crear examen a nombre de otro");
	}

	@Test
	void elDocenteDuenoSiPuedeAdministrarSuExamen() throws Exception {
		RequestPostProcessor yo = docente(emailA);
		permitido(post("/api/examenes/" + examen + "/abrir").with(yo), "abrir propio");
		permitido(post("/api/examenes/" + examen + "/cerrar").with(yo), "cerrar propio");
		assertEquals(200, estado(get("/api/examenes/profesor/" + profA).with(yo)));
		assertEquals(200, estado(get("/api/apelaciones/profesor/" + profA + "/pendientes").with(yo)));
	}

	@Test
	void elAdministradorPuedeTodo() throws Exception {
		RequestPostProcessor adm = admin();
		assertEquals(200, estado(get("/api/supervision/sesion/" + sesion).with(adm)));
		assertEquals(200, estado(get("/api/examenes/profesor/" + profA).with(adm)));
		permitido(post("/api/examenes/" + examen + "/abrir").with(adm), "abrir como admin");
	}

	// ───────── evidencias en disco ─────────
	private String crearEvidencia(String sesionDeLaRuta) throws Exception {
		Path base = Paths.get(System.getProperty("user.home"), "Documents", "DataSupervision", "Examenes");
		Path carpeta = base.resolve(materia).resolve("est_prueba").resolve(sesionDeLaRuta).resolve("webcam");
		Files.createDirectories(carpeta);
		Path archivo = carpeta.resolve("foto.txt");
		Files.writeString(archivo, "evidencia de prueba");
		// se limpia de adentro hacia afuera
		aLimpiar.add(archivo); aLimpiar.add(carpeta); aLimpiar.add(carpeta.getParent()); aLimpiar.add(carpeta.getParent().getParent());
		aLimpiar.add(carpeta.getParent().getParent().getParent());
		return materia + "/est_prueba/" + sesionDeLaRuta + "/webcam/foto.txt";
	}

	@Test
	void soloElDocenteDuenoDescargaLaEvidenciaDeSuSesion() throws Exception {
		String ruta = crearEvidencia(sesion);
		assertEquals(200, estado(get("/api/supervision/evidencia/descargar").param("ruta", ruta).with(docente(emailA))));
		rechazado(get("/api/supervision/evidencia/descargar").param("ruta", ruta).with(docente(emailB)), "evidencia de otro docente");
		rechazado(get("/api/supervision/evidencia/descargar").param("ruta", ruta).with(estudiante(emailE1)), "evidencia pedida por un estudiante");
		assertEquals(200, estado(get("/api/supervision/evidencia/descargar").param("ruta", ruta).with(admin())));
	}

	@Test
	void losTruquitosConLaRutaNoSirvenParaSaltarseLaAutorizacion() throws Exception {
		String ruta = crearEvidencia(sesion);
		// el docente B intenta "apoyarse" en una sesión inventada y salir hacia la de A con '..'
		String truco = materia + "/est_prueba/" + sesion + "/webcam/../../../" + materia + "/est_prueba/" + sesion + "/webcam/foto.txt";
		String ocultoEnOtraCarpeta = "sesion_inventada/../" + ruta;
		for (String r : new String[] {truco, ocultoEnOtraCarpeta, "./" + ruta, ruta.replace("/", "//")}) {
			rechazado(get("/api/supervision/evidencia/descargar").param("ruta", r).with(docente(emailB)), "ruta con truco: " + r);
		}
		// una ruta que no corresponde a ninguna sesión solo la baja un administrador
		rechazado(get("/api/supervision/evidencia/descargar").param("ruta", "no_existe/foto.png").with(docente(emailA)), "ruta sin sesión");
	}
}
