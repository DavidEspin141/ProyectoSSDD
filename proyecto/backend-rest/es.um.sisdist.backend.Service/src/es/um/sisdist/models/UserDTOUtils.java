/**
 *
 */
package es.um.sisdist.models;

import es.um.sisdist.backend.dao.models.User;

/**
 * @author dsevilla
 *
 */
public class UserDTOUtils
{
    // Convierte de la entidad de Base de Datos (User) al objeto de la API (UserDTO)
    public static UserDTO toDTO(User u) {
        if (u == null) return null;
        return new UserDTO(
            u.getId(),
            u.getEmail(),
            null, // No enviamos el hash del password por seguridad
            u.getName(),
            u.getToken()
        );
    }

    // Convierte del objeto de la API (UserDTO) a la entidad de Base de Datos (User)
    public static User fromDTO(UserDTO dto) {
        if (dto == null) return null;
        // Usamos el constructor que incluye la lista de conversaciones vacía
        return new User(
            dto.getEmail(),
            dto.getPassword(),
            dto.getName(),
            dto.getToken()
        );
    }
}
