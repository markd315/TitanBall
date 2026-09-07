package authserver.users.masteries;

import authserver.models.MasteriesStat;
import org.springframework.dao.DataAccessException;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface MasteriesStatRepository extends Repository<MasteriesStat, Integer> {
    void save(MasteriesStat masteriesStat) throws DataAccessException;
    void delete(MasteriesStat masteriesStat) throws DataAccessException;
    MasteriesStat findByRole(String role) throws DataAccessException;
    MasteriesStat findFirstByRole(String role) throws DataAccessException;
    Optional<MasteriesStat> findById(Long id);
}
