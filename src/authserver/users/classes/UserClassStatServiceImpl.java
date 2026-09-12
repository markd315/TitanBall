package authserver.users.classes;

import authserver.models.UserClassStat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserClassStatServiceImpl implements UserClassStatService {

    @Autowired
    UserClassStatRepository userClassStatRepository;

    @Override
    @Transactional(readOnly = true)
    public UserClassStat findByUserAndRole(String emailOrUsername, String role) {
        if (emailOrUsername == null || role == null) return null;
        String trimmedUser = emailOrUsername.trim();
        String trimmedRole = role.trim();
        try {
            UserClassStat stat = userClassStatRepository.findFirstByEmailAndRole(trimmedUser, trimmedRole);
            if (stat == null) {
                stat = userClassStatRepository.findByEmailAndRole(trimmedUser, trimmedRole);
            }
            if (stat == null) {
                stat = userClassStatRepository.findFirstByUsernameAndRole(trimmedUser, trimmedRole);
            }
            if (stat == null) {
                stat = userClassStatRepository.findByUsernameAndRole(trimmedUser, trimmedRole);
            }
            return stat;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserClassStat> findAllByUser(String emailOrUsername) {
        if (emailOrUsername == null) return new ArrayList<>();
        String trimmedUser = emailOrUsername.trim();
        try {
            List<UserClassStat> list = userClassStatRepository.findByEmail(trimmedUser);
            if (list == null || list.isEmpty()) {
                list = userClassStatRepository.findByUsername(trimmedUser);
            }
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    @Transactional
    public void save(UserClassStat userClassStat) {
        userClassStatRepository.save(userClassStat);
    }

    @Override
    @Transactional
    public void delete(UserClassStat userClassStat) {
        userClassStatRepository.delete(userClassStat);
    }
}
