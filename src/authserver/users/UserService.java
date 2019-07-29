package authserver.users;

import authserver.models.User;
import gameserver.engine.StatEngine;

public interface UserService {

    void saveUser(User user) throws Exception;
    void deleteUser(User user) throws Exception;
    User findUserByUsername(String username);
    User findUserByEmail(String username);
    void postgameStats(String email, StatEngine stats, int wasVictorious, double newRating) throws Exception;
}
