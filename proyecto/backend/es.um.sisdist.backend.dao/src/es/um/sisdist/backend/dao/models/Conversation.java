package es.um.sisdist.backend.dao.models;

import es.um.sisdist.backend.dao.models.StatusConversation;
import es.um.sisdist.backend.dao.models.Dialogue;

import java.util.List;
import java.util.ArrayList;
import java.io.Serializable;
public class Conversation implements Serializable {

    private String dialogue_id;
    private StatusConversation status; // Estado de la conversación 
    private List<Dialogue> dialogue; // Conjunto de mensajes que forman la conversación

    private String urlNext; // url para continuar la conversación
    private String urlEnd; // url para finalizar la conversación

    public Conversation(String dialogue_id, StatusConversation status, List<Dialogue> dialogues)
    {
        this.dialogue_id = dialogue_id;
        this.status = StatusConversation.READY;
        this.dialogue = new ArrayList<>(dialogues);
    }

    public Conversation()
    {
    }

    public String getDialogue_id()
    {
        return dialogue_id;
    }

    public void setDialogue_id(String dialogue_id)
    {
        this.dialogue_id = dialogue_id;
    }

    public StatusConversation getStatus()
    {
        return status;
    }

    public void setStatus(StatusConversation status)
    {
        this.status = status;
    }

    public List<Dialogue> getDialogue()
    {
        return dialogue;
    }

    public void setDialogue(List<Dialogue> dialogue)
    {
        this.dialogue = new ArrayList<>(dialogue);
    }
}