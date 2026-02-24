package es.um.sisdist.backend.grpc.impl;

import java.util.logging.Logger;

import es.um.sisdist.backend.grpc.GrpcServiceGrpc;
import es.um.sisdist.backend.grpc.PingRequest;
import es.um.sisdist.backend.grpc.PingResponse;
import io.grpc.stub.StreamObserver;

class GrpcServiceImpl extends GrpcServiceGrpc.GrpcServiceImplBase 
{
	private Logger logger;
	
    public GrpcServiceImpl(Logger logger) 
    {
		super();
		this.logger = logger;
	}

	@Override
	public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) 
	{
		logger.info("Recived PING request, value = " + request.getV());
		responseObserver.onNext(PingResponse.newBuilder().setV(request.getV()).build());
		responseObserver.onCompleted();
	}
	
	//NUEVO: METODO PARA CONTESTAR A LAS CONSULTAS ENTRANTES DEL CLIENTE
	@Override
	public void getChatStream(ChatRequest request, StreamObserver<ChatResponse> responseObserver) {
	    // 1. Extraemos los datos de la petición
	    String dialogueId = request.getDialogueId();
	    String prompt = request.getPrompt();
    
	    // 2. Aquí iría la lógica de tu "cerebro" o modelo de lenguaje (el análisis)
	    String textoAnalizado = "He analizado tu mensaje: '" + prompt + "'. Aquí tienes mi respuesta...";

	    // 3. Construimos el objeto de respuesta siguiendo tu estructura .proto
	    // El tipo 'long' en proto3 mapea directamente a 'long' en Java [cite: 951]
	    ChatResponse response = ChatResponse.newBuilder()
        	    .setDialogueId(dialogueId)          // Campo 1: Confirmamos la sesión
        	    .setAnswer(textoAnalizado)          // Campo 2: La respuesta generada
        	    .setTimestamp(System.currentTimeMillis()) // Campo 3: Marca de tiempo para el orden
        	    .build();

	    // 4. Enviamos la respuesta al cliente [cite: 1313, 1324]
	    responseObserver.onNext(response);

	    // 5. Cerramos el flujo de comunicación [cite: 1313, 1324]
	    responseObserver.onCompleted();
	}


/*
	@Override
	public void storeImage(ImageData request, StreamObserver<Empty> responseObserver)
    {
		logger.info("Add image " + request.getId());
    	imageMap.put(request.getId(),request);
    	responseObserver.onNext(Empty.newBuilder().build());
    	responseObserver.onCompleted();
	}

	@Override
	public StreamObserver<ImageData> storeImages(StreamObserver<Empty> responseObserver) 
	{
		// La respuesta, sólo un objeto Empty
		responseObserver.onNext(Empty.newBuilder().build());

		// Se retorna un objeto que, al ser llamado en onNext() con cada
		// elemento enviado por el cliente, reacciona correctamente
		return new StreamObserver<ImageData>() {
			@Override
			public void onCompleted() {
				// Terminar la respuesta.
				responseObserver.onCompleted();
			}
			@Override
			public void onError(Throwable arg0) {
			}
			@Override
			public void onNext(ImageData imagedata) 
			{
				logger.info("Add image (multiple) " + imagedata.getId());
		    	imageMap.put(imagedata.getId(), imagedata);	
			}
		};
	}

	@Override
	public void obtainImage(ImageSpec request, StreamObserver<ImageData> responseObserver) {
		// TODO Auto-generated method stub
		super.obtainImage(request, responseObserver);
	}

	@Override
	public StreamObserver<ImageSpec> obtainCollage(StreamObserver<ImageData> responseObserver) {
		// TODO Auto-generated method stub
		return super.obtainCollage(responseObserver);
	}
	*/
}
