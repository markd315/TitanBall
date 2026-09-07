package authserver.users.upgrades;

import authserver.models.UpgradeClassStat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpgradeClassStatServiceImpl implements UpgradeClassStatService {
    @Autowired
    UpgradeClassStatRepository upgradeClassStatRepository;

    @Override
    @Transactional(readOnly = true)
    public UpgradeClassStat findStatsTrackerByUpgrade(String upgrade) {
        UpgradeClassStat stat = null;
        try {
            stat = upgradeClassStatRepository.findFirstByUpgrade(upgrade);
            if (stat == null) {
                stat = upgradeClassStatRepository.findByUpgrade(upgrade);
            }
        } catch (Exception e) {
            return null;
        }
        return stat;
    }

    @Override
    @Transactional
    public void saveUpgradeClassStat(UpgradeClassStat upgradeClassStat) {
        upgradeClassStatRepository.save(upgradeClassStat);
    }
}
