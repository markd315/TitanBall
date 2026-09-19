package authserver.users.playerstats;

import authserver.models.PlayerGameStat;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerGameStatRepository extends CrudRepository<PlayerGameStat, Long> {
}
