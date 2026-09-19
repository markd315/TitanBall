package authserver.users.playerstats;

import authserver.models.PlayerGameStat;
import java.util.List;

public interface PlayerGameStatService {
    void save(PlayerGameStat stat);
    void saveAll(List<PlayerGameStat> stats);
}
