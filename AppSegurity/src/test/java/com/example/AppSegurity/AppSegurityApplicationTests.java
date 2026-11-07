package com.example.AppSegurity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// WebSocketConfig necesita un contenedor de servlets real (ServerContainer), por eso puerto aleatorio y no entorno MOCK
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"SEEDERS_ENABLED=false",
		"spring.mongodb.uri=mongodb://localhost:27017/app_supervision_test",
		"spring.data.mongodb.uri=mongodb://localhost:27017/app_supervision_test"})
class AppSegurityApplicationTests {

	@Test
	void contextLoads() {
	}

}
