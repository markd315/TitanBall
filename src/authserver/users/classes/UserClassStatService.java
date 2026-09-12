package authserver.users.classes;

import authserver.models.UserClassStat;
import java.util.List;

public interface UserClassStatService {
    UserClassStat findByUserAndRole(String emailOrUsername, String role);
    List<UserClassStat> findAllByUser(String emailOrUsername);
    void save(UserClassStat userClassStat);
    void delete(UserClassStat userClassStat);
}
