package es.um.sisdist.backend.Service;

import java.util.Optional;
import es.um.sisdist.backend.Service.impl.AppLogicImpl;
import es.um.sisdist.models.UserDTO;
import es.um.sisdist.models.UserDTOUtils;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import es.um.sisdist.backend.dao.models.User;

@Path("/u")
public class UsersEndpoint
{
    private AppLogicImpl impl = AppLogicImpl.getInstance();

    @GET
    @Path("/{email}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserInfo(@PathParam("email") String email) {
        return impl.getUserByEmail(email)
            .map(u -> Response.ok(UserDTOUtils.toDTO(u)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    // Endpoint: POST /u/register
    // Función: Recibe un UserDTO desde Flask, lo registra y devuelve un 200 OK o 400 Error.
    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response registerUser(UserDTO newUser) {
        Optional<User> registered = impl.registerUser(newUser);
        if (registered.isPresent()) {
            return Response.ok(UserDTOUtils.toDTO(registered.get())).build();
        }
        return Response.status(Response.Status.BAD_REQUEST).entity("El email ya existe o error interno").build();
    }

    // Endpoint: POST /u/login
    // Función: Comprueba las credenciales. Si son correctas, devuelve los datos del usuario.
    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response loginUser(UserDTO loginData) {
        Optional<User> u = impl.checkLogin(loginData.getEmail(), loginData.getPassword());
        if (u.isPresent()) {
            return Response.ok(UserDTOUtils.toDTO(u.get())).build();
        }
        return Response.status(Response.Status.UNAUTHORIZED).entity("Credenciales inválidas").build();
    }
}
