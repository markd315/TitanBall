package authserver.users.identities;

import authserver.models.User;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    User findByUsername(String username) throws DataAccessException;

    User findByEmail(String email) throws DataAccessException;

    @Query("SELECT u FROM User u WHERE u.username = :login OR u.email = :login")
    User findByUsernameOrEmail(@Param("login") String login);

    // JpaRepository already provides findById(), findAll(), save(), delete()
}