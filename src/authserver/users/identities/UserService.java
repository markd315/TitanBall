package authserver.users.identities;

import authserver.models.User;

import java.util.List;

public interface UserService {

    void saveUser(User user) throws Exception;
    void deleteUser(User user) throws Exception;
    User findUserByUsername(String username);
    User findUserByEmail(String username);
    List<User> findAll();
}
