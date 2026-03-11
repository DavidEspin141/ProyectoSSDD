
package es.um.sisdist.backend.Service.impl;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import es.um.sisdist.backend.dao.DAOFactoryImpl;
import es.um.sisdist.backend.dao.IDAOFactory;
import es.um.sisdist.backend.dao.models.Conversation;
import es.um.sisdist.backend.dao.models.Dialogue;
import es.um.sisdist.backend.dao.models.StatusConversation;
import es.um.sisdist.backend.dao.models.User;
import es.um.sisdist.backend.dao.models.utils.UserUtils;
import es.um.sisdist.backend.dao.user.IUserDAO;
import es.um.sisdist.backend.grpc.ChatRequest;
import es.um.sisdist.backend.grpc.ChatResponse;
import es.um.sisdist.backend.grpc.LLMServiceGrpc;
import es.um.sisdist.models.UserDTO;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;


public class AppLogicImpl
{
    IDAOFactory daoFactory;
    IUserDAO dao;

    private static final Logger logger = Logger.getLogger(AppLogicImpl.class.getName());

    private final ManagedChannel channel;
    private final LLMServiceGrpc.LLMServiceBlockingStub blockingStub;
    //private final GrpcServiceGrpc.GrpcServiceStub asyncStub;

    static AppLogicImpl instance = new AppLogicImpl();

    private AppLogicImpl()
    {
        daoFactory = new DAOFactoryImpl();
        Optional<String> backend = Optional.ofNullable(System.getenv("DB_BACKEND"));
        
        if (backend.isPresent() && backend.get().equals("mongo"))
            dao = daoFactory.createMongoUserDAO();
        else
            dao = daoFactory.createSQLUserDAO();

        var grpcServerName = Optional.ofNullable(System.getenv("GRPC_SERVER"));
        var grpcServerPort = Optional.ofNullable(System.getenv("GRPC_SERVER_PORT"));

        channel = ManagedChannelBuilder
                .forAddress(grpcServerName.orElse("localhost"), Integer.parseInt(grpcServerPort.orElse("50051")))
                // Channels are secure by default (via SSL/TLS). For the example we disable TLS
                // to avoid needing certificates.
                .usePlaintext().build();
        blockingStub = LLMServiceGrpc.newBlockingStub(channel);
        //asyncStub = GrpcServiceGrpc.newStub(channel);
    }

    public static AppLogicImpl getInstance()
    {
        return instance;
    }

    public Optional<User> getUserByEmail(String userId)
    {
        Optional<User> u = dao.getUserByEmail(userId);
        return u;
    }

    public Optional<User> getUserById(String userId)
    {
        return dao.getUserById(userId);
    }

    // El frontend, a través del formulario de login,
    // envía el usuario y pass, que se convierte a un DTO. De ahí
    // obtenemos la consulta a la base de datos, que nos retornará,
    // si procede,
    public Optional<User> checkLogin(String email, String pass)
    {
        Optional<User> u = dao.getUserByEmail(email);

        if (u.isPresent())
        {
            String hashed_pass = UserUtils.md5pass(pass);
            if (0 == hashed_pass.compareTo(u.get().getPassword_hash()))
                return u;
        }

        return Optional.empty();
    }
    
    
	public String pedirRespuestaIA(String prompt, String userId, String dialogueId) {
	    // Se puede usar el DAO para validar el token del usuario 
	    // User user = dao.getUserByToken(token); 
	
	    // 2. Construimos la petición gRPC usando el Builder generado 
	    ChatRequest request = ChatRequest.newBuilder()
	            .setUserId(userId)      // Campo 1: Identificador del usuario
	            .setDialogueId(dialogueId) // Campo 2: ID para mantener el hilo/contexto
        	    .setPrompt(prompt)      // Campo 3: El texto del usuario
        	    .build();
	
	    try {
	        // 3. Enviamos la petición y recibimos la respuesta de forma síncrona
	        ChatResponse response = blockingStub.processPrompt(request);
	        
	        System.out.println("Respuesta recibida a las: " + response.getTimestamp());
	        
	        // 4. Devolvemos el texto generado por la IA [cite: 1149]
	        return response.getResponse();
	    } catch (StatusRuntimeException e) {
	        return "Error al conectar con el motor de IA";
	    }
	}
	
	public Conversation crearNuevaConversacion(String userId, String newId) {
        // Buscamos al usuario en la base de datos
        Optional<User> userOpt = getUserById(userId);
    
        if (userOpt.isPresent()) {
        User user = userOpt.get();
        
            // Si la lista de conversaciones es nula, la inicializamos
            if (user.getConversations() == null) {
            user.setConversations(new java.util.ArrayList<>());
            }

            // Creamos la nueva instancia de Conversation
            // Usamos READY como estado inicial
            Conversation nuevaConv = new Conversation(
                newId, 
                StatusConversation.READY, 
                new java.util.ArrayList<>()
            );

            //Añadimos la conversación a la lista del usuario
            user.getConversations().add(nuevaConv);

        // Persistimos los cambios en la base de datos
        dao.updateConversations(userId, user.getConversations());
        return nuevaConv;
        }
    
        return null; // O lanzar una excepción si el usuario no existe
    }

    public Conversation obtenerConversacion(String userId, String dialogueId) {
        //Recuperamos al usuario de la base de datos/DAO
        Optional<User> userOpt = getUserById(userId);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            List<Conversation> listaConvs = user.getConversations();

            if (listaConvs != null) {
                // Buscamos en la lista la conversación que coincida con el ID
                return listaConvs.stream()
                    .filter(c -> c.getDialogueId().equals(dialogueId))
                    .findFirst()
                    .orElse(null); // Si no la encuentra, devuelve null
            }
        }
    
        // Si el usuario no existe o no tiene conversaciones
        return null;
    }

    public Dialogue enviarMensajeEIA(String prompt, String userId, String dialogueId) {
    Optional<User> userOpt = getUserById(userId);
    if (userOpt.isEmpty()) return null;

    User user = userOpt.get();
    if (user.getConversations() == null) return null;

    Conversation conv = user.getConversations().stream()
        .filter(c -> dialogueId.equals(c.getDialogueId()))
        .findFirst()
        .orElse(null);

    if (conv == null) return null;

    if (conv.getDialogue() == null) {
        conv.setDialogue(new java.util.ArrayList<>());
    }

    conv.setStatus(StatusConversation.BUSY);

    try {
        ChatRequest request = ChatRequest.newBuilder()
            .setUserId(userId)
            .setDialogueId(dialogueId)
            .setPrompt(prompt)
            .build();

        ChatResponse response = blockingStub.processPrompt(request);

        Dialogue nuevoMensaje = new Dialogue(prompt, response.getResponse(), response.getTimestamp());
        conv.getDialogue().add(nuevoMensaje);

        conv.setStatus(StatusConversation.READY);

        // Persistir EXACTAMENTE el user/conversations que acabas de modificar
        dao.updateConversations(userId, user.getConversations());

        return nuevoMensaje;

    } catch (StatusRuntimeException e) {
        conv.setStatus(StatusConversation.READY);
        logger.severe("Error llamando a gRPC: " + e.getStatus());

        Dialogue errMsg = new Dialogue(prompt, "ERROR GRPC", System.currentTimeMillis());
        conv.getDialogue().add(errMsg);

        dao.updateConversations(userId, user.getConversations());

        return errMsg;
    }
}
    // Toma el DTO, verifica duplicados, crea la entidad User y la guarda en BD.
    public Optional<User> registerUser(UserDTO dto) {
        // Verificamos si el email ya está registrado
        if (dao.getUserByEmail(dto.getEmail()).isPresent()) {
            return Optional.empty(); 
        }

        // Creamos el nuevo objeto User. 
        // El constructor genera un MD5 del password y un ID automáticamente.
        User newUser = new User(
            dto.getEmail(),
            UserUtils.md5pass(dto.getPassword()), 
            dto.getName(),
            "TOKEN_INICIAL"
        );

        // Lo guardamos en MySQL
        boolean success = dao.insertUser(newUser); 

        return success ? Optional.of(newUser) : Optional.empty();
    }
	
}
