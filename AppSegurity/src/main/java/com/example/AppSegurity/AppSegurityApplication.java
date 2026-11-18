package com.example.AppSegurity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class AppSegurityApplication {

	@jakarta.annotation.PostConstruct
	public void configurarZonaHoraria() {
		java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Bogota"));
	}

	public static void main(String[] args) {
		java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Bogota"));
		SpringApplication.run(AppSegurityApplication.class, args);
	}

}
