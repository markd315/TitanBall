package authserver.users;

import authserver.models.User;
import authserver.users.identities.UserRepository;
import authserver.users.identities.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService, UserService {

    @Autowired
    UserRepository userRepository;

    @Autowired
    ClassRepository classRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String usernameOrEmail)
            throws UsernameNotFoundException {
        // Let people login with either email or email
        User user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found with email or email : " + usernameOrEmail)
                );

        return user;
    }

    // This method is used by JWTAuthenticationFilter
    @Transactional
    public UserDetails loadUserById(Long id) {
        User user = userRepository.findById(id).orElseThrow(
                () -> new UsernameNotFoundException("User not found with id : " + id)
        );
        return user;
    }

    @Override
    @Transactional
    public void saveUser(User user) throws Exception {
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteUser(User user){
        userRepository.delete(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User findUserByUsername(String username) throws DataAccessException {
        User user;
        try {
            user = userRepository.findByUsername(username);
        } catch (ObjectRetrievalFailureException | EmptyResultDataAccessException e) {
            // just ignore not found exceptions for Jdbc/Jpa realization
            return null;
        }
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public User findUserByEmail(String email) throws DataAccessException {
        User user;
        try {
            user = userRepository.findByEmail(email);
        } catch (ObjectRetrievalFailureException | EmptyResultDataAccessException e) {
            // just ignore not found exceptions for Jdbc/Jpa realization
            return null;
        }
        return user;
    }

    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }

}