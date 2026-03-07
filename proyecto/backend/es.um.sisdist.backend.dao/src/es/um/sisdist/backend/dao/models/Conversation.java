package es.um.sisdist.backend.dao.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
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

    public void setUrlEnd(String urlend) {
        this.urlEnd = urlend;
    }

    public void setUrlNext(String urlnext) {
        this.urlNext = urlnext;
    }
}