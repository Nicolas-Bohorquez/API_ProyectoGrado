package com.example.AppSegurity.Security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class ConfiguracionSeguridad {

    @Autowired
    private JwtFiltradoTokens jwtFiltradoTokens; // Nuestro filtro cadenero que creamos antes

    //1. Aqui definimos quien tiene autorización para entrar y quien no
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                //Apagamos CSRF porque nuestra API usa JWT (es Stateless, no usa cookies de sesión vulnerables)
                .csrf(csrf -> csrf.disable())
                //Le decimos a Spring que no guarde sesiones en memoria, cada petición debe traer su Token en la cabezera para validar
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                //Configuramos las reglas para las rutas (Endpoints)
                .authorizeHttpRequests(auth -> auth
                //Rutas públicas (El Login). Todos pueden entrar aquí sin token y sin 
                // OJO: Asumo que tus Controladores tendrán "/api/auth/login..."
                .requestMatchers("/api/auth/**").permitAll()
                //Solo para administradores y superadmin
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPERADMIN")
                //Solo para los profesores (y administradores)
                .requestMatchers("/api/profesor/**").hasAnyRole("PROFESOR", "ADMIN", "SUPERADMIN")
                //Solo para los estudiantes
                .requestMatchers("/api/estudiante/**").hasRole("ESTUDIANTE")
                // Rutas que usa la app del estudiante durante el examen (cualquier usuario autenticado)
                .requestMatchers(HttpMethod.POST, "/api/supervision/iniciar").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/supervision/*/alerta").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/supervision/*/finalizar").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/supervision/*/heartbeat").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/supervision/*/interrumpir").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/supervision/reglas/*").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/apelaciones/*/solicitar").authenticated()
                // El resto de supervisión, exámenes y apelaciones es solo del personal docente
                .requestMatchers("/api/examenes/**", "/api/supervision/**", "/api/apelaciones/**")
                    .hasAnyRole("PROFESOR", "ADMIN", "SUPERADMIN")
                // Cualquier otra ruta de la API (Ej: /api/examenes) exigirá que el token sea válido
                .anyRequest().authenticated()
            )
                // Ponemos a nuestro filtro (JwtFiltradoTokens) en la puerta de entrada, 
                // antes del filtro por defecto de Spring
                .addFilterBefore(jwtFiltradoTokens, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 2. CONFIGURAR EL ENCRIPTADO (BCRYPT COMPATIBLE + ARGON2)
    @Bean
    public PasswordEncoder passwordEncoder() {
        final PasswordEncoder bcrypt = new BCryptPasswordEncoder();
        final PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 4096, 3);
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return bcrypt.encode(rawPassword);
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                if (encodedPassword == null) return false;
                if (encodedPassword.startsWith("$argon2")) {
                    return argon2.matches(rawPassword, encodedPassword);
                }
                return bcrypt.matches(rawPassword, encodedPassword);
            }
        };
    }

    // 3. EXPORTAR EL MANAGER DE AUTENTICACIÓN
    // Lo vamos a necesitar en tu 'AutenticacionService' para forzar el login manual 
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
