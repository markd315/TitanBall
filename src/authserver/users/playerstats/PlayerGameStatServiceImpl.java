package authserver.users.playerstats;

import authserver.models.PlayerGameStat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlayerGameStatServiceImpl implements PlayerGameStatService {

    @Autowired
    private PlayerGameStatRepository playerGameStatRepository;

    @Override
    @Transactional
    public void save(PlayerGameStat stat) {
        playerGameStatRepository.save(stat);
    }

    @Override
    @Transactional
    public void saveAll(List<PlayerGameStat> stats) {
        playerGameStatRepository.saveAll(stats);
    }
}
