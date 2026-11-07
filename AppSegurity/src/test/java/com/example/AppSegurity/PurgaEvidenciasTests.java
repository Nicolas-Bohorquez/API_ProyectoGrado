package com.example.AppSegurity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.AppSegurity.Services.PurgaEvidenciasService;

/** Retención de evidencias: solo se borra lo viejo, solo dentro de la carpeta de evidencias y sin tocar nada más. */
class PurgaEvidenciasTests {

	private Path raiz, base, fuera;

	@BeforeEach
	void preparar() throws IOException {
		raiz = Files.createTempDirectory("purga_");
		base = Files.createDirectories(raiz.resolve("Examenes"));
		fuera = Files.createDirectories(raiz.resolve("otra_carpeta"));
	}

	@AfterEach
	void limpiar() throws IOException {
		try (Stream<Path> s = Files.walk(raiz)) {
			s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	private Path archivo(Path carpeta, String nombre, long diasDeAntiguedad) throws IOException {
		Files.createDirectories(carpeta);
		Path f = carpeta.resolve(nombre);
		Files.writeString(f, "evidencia");
		Files.setLastModifiedTime(f, FileTime.from(Instant.now().minus(Duration.ofDays(diasDeAntiguedad))));
		return f;
	}

	@Test
	void borraLoViejoYConservaLoReciente() throws IOException {
		Path ses = base.resolve("MAT").resolve("Ana").resolve("SES-1").resolve("webcam");
		Path vieja = archivo(ses, "vieja.webp", 200);
		Path justoAntes = archivo(ses, "casi.webp", 179);
		Path reciente = archivo(ses, "nueva.webp", 1);
		int n = new PurgaEvidenciasService(base, 180).purgar(Instant.now().minus(Duration.ofDays(180)));
		assertEquals(1, n);
		assertFalse(Files.exists(vieja));
		assertTrue(Files.exists(justoAntes), "con 179 días todavía no toca");
		assertTrue(Files.exists(reciente));
	}

	@Test
	void retiraLasCarpetasQueQuedanVaciasPeroNoLaBase() throws IOException {
		Path viejo = archivo(base.resolve("MAT").resolve("Ana").resolve("SES-1").resolve("audio"), "a.wav", 400);
		new PurgaEvidenciasService(base, 180).purgar(Instant.now().minus(Duration.ofDays(180)));
		assertFalse(Files.exists(viejo));
		assertFalse(Files.exists(base.resolve("MAT")), "las carpetas vacías se retiran");
		assertTrue(Files.isDirectory(base), "la carpeta base nunca se borra");
	}

	@Test
	void unaCarpetaConEvidenciaRecienteNoSeBorra() throws IOException {
		Path ses = base.resolve("MAT").resolve("Luis").resolve("SES-2");
		archivo(ses.resolve("webcam"), "vieja.webp", 300);
		Path reciente = archivo(ses.resolve("proceso"), "hoy.webp", 0);
		new PurgaEvidenciasService(base, 180).purgar(Instant.now().minus(Duration.ofDays(180)));
		assertTrue(Files.exists(reciente));
		assertFalse(Files.exists(ses.resolve("webcam")), "la carpeta que quedó vacía se retira");
		assertTrue(Files.isDirectory(ses.resolve("proceso")));
	}

	@Test
	void nuncaTocaNadaFueraDeLaCarpetaDeEvidencias() throws IOException {
		Path ajeno = archivo(fuera, "importante.txt", 1000);
		Path vecino = archivo(raiz, "vecino.txt", 1000);
		archivo(base, "suelto.webp", 1000);
		new PurgaEvidenciasService(base, 180).purgar(Instant.now().minus(Duration.ofDays(180)));
		assertTrue(Files.exists(ajeno));
		assertTrue(Files.exists(vecino));
	}

	@Test
	void noSigueEnlacesSimbolicosHaciaAfuera() throws IOException {
		Path ajeno = archivo(fuera, "intocable.txt", 1000);
		Path enlace = base.resolve("enlace");
		try {
			Files.createSymbolicLink(enlace, fuera);
		} catch (UnsupportedOperationException | IOException | SecurityException e) {
			return;       // sin permiso para crear enlaces en este equipo: la protección igual existe, solo no se puede probar aquí
		}
		new PurgaEvidenciasService(base, 180).purgar(Instant.now().minus(Duration.ofDays(180)));
		assertTrue(Files.exists(ajeno), "lo que hay detrás de un enlace simbólico no se debe borrar");
	}

	@Test
	void conLaCarpetaInexistenteNoHaceNada() {
		assertEquals(0, new PurgaEvidenciasService(raiz.resolve("no_existe"), 180).purgar(Instant.now()));
	}

	@Test
	void desactivadaConCeroDias() throws IOException {
		Path vieja = archivo(base.resolve("M").resolve("E").resolve("S"), "x.webp", 1000);
		new PurgaEvidenciasService(base, 0).purgarProgramada();
		assertTrue(Files.exists(vieja), "con 0 días la purga está desactivada");
		new PurgaEvidenciasService(base, -5).purgarProgramada();
		assertTrue(Files.exists(vieja), "un plazo negativo tampoco borra nada");
	}

	@Test
	void laTareaProgramadaAplicaElPlazoConfigurado() throws IOException {
		Path vieja = archivo(base.resolve("M").resolve("E").resolve("S"), "vieja.webp", 100);
		Path nueva = archivo(base.resolve("M").resolve("E").resolve("S"), "nueva.webp", 5);
		new PurgaEvidenciasService(base, 30).purgarProgramada();
		assertFalse(Files.exists(vieja));
		assertTrue(Files.exists(nueva));
	}

	@Test
	void unPlazoDeUnDiaNuncaBorraLoDeHoy() throws IOException {
		Path hoy = archivo(base.resolve("M").resolve("E").resolve("S"), "hoy.webp", 0);
		new PurgaEvidenciasService(base, 1).purgarProgramada();
		assertTrue(Files.exists(hoy));
	}
}
