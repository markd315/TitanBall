package authserver.users.premades;

import authserver.models.Premade;
import org.springframework.dao.DataAccessException;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface PremadeRepository extends Repository<Premade, Integer> {
    void save(Premade premade) throws DataAccessException;
    void delete(Premade premade) throws DataAccessException;
    Optional<Premade> findByTeamname(String teamname) throws DataAccessException;
    List<Premade> findAll();
}
