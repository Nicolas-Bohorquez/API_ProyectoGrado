package com.example.AppSegurity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.example.AppSegurity.Security.IntentosExcedidosException;
import com.example.AppSegurity.Security.LimitadorIntentos;

/** Pruebas puras (sin Spring ni base de datos) del límite de intentos fallidos. */
class LimitadorIntentosTests {

	private final AtomicLong ahora = new AtomicLong(1_000_000L);

	private LimitadorIntentos nuevo(int maxFallos, long ventanaMs, long bloqueoMs) {
		LimitadorIntentos l = new LimitadorIntentos(maxFallos, ventanaMs, bloqueoMs);
		l.usarReloj(ahora::get);
		return l;
	}

	@Test
	void bloqueaTrasElMaximoDeFallosSeguidos() {
		LimitadorIntentos l = nuevo(5, 15 * 60_000, 15 * 60_000);
		for (int i = 0; i < 4; i++) {
			l.verificar("login:ana@x.co");
			l.registrarFallo("login:ana@x.co");
		}
		assertDoesNotThrow(() -> l.verificar("login:ana@x.co"), "con 4 fallos todavía puede intentar");
		l.registrarFallo("login:ana@x.co");                         // el quinto
		IntentosExcedidosException e = assertThrows(IntentosExcedidosException.class, () -> l.verificar("login:ana@x.co"));
		assertTrue(e.getMessage().contains("15 minuto"), e.getMessage());
		assertTrue(e.getMilisegundosRestantes() > 0);
	}

	@Test
	void elBloqueoTerminaConElTiempo() {
		LimitadorIntentos l = nuevo(3, 60_000, 10 * 60_000);
		for (int i = 0; i < 3; i++) l.registrarFallo("k");
		assertThrows(IntentosExcedidosException.class, () -> l.verificar("k"));
		ahora.addAndGet(9 * 60_000);
		assertThrows(IntentosExcedidosException.class, () -> l.verificar("k"), "a los 9 min sigue bloqueado");
		ahora.addAndGet(2 * 60_000);
		assertDoesNotThrow(() -> l.verificar("k"), "pasado el bloqueo puede volver a intentar");
	}

	@Test
	void unAciertoBorraElContador() {
		LimitadorIntentos l = nuevo(3, 60_000, 60_000);
		l.registrarFallo("k"); l.registrarFallo("k");
		l.registrarExito("k");
		l.registrarFallo("k"); l.registrarFallo("k");
		assertDoesNotThrow(() -> l.verificar("k"), "tras el acierto se empieza de cero");
	}

	@Test
	void losFallosViejosFueraDeLaVentanaNoSuman() {
		LimitadorIntentos l = nuevo(3, 60_000, 60_000);
		l.registrarFallo("k"); l.registrarFallo("k");
		ahora.addAndGet(120_000);                                  // la ventana de 1 min ya pasó
		l.registrarFallo("k");
		assertDoesNotThrow(() -> l.verificar("k"), "dos fallos hace 2 min + uno ahora no son 3 seguidos");
	}

	@Test
	void cadaClaveEsIndependienteYNoDistingueMayusculas() {
		LimitadorIntentos l = nuevo(2, 60_000, 60_000);
		l.registrarFallo("login:ANA@x.co"); l.registrarFallo("login:ana@x.co ");
		assertThrows(IntentosExcedidosException.class, () -> l.verificar("LOGIN:Ana@X.co"));
		assertDoesNotThrow(() -> l.verificar("login:luis@x.co"), "otro correo no se ve afectado");
	}

	@Test
	void sinRegistroPreviosNuncaBloquea() {
		LimitadorIntentos l = nuevo(5, 60_000, 60_000);
		assertDoesNotThrow(() -> l.verificar("nuevo"));
		assertDoesNotThrow(() -> l.verificar(null));
		assertEquals(0, l.registrosActivos());
	}

	@Test
	void soportaMuchosHilosAlMismoTiempo() throws Exception {
		LimitadorIntentos l = new LimitadorIntentos(1000, 60_000, 60_000);
		Thread[] hilos = new Thread[8];
		for (int i = 0; i < hilos.length; i++) {
			hilos[i] = new Thread(() -> { for (int j = 0; j < 100; j++) { l.registrarFallo("compartida"); l.verificar("compartida"); } });
			hilos[i].start();
		}
		for (Thread t : hilos) t.join();
		// 800 fallos < 1000: sigue sin bloquear; no hubo pérdida ni excepciones de concurrencia
		assertDoesNotThrow(() -> l.verificar("compartida"));
	}
}
