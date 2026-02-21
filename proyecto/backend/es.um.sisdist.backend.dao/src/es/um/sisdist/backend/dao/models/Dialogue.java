package es.um.sisdist.backend.dao.models;

import java.io.Serializable;


public class Dialogue implements Serializable {
    
    private String prompt; // Pregunta del usuario
    private String response; // Respuesta del sistema
    private long timestamp; // Marca de tiempo del diálogo

    public Dialogue(String prompt, String response, long timestamp)
    {
        this.prompt = prompt;
        this.response = response;
        this.timestamp = timestamp;
    }

    public Dialogue()
    {
    }

    public String getPrompt()
    {
        return prompt;
    }

    public void setPrompt(String prompt)
    {
        this.prompt = prompt;
    }

    public String getResponse()
    {
        return response;
    }

    public void setResponse(String response)
    {
        this.response = response;
    }

    public long getTimestamp()
    {
        return timestamp;
    }

    public void setTimestamp(long timestamp)
    {
        this.timestamp = timestamp;
    }

}