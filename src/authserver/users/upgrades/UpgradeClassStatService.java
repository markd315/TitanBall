package authserver.users.upgrades;

import authserver.models.UpgradeClassStat;

public interface UpgradeClassStatService {
    UpgradeClassStat findStatsTrackerByUpgrade(String upgrade);
    void saveUpgradeClassStat(UpgradeClassStat upgradeClassStat);
}
