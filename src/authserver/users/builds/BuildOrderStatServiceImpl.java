package authserver.users.builds;

import authserver.models.BuildOrderStat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuildOrderStatServiceImpl implements BuildOrderStatService {
    @Autowired
    BuildOrderStatRepository buildOrderStatRepository;

    @Override
    @Transactional(readOnly = true)
    public BuildOrderStat findStatsTrackerByBuildName(String buildname) {
        BuildOrderStat stat = null;
        try {
            stat = buildOrderStatRepository.findFirstByBuildname(buildname);
            if (stat == null) {
                stat = buildOrderStatRepository.findByBuildname(buildname);
            }
        } catch (Exception e) {
            return null;
        }
        return stat;
    }

    @Override
    @Transactional
    public void saveBuildOrderStat(BuildOrderStat buildOrderStat) {
        buildOrderStatRepository.save(buildOrderStat);
    }
}
