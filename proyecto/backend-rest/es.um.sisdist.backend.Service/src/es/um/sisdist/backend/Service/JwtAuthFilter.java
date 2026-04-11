package es.um.sisdist.backend.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

import es.um.sisdist.backend.Service.impl.AppLogicImpl;

// La anotación @Provider le dice a Tomcat que cargue este filtro automáticamente
@Provider
public class JwtAuthFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        
        //Miramos a qué URL quiere acceder el usuario
        String path = requestContext.getUriInfo().getPath();

        //Dejamos pasar sin pedir token a las rutas de login y registro
        if (path.contains("/login") || path.contains("/register")) {
            return; 
        }

        //Buscamos la cabecera HTTP llamada "Authorization"
        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

        //Si no trae esa cabecera o no tiene el formato correcto ("Bearer <token>") se deniega el acceso
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Acceso denegado: Token JWT requerido en la cabecera").build());
            return;
        }

        // Si contiene la cabecera, se extrae el token 
        String token = authHeader.substring("Bearer".length()).trim();

        try {
            // Intentamos abrir el token con la clave secreta.
            // Si el token fue modificado, o si ya caducó, 
            // la librería lanzará una excepción y saltará al bloque 'catch'.
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(AppLogicImpl.SECRET_KEY.getBytes())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // Si llegamos aquí, el token es válido y la petición sigue su curso
            
        } catch (Exception e) {
            // Token falso, rechazamos la petición
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Acceso denegado: Token JWT inválido o caducado").build());
        }
    }
}