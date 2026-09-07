package authserver.users.upgrades;

import authserver.models.UpgradeClassStat;
import org.springframework.dao.DataAccessException;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface UpgradeClassStatRepository extends Repository<UpgradeClassStat, Integer> {
    void save(UpgradeClassStat upgradeClassStat) throws DataAccessException;
    void delete(UpgradeClassStat upgradeClassStat) throws DataAccessException;
    UpgradeClassStat findByUpgrade(String upgrade) throws DataAccessException;
    UpgradeClassStat findFirstByUpgrade(String upgrade) throws DataAccessException;
    Optional<UpgradeClassStat> findById(Long id);
}
