package es.um.sisdist.backend.Service;

import java.util.Optional;

import es.um.sisdist.backend.Service.impl.AppLogicImpl;
import es.um.sisdist.backend.dao.models.Conversation;
import es.um.sisdist.backend.dao.models.Dialogue;
import es.um.sisdist.backend.dao.models.StatusConversation;
import es.um.sisdist.backend.dao.models.User;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;


@Path("/u/{userId}/dialogue")
public class ChatsEndpoint {
    private AppLogicImpl logic = AppLogicImpl.getInstance();

    // Obtener una conversación específica
    @GET
    @Path("/{dialogueId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConversation(@PathParam("userId") String userId, @PathParam("dialogueId") String dialogueId) {
        
        // 1. Buscamos la conversación en la lógica (que usa los DAO)
        Conversation conv = logic.obtenerConversacion(userId, dialogueId);
        
        if (conv == null) return Response.status(Status.NOT_FOUND).build();

        // 2. Aquí es donde "ligamos" tus clases con las URLs de la imagen
        // Añadimos las URLs de navegación dinámicamente
        conv.setUrlNext("/u/" + userId + "/dialogue/" + dialogueId + "/next");
        conv.setUrlEnd("/u/" + userId + "/dialogue/" + dialogueId + "/end");

        return Response.ok(conv).build();
    }
    
    /**
     * 1. LISTAR CONVERSACIONES: Obtiene todos los chats de un usuario.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConversations(@PathParam("userId") String userId) {
        Optional<User> user = logic.getUserById(userId); // Método a implementar en logic
        if (!user.isPresent()) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity("Usuario no encontrado")
                           .build();
        }
        User usuario= user.get();
        return Response.ok(usuario.getConversations()).build(); //
    }

    /**
     * 2. INICIAR CHAT: Crea una nueva Conversation y envía el primer prompt.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createConversation(@PathParam("userId") String userId, Dialogue request) {
        // Generamos un ID único para la nueva charla
        String newId = java.util.UUID.randomUUID().toString();
        
        // Creamos la conversación en estado READY
        logic.crearNuevaConversacion(userId, newId); 
        
        // Enviamos el primer mensaje (Llamada gRPC)
        return processMessage(userId, newId, request.getPrompt());
    }

    /**
     * 3. CONTINUAR CHAT: Envía un mensaje a una charla existente.
     */
    @POST
    @Path("/{dialogueId}/next")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response nextStep(
            @PathParam("userId") String userId,
            @PathParam("dialogueId") String dialogueId,
            Dialogue request) {
        
        // Verificamos si la conversación existe y si está READY
        Conversation conv = logic.obtenerConversacion(userId, dialogueId);
        
        if (conv == null) return Response.status(Response.Status.NOT_FOUND).build();
        
        // Regla: Si no está READY, no se permite POST
        if (conv.getStatus() != StatusConversation.READY) {
            return Response.status(Response.Status.FORBIDDEN)
                           .entity("La conversación no está lista o ya ha finalizado.")
                           .build();
        }

        return processMessage(userId, dialogueId, request.getPrompt());
    }

    /**
     * Método auxiliar para evitar duplicar la lógica de envío y HATEOAS
     */
    private Response processMessage(String userId, String dialogueId, String prompt) {
        logic.enviarMensajeEIA(prompt, userId, dialogueId);

        Conversation conv = logic.obtenerConversacion(userId, dialogueId);
        if (conv == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity("Conversación no encontrada tras enviarMensajeEIA (no creada o no persistida)")
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }

        conv.setUrlNext("/u/" + userId + "/dialogue/" + dialogueId + "/next");
        conv.setUrlEnd("/u/" + userId + "/dialogue/" + dialogueId + "/end");

        return Response.ok(conv).build();
    }

    /**
     * 4. TERMINAR CHAT: Cambia el estado de la conversación a FINISHED.
     */
    @POST
    @Path("/{dialogueId}/end")
    @Produces(MediaType.APPLICATION_JSON)
    public Response endConversation(
            @PathParam("userId") String userId,
            @PathParam("dialogueId") String dialogueId) {
        
        boolean finished = logic.finalizarConversacion(userId, dialogueId);
        
        if (finished) {
            Conversation conv = logic.obtenerConversacion(userId, dialogueId);
            return Response.ok(conv).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    /**
     * 5. ELIMINAR CHAT: Borra el log de la base de datos.
     */
    @DELETE
    @Path("/{dialogueId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteConversation(
            @PathParam("userId") String userId,
            @PathParam("dialogueId") String dialogueId) {
        
        boolean deleted = logic.eliminarConversacion(userId, dialogueId);
        
        if (deleted) {
            return Response.ok("{\"status\":\"deleted\"}").build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    
    
}
