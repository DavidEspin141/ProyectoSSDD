package es.um.sisdist.backend.grpc.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

import es.um.sisdist.backend.grpc.ChatRequest;
import es.um.sisdist.backend.grpc.ChatResponse;
import es.um.sisdist.backend.grpc.LLMServiceGrpc; 
import io.grpc.stub.StreamObserver;

class GrpcServiceImpl extends LLMServiceGrpc.LLMServiceImplBase 
{
	private Logger logger;
	private final HttpClient httpClient;
	
    public GrpcServiceImpl(Logger logger) 
    {
		super();
		this.logger = logger;
		// Inicializamos un cliente HTTP estándar de Java
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
	}
	
	//METODO PARA CONTESTAR A LAS CONSULTAS ENTRANTES DEL CLIENTE
	@Override
	public void processPrompt(ChatRequest request, StreamObserver<ChatResponse> responseObserver) {
	    //Extraemos los datos de la petición
	    String dialogueId = request.getDialogueId();
	    String prompt = request.getPrompt();
        
        String aiResponse = "Error en la comunicación con LlamaChat";
		try {
            String llamaHost = System.getenv().getOrDefault("LLAMACHAT_HOST", "ssdd-llamachat");
            String llamaPort = System.getenv().getOrDefault("LLAMACHAT_PORT", "5020");
            String baseUrl = "http://" + llamaHost + ":" + llamaPort;

            //Enviar petición POST inicial
            String jsonInput = "{\"prompt\": \"" + prompt.replace("\"", "\\\"") + "\"}";
            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/prompt"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonInput))
                    .build();

            HttpResponse<String> postRes = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString());

            // Comprobar si fue aceptada (202) y extraer la URL de la respuesta
            if (postRes.statusCode() == 202) {
                String location = postRes.headers().firstValue("Location").orElse(null);
                
                if (location != null) {
                    boolean isReady = false;
                    
                    //Bucle de Polling: Preguntar hasta que esté listo (200 OK)
                    while (!isReady) {
                        Thread.sleep(1000); // Esperar 1 segundo entre intentos para no saturar
                        
                        HttpRequest getReq = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + location))
                                .GET()
                                .build();
                        
                        HttpResponse<String> getRes = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString());
                        
                        if (getRes.statusCode() == 200) {
                            isReady = true;
                            // Extraer el campo "answer" del JSON de Python
                            String jsonBody = getRes.body();
                            aiResponse = extractAnswerFromJson(jsonBody);
                        } else if (getRes.statusCode() != 102 && getRes.statusCode() != 204) {
                            // Si da un error distinto a "Procesando" o "Inicializando", salimos
                            logger.warning("Error en el polling: " + getRes.statusCode());
                            break;
                        }
                    }
                }
            } else {
                logger.warning("La IA no aceptó el prompt. Código: " + postRes.statusCode());
            }

        } catch (Exception e) {
            logger.severe("Fallo de conexión: " + e.getMessage());
        }   

	    // Construimos el objeto de respuesta 
	    ChatResponse response = ChatResponse.newBuilder()
        	    .setDialogueId(dialogueId)          
        	    .setResponse(aiResponse)          
        	    .setTimestamp(System.currentTimeMillis()) // Marca de tiempo para el orden
        	    .build();

	    // Enviamos la respuesta al cliente 
	    responseObserver.onNext(response);
	    //Cerramos el flujo de comunicación 
	    responseObserver.onCompleted();
	}
	// Método auxiliar para extraer el 'answer' del JSON de Python 
	private String extractAnswerFromJson(String json) {
        try {
            String search = "\"answer\":";
            int startIndex = json.indexOf(search) + search.length();
            int firstQuote = json.indexOf("\"", startIndex) + 1;
            int lastQuote = json.lastIndexOf("\"");
            // Reemplazamos los saltos de línea escapados por saltos reales
            return json.substring(firstQuote, lastQuote).replace("\\n", "\n").replace("\\\"", "\"");
        } catch (Exception e) {
            return "Respuesta recibida pero no se pudo parsear el JSON: " + json;
        }
    }

}
