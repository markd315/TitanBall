package authserver.users;

import authserver.models.User;
import gameserver.engine.StatEngine;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService, UserService {

    @Autowired
    UserRepository userRepository;

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
    public void postgameStats(String email, StatEngine stats, int wasVictorious, double newRating) throws Exception {
        User user = findUserByEmail(email);
        JSONObject toAdd = stats.statsOf(email);
        if(wasVictorious == 1){
            user.setWins(user.getWins() + 1);
        }else{
            user.setLosses(user.getLosses() + 1);
        }
        user.setRating(newRating);
        if(toAdd.has(StatEngine.StatEnum.GOALS.toString())){
            user.setGoals((int) (user.getGoals() + (double) toAdd.get(StatEngine.StatEnum.GOALS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.SIDEGOALS.toString())){
            user.setSidegoals((int) (user.getSidegoals() +(double)  toAdd.get(StatEngine.StatEnum.SIDEGOALS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.POINTS.toString())){
            user.setPoints(user.getPoints() + (double) toAdd.get(StatEngine.StatEnum.POINTS.toString()));
        }
        if(toAdd.has(StatEngine.StatEnum.STEALS.toString())){
            user.setSteals((int) (user.getSteals() + (double)  toAdd.get(StatEngine.StatEnum.STEALS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.BLOCKS.toString())){
            user.setBlocks((int) (user.getBlocks() + (double) toAdd.get(StatEngine.StatEnum.BLOCKS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.PASSES.toString())){
            user.setPasses((int) (user.getPasses() +(double)  toAdd.get(StatEngine.StatEnum.PASSES.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.KILLS.toString())){
            user.setKills((int) (user.getKills() + (double) toAdd.get(StatEngine.StatEnum.KILLS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.DEATHS.toString())){
            user.setDeaths((int) (user.getDeaths() +(double) toAdd.get(StatEngine.StatEnum.DEATHS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.TURNOVERS.toString())){
            user.setTurnovers((int) (user.getTurnovers() +(double)  toAdd.get(StatEngine.StatEnum.TURNOVERS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.KILLASSISTS.toString())){
            user.setKillassists((int) (user.getKillassists() +(double)  toAdd.get(StatEngine.StatEnum.KILLASSISTS.toString())));
        }
        if(toAdd.has(StatEngine.StatEnum.GOALASSISTS.toString())){
            user.setGoalassists((int) (user.getGoalassists() +(double)  toAdd.get(StatEngine.StatEnum.GOALASSISTS.toString())));
        }
        saveUser(user);
    }
}