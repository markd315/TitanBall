package authserver.users;

import authserver.models.User;

public interface UserService {

    void saveUser(User user) throws Exception;
    void deleteUser(User user) throws Exception;
    User findUserByUsername(String username);
    User findUserByEmail(String username);

}
