package com.example.AppSegurity.Security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtProveedor {

    //La llave se lee de la variable de entorno JWT_SECRET (mínimo 32 caracteres = 256 bits).
    //Si no está definida se usa la llave anterior para no tumbar el despliegue, pero es INSEGURA: defina JWT_SECRET en producción.
    private static final String JWT_SECRET_LEGADO = "EstaEsUnaLlaveSecretaSuperSeguraParaElProyectoDeGrado123456789";
    private final String JWT_SECRET = resolverSecreto();

    private static String resolverSecreto() {
        String secreto = System.getenv("JWT_SECRET");
        if (secreto == null || secreto.length() < 32) {
            System.err.println("[SEGURIDAD] JWT_SECRET no definido (o menor a 32 caracteres): se usa la llave legada insegura.");
            return JWT_SECRET_LEGADO;
        }
        return secreto;
    }
    
    //Tiempo de vida del Access Token: 15 minutos (en milisegundos)
    private final long ACCESS_TOKEN_EXPIRATION = 15 * 60 * 1000;

    //Generamos la llave encriptada a partir de nuestro texto secreto
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
    }

    //1. Fabricamos el toke y Se llama cuando el usuario se loguea exitosamente
    public String generarToken(Authentication authentication) {
        //Spring Security nos da el objeto del usuario logueado
        UserDetails usuario = (UserDetails) authentication.getPrincipal();
        
        Date ahora = new Date();
        Date fechaExpiracion = new Date(ahora.getTime() + ACCESS_TOKEN_EXPIRATION);

        // Construimos el Token JWT
        return Jwts.builder()
                .setSubject(usuario.getUsername()) // El 'Username' será el Email del profesor o alumno
                .setIssuedAt(ahora)
                .setExpiration(fechaExpiracion)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    //2. Extraemos el email y lo usamos para leer el token cuando alguien hace una petición
    public String extraerEmail(String token) {
        try {
            io.jsonwebtoken.Claims claims = io.jsonwebtoken.Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getSubject();
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return e.getClaims().getSubject();
        }
    }
    
    /** Instante en que expiró (o expirará) el token; solo se lee si la firma es válida. */
    public java.util.Date extraerExpiracion(String token) {
        try {
            return io.jsonwebtoken.Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                    .parseClaimsJws(token).getBody().getExpiration();
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return e.getClaims().getExpiration();
        }
    }

    public boolean validarFirma(String token) {
        try {
            io.jsonwebtoken.Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return true; // Expirado pero firma valida
        } catch (Exception e) {
            return false; // Alterado o invalido
        }
    }

    //3. Validamos el token y detectamos si el token está expirado o fue alterado por un hacker
    public boolean validarToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            System.out.println("Error de Seguridad - Token Inválido: " + e.getMessage());
            return false;
        }
    }
}
