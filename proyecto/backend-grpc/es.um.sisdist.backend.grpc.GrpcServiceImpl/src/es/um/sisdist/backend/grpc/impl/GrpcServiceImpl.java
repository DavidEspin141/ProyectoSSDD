package es.um.sisdist.backend.grpc.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.um.sisdist.backend.grpc.ChatRequest;
import es.um.sisdist.backend.grpc.ChatResponse;
import es.um.sisdist.backend.grpc.LLMServiceGrpc;
import io.grpc.stub.StreamObserver;

class GrpcServiceImpl extends LLMServiceGrpc.LLMServiceImplBase {

    private Logger logger;
    private final HttpClient httpClient;

    public GrpcServiceImpl(Logger logger) {
        super();
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void processPrompt(ChatRequest request, StreamObserver<ChatResponse> responseObserver) {
        String dialogueId = request.getDialogueId();
        String prompt = request.getPrompt();

        // Inicializamos con un mensaje de error por si algo falla
        String aiResponse = "Error en la comunicación con LlamaChat";

        try {
            String llamaHost = System.getenv().getOrDefault("LLAMACHAT_HOST", "ssdd-llamachat");
            String llamaPort = System.getenv().getOrDefault("LLAMACHAT_PORT", "5020");
            String baseUrl = "http://" + llamaHost + ":" + llamaPort;

            // 1. Limpieza del prompt para evitar romper el formato JSON
            String cleanPrompt = prompt.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");

            String jsonInput = String.format("{\"prompt\": \"%s\"}", cleanPrompt);

            // 2. Petición POST inicial
            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/prompt"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "*/*")
                    .header("User-Agent", "curl/8.5.0")
                    .header("Expect", "") // Requiere JAVA_TOOL_OPTIONS en docker-compose
                    .POST(HttpRequest.BodyPublishers.ofString(jsonInput))
                    .build();

            HttpResponse<String> postRes = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString());

            if (postRes.statusCode() == 202) {
                String location = postRes.headers().firstValue("Location").orElse(null);

                if (location != null) {
                    boolean isReady = false;

                    // 3. Bucle de Polling con gestión de excepciones para el código 102
                    while (!isReady) {
                        Thread.sleep(1000); // Esperar entre intentos

                        HttpRequest getReq = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + location))
                                .header("Accept", "application/json")
                                .header("User-Agent", "curl/8.5.0")
                                .GET()
                                .build();

                        try {
                            HttpResponse<String> getRes = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString());
                            int status = getRes.statusCode();

                            if (status == 200) {
                                isReady = true;
                                aiResponse = extractAnswerFromJson(getRes.body());
                                logger.info("¡Éxito! Respuesta de IA recibida.");
                            } else if (status == 102 || status == 204) {
                                logger.info("La IA sigue procesando (Status " + status + ")...");
                            } else {
                                logger.warning("Error inesperado en el polling: " + status);
                                aiResponse = "La IA devolvió un error (Código " + status + ")";
                                isReady = true;
                            }
                        } catch (java.io.IOException e) {
                            //Capturamos el cuelgue del servidor Python
                            if (e.getMessage().contains("received no bytes")) {
                                logger.info("IA ocupada (Cierre de conexión por código 102). Reintentando...");
                                // No hacemos nada, el bucle volverá a preguntar en 1 segundo
                            } else {
                                // Si es un error de red distinto, lo lanzamos fuera
                                throw e;
                            }
                        }
                    }
                }
            } else {
                aiResponse = "La IA no aceptó el prompt inicial. Código: " + postRes.statusCode();
                logger.warning(aiResponse);
            }

        } catch (Exception e) {
            aiResponse = "Excepción detectada en Java: " + e.getMessage();
            logger.severe("Fallo de conexión: " + e.toString());
        }

        // 4. Construcción y envío de la respuesta gRPC
        ChatResponse response = ChatResponse.newBuilder()
                .setDialogueId(dialogueId)
                .setResponse(aiResponse)
                .setTimestamp(System.currentTimeMillis())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String extractAnswerFromJson(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode answer = root.get("answer");
            if (answer == null || answer.isNull()) {
                return "Respuesta sin campo 'answer': " + json;
            }
            return answer.asText(); 
        } catch (Exception e) {
            return "Error al parsear JSON de la IA: " + e.getMessage() + " | raw=" + json;
        }
    }
}
