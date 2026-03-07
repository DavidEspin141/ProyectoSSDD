package es.um.sisdist.backend.dao.user;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import es.um.sisdist.backend.dao.models.User;


public class SQLUserDAO implements IUserDAO
{
    Optional<Connection> conn;

    public SQLUserDAO()
    {
        // Generate an optional from a direct connection attempt

        Connection connection = null;
        try
        {
            Class.forName("com.mysql.cj.jdbc.Driver").getConstructor().newInstance();

            // Si el nombre del host se pasa por environment, se usa aquí.
            // Si no, se usa localhost. Esto permite configurarlo de forma
            // sencilla para cuando se ejecute en el contenedor, y a la vez
            // se pueden hacer pruebas locales
            String sqlServerName = Optional.ofNullable(System.getenv("SQL_SERVER")).orElse("localhost");
            String dbName = Optional.ofNullable(System.getenv("DB_NAME")).orElse("ssdd");
            connection = DriverManager.getConnection(
                "jdbc:mysql://" + sqlServerName + "/" + dbName + "?user=root&password=root");
        } catch (Exception e)
        {
            e.printStackTrace();
        }
        conn = Optional.ofNullable(connection);
    }


    @Override
    public Optional<User> getUserById(String id)
    {
        if (conn.isEmpty()) return Optional.empty();
        try {
            PreparedStatement stm = conn.get().prepareStatement("SELECT * FROM users WHERE id = ?");
            stm.setString(1, id);
            ResultSet result = stm.executeQuery();
            if (result.next()) return createUser(result);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    private Optional<User> createUser(ResultSet result) {
        try {
            String userId = result.getString("id");
            List<Conversation> conversations = getConversationsForUser(userId);
            
            return Optional.of(new User(
                    userId,
                    result.getString("email"),
                    result.getString("password_hash"),
                    result.getString("name"),
                    result.getString("token"),
                    conversations));
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> getUserByEmail(String id)
    {
        Optional<PreparedStatement> stm;
        if (conn.isEmpty())
            return Optional.empty();

        try
        {
            stm = Optional.ofNullable(conn.get().prepareStatement("SELECT * from users WHERE email = ?"));
            if (stm.isEmpty())
                return Optional.empty();
            stm.get().setString(1, id);
            ResultSet result = stm.get().executeQuery();
            if (result.next())
                return createUser(result);
        } catch (SQLException e)
        {
            // Fallthrough
        }
        return Optional.empty();
    }
    // Método auxiliar para obtener las conversaciones del usuario
    private List<Conversation> getConversationsForUser(String userId) throws SQLException {
        List<Conversation> conversaciones = new ArrayList<>();
        PreparedStatement stm = conn.get().prepareStatement("SELECT * FROM conversations WHERE user_id = ?");
        stm.setString(1, userId);
        ResultSet rs = stm.executeQuery();
        
        while (rs.next()) {
            String dialogueId = rs.getString("dialogue_id");
            StatusConversation status = StatusConversation.valueOf(rs.getString("status"));
            List<Dialogue> dialogos = getDialoguesForConversation(dialogueId);
            
            conversaciones.add(new Conversation(dialogueId, status, dialogos));
        }
        return conversaciones;
    }
    // Método auxiliar para obtener los mensajes de una conversación
    private List<Dialogue> getDialoguesForConversation(String dialogueId) throws SQLException {
        List<Dialogue> dialogos = new ArrayList<>();
        PreparedStatement stm = conn.get().prepareStatement("SELECT * FROM dialogues WHERE dialogue_id = ? ORDER BY timestamp ASC");
        stm.setString(1, dialogueId);
        ResultSet rs = stm.executeQuery();
        
        while (rs.next()) {
            dialogos.add(new Dialogue(
                rs.getString("prompt"), 
                rs.getString("response"),
                rs.getLong("timestamp")
            ));
        }
        return dialogos;
    }

    @Override
    public void updateConversations(String userId, List<Conversation> conversations) {
        if (conn.isEmpty()) return;

        try {
            for (Conversation conv : conversations) {
                // 1. Insertar o actualizar conversación
                String sqlConv = "INSERT INTO conversations (dialogue_id, user_id, status) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE status = ?";
                PreparedStatement psConv = conn.get().prepareStatement(sqlConv);
                psConv.setString(1, conv.getDialogue_id());
                psConv.setString(2, userId);
                psConv.setString(3, conv.getStatus().name());
                psConv.setString(4, conv.getStatus().name());
                psConv.executeUpdate();

                // 2. Limpiar mensajes antiguos para evitar duplicados
                PreparedStatement psDel = conn.get().prepareStatement("DELETE FROM dialogues WHERE dialogue_id = ?");
                psDel.setString(1, conv.getDialogue_id());
                psDel.executeUpdate();

                // 3. Insertar historial de mensajes actualizado
                String sqlDiag = "INSERT INTO dialogues (dialogue_id, prompt, response, timestamp) VALUES (?, ?, ?, ?)";
                for (Dialogue d : conv.getDialogue()) {
                    PreparedStatement psDiag = conn.get().prepareStatement(sqlDiag);
                    psDiag.setString(1, conv.getDialogue_id());
                    psDiag.setString(2, d.getPrompt());
                    psDiag.setString(3, d.getResponse()); 
                    psDiag.setLong(4, d.getTimestamp());
                    psDiag.executeUpdate();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
