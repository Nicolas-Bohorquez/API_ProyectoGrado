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

    //La llave se lee de la variable de entorno JWT_SECRET (mínimo 32 bytes = 256 bits). Sin ella el servidor no arranca
    //(ya no hay llave de respaldo escrita en el código). Ver SecretosServidor.
    private final String JWT_SECRET = SecretosServidor.resolver("JWT_SECRET",
            s -> SecretosServidor.bytesUtf8(s) >= 32, "mínimo 32 bytes", 64);
    
    //Tiempo de vida del Access Token: 15 minutos (en milisegundos)
    private final long ACCESS_TOKEN_EXPIRATION = 15 * 60 * 1000;

    //Generamos la llave encriptada a partir de nuestro texto secreto
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(JWT_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    //1. Fabricamos el toke y Se llama cuando el usuario se loguea exitosamente
    public String generarToken(Authentication authentication) {
        return generarToken(authentication, 0, System.currentTimeMillis());
    }

    public String generarToken(Authentication authentication, int tokenVersion, long origIatMs) {
        //Spring Security nos da el objeto del usuario logueado
        UserDetails usuario = (UserDetails) authentication.getPrincipal();

        Date ahora = new Date();
        Date fechaExpiracion = new Date(ahora.getTime() + ACCESS_TOKEN_EXPIRATION);

        // Construimos el Token JWT con versión de revocación y marca de inicio de la cadena de refresh
        return Jwts.builder()
                .setSubject(usuario.getUsername()) // El 'Username' será el Email del profesor o alumno
                .claim("tv", tokenVersion)
                .claim("origIat", origIatMs)
                .setIssuedAt(ahora)
                .setExpiration(fechaExpiracion)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public int extraerTokenVersion(String token) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                    .parseClaimsJws(token).getBody();
            Object v = claims.get("tv");
            return v instanceof Number ? ((Number) v).intValue() : 0;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            Object v = e.getClaims().get("tv");
            return v instanceof Number ? ((Number) v).intValue() : 0;
        }
    }

    public Long extraerOrigenCadenaMs(String token) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                    .parseClaimsJws(token).getBody();
            Object orig = claims.get("origIat");
            if (orig instanceof Number) {
                return ((Number) orig).longValue();
            }
            return claims.getIssuedAt() != null ? claims.getIssuedAt().getTime() : null;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            Object orig = e.getClaims().get("origIat");
            if (orig instanceof Number) {
                return ((Number) orig).longValue();
            }
            return e.getClaims().getIssuedAt() != null ? e.getClaims().getIssuedAt().getTime() : null;
        }
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
