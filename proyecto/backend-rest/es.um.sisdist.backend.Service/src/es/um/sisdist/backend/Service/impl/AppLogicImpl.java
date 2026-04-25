
package es.um.sisdist.backend.Service.impl;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
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
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import io.prometheus.client.Counter;


public class AppLogicImpl{
    IDAOFactory daoFactory;
    IUserDAO dao;

    private static final Logger logger = Logger.getLogger(AppLogicImpl.class.getName());

    private final ManagedChannel channel;
    private final LLMServiceGrpc.LLMServiceBlockingStub blockingStub;

    static AppLogicImpl instance = new AppLogicImpl();
    //Clave con la que se cifrará los tokens JWT. 
    public static final String SECRET_KEY = "LlamaChatSecretKeyParaSistemasDistribuidos2026!!!";
    // Métrica personalizada para contar número de mensajes enviados a la IA
    private static final Counter mensajesIA = Counter.build()
                            .name("llamachat_mensajes_ia_total")
                            .help("Número total de mensajes enviados al motor de IA por los usuarios")
                            .register();
    
    private AppLogicImpl(){
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
    }

    public static AppLogicImpl getInstance(){
        return instance;
    }

    public Optional<User> getUserByEmail(String userId){
        Optional<User> u = dao.getUserByEmail(userId);
        return u;
    }

    public Optional<User> getUserById(String userId){
        return dao.getUserById(userId);
    }

    // El frontend, a través del formulario de login,
    // envía el usuario y pass, que se convierte a un DTO. De ahí
    // obtenemos la consulta a la base de datos, que nos retornará,
    // si procede,
    public Optional<User> checkLogin(String email, String pass){
        Optional<User> u = dao.getUserByEmail(email);

        if (u.isPresent())
        {
            String hashed_pass = UserUtils.md5pass(pass);
            if (0 == hashed_pass.compareTo(u.get().getPassword_hash())){
                String tokenReal = generarJWT(u.get());
                u.get().setToken(tokenReal);
                return u;
            }
        }

        return Optional.empty();
    }
    
    
	public String pedirRespuestaIA(String prompt, String userId, String dialogueId) {
	
	    //Construimos la petición gRPC usando el Builder generado 
	    ChatRequest request = ChatRequest.newBuilder()
	            .setUserId(userId)      //Identificador del usuario
	            .setDialogueId(dialogueId) // ID para mantener el hilo/contexto
        	    .setPrompt(prompt)      // El texto del usuario
        	    .build();
	
	    try {
	        //Enviamos la petición y recibimos la respuesta de forma síncrona
	        ChatResponse response = blockingStub.processPrompt(request);
	        
	        System.out.println("Respuesta recibida a las: " + response.getTimestamp());
	        
	        //Devolvemos el texto generado por la IA
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
    
        return null; //Lanzar una excepción si el usuario no existe
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

    public Dialogue enviarMensajeEIA(String prompt, String userId, String dialogueId, String token) {
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

            //Incrementamos contador para la métrica
            mensajesIA.inc();
            //Creamos los metadatos y metemos el token con el formato "Bearer <token>"
            Metadata metadata = new Metadata();
            metadata.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
            //Creamos un nuevo stub que intercepta la llamada para pegarle los metadatos
            LLMServiceGrpc.LLMServiceBlockingStub stubConAuth = 
                blockingStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));


            ChatResponse response = stubConAuth.processPrompt(request);

            Dialogue nuevoMensaje = new Dialogue(prompt, response.getResponse(), response.getTimestamp());
            conv.getDialogue().add(nuevoMensaje);

            conv.setStatus(StatusConversation.READY);
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
            "" // El token se generará al hacer login, no al registrar
        );

        // Lo guardamos en MySQL
        boolean success = dao.insertUser(newUser); 

        return success ? Optional.of(newUser) : Optional.empty();
    }

    /**
     * Finaliza una conversación cambiando su estado a FINISHED.
     * Así evitamos que se sigan enviando mensajes a este chat.
     */
    public boolean finalizarConversacion(String userId, String dialogueId) {
        Optional<User> userOpt = getUserById(userId);
        if (userOpt.isEmpty()) return false;

        User user = userOpt.get();
        if (user.getConversations() == null) return false;

        // Buscamos la conversación
        Conversation conv = user.getConversations().stream()
            .filter(c -> dialogueId.equals(c.getDialogueId()))
            .findFirst()
            .orElse(null);

        if (conv == null) return false;

        // Cambiamos el estado y persistimos en la base de datos
        conv.setStatus(StatusConversation.FINISHED);
        dao.updateConversations(userId, user.getConversations());
        return true;
    }

    
    // Elimina una conversación de la lista del usuario y actualiza la BD.
    public boolean eliminarConversacion(String userId, String dialogueId) {
        Optional<User> userOpt = getUserById(userId);
        if (userOpt.isEmpty()) return false;

        User user = userOpt.get();
        if (user.getConversations() != null) {
            // Lo quitamos de la memoria temporal para mantener la coherencia
            user.getConversations().removeIf(c -> dialogueId.equals(c.getDialogueId()));
        }

        // Ejecutamos la orden de destrucción masiva en MySQL
        return dao.deleteConversation(dialogueId);
    }

    private String generarJWT(User user) {
        long tiempoExpiracion = 1000 * 60 * 60 * 24; // 24 horas en milisegundos

        return Jwts.builder()
                .setSubject(user.getEmail())                  // A quién pertenece
                .claim("userId", user.getId())                // Guardamos el ID dentro del token
                .claim("name", user.getName())                // Guardamos el nombre
                .setIssuedAt(new Date())                      // Fecha de creación
                .setExpiration(new Date(System.currentTimeMillis() + tiempoExpiracion)) // Caducidad
                .signWith(Keys.hmacShaKeyFor(SECRET_KEY.getBytes()), SignatureAlgorithm.HS256) // Firma
                .compact();
    }
	
}
