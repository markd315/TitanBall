package authserver.users.builds;

import authserver.models.BuildOrderStat;
import org.springframework.dao.DataAccessException;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface BuildOrderStatRepository extends Repository<BuildOrderStat, Integer> {
    void save(BuildOrderStat buildOrderStat) throws DataAccessException;
    void delete(BuildOrderStat buildOrderStat) throws DataAccessException;
    BuildOrderStat findByBuildname(String buildname) throws DataAccessException;
    BuildOrderStat findFirstByBuildname(String buildname) throws DataAccessException;
    Optional<BuildOrderStat> findById(Long id);
}
