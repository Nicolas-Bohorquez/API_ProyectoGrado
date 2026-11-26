package com.example.AppSegurity.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFiltradoTokens extends OncePerRequestFilter {

    @Autowired
    private JwtProveedor jwtProveedor;

    @Autowired
    private ServicioDetallesUsuarioPersonalizados userDetailsService;

    //Este método se ejecuta automáticamente cada vez que alguien llama a una ruta de nuestra API
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
            
        try {
            //1. Extraemos el token de la petición HTTP
            String token = extraerTokenDelRequest(request);

            //2. Si hay token y la JwtProveedor verifica que sea válido
            if (token != null && jwtProveedor.validarToken(token)) {
                
                //3. Sacamos el email y versión del token
                String email = jwtProveedor.extraerEmail(token);
                int tokenVersion = jwtProveedor.extraerTokenVersion(token);

                //4. Verificamos versión de revocación y cargamos usuario activo
                if (tokenVersion != userDetailsService.obtenerTokenVersion(email)) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                //5. Creamos la "Credencial" oficial de Spring Security
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                //6. Registramos al usuario en la memoria del servidor y ya puede entrar.
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception e) {
            System.out.println("Error en el Filtro JWT: No se pudo autenticar al usuario -> " + e.getMessage());
        }

        //7. Pase lo que pase (se haya logueado o no), dejamos que la petición continúe.
        //Si el usuario no ogró loguearse arriba y la ruta es privada, Spring Security la bloqueará más adelante.
        filterChain.doFilter(request, response);
    }

    //Función auxiliar para leer el encabezado que nos manda Python o Moodle
    private String extraerTokenDelRequest(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        
        // Y siempre vienen precedidos por la palabra "Bearer " (Portador)
        if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7); // Cortamos los primeros 7 caracteres ("Bearer ") para dejar el token puro
        }
        return null;
    }
}