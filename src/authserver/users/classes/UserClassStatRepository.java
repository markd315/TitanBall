package authserver.users.classes;

import authserver.models.UserClassStat;
import org.springframework.dao.DataAccessException;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface UserClassStatRepository extends Repository<UserClassStat, Integer> {
    void save(UserClassStat userClassStat) throws DataAccessException;
    void delete(UserClassStat userClassStat) throws DataAccessException;
    Optional<UserClassStat> findById(Integer id);
    UserClassStat findByEmailAndRole(String email, String role) throws DataAccessException;
    UserClassStat findFirstByEmailAndRole(String email, String role) throws DataAccessException;
    UserClassStat findByUsernameAndRole(String username, String role) throws DataAccessException;
    UserClassStat findFirstByUsernameAndRole(String username, String role) throws DataAccessException;
    List<UserClassStat> findByEmail(String email) throws DataAccessException;
    List<UserClassStat> findByUsername(String username) throws DataAccessException;
    List<UserClassStat> findAll() throws DataAccessException;
}
