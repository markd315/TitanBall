package authserver.users;

import authserver.models.User;
import org.springframework.dao.DataAccessException;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface UserRepository extends Repository<User, Integer> {
    void save(User user) throws DataAccessException;
    void delete(User user) throws DataAccessException;
    User findByUsername(String username) throws DataAccessException;
    User findByEmail(String email) throws DataAccessException;
    Optional<User> findByUsernameOrEmail(String usernameOrEmail, String usernameOrEmail1);
    Optional<User> findById(Long id);
}

