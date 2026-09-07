package authserver.users.builds;

import authserver.models.BuildOrderStat;

public interface BuildOrderStatService {
    BuildOrderStat findStatsTrackerByBuildName(String buildname);
    void saveBuildOrderStat(BuildOrderStat buildOrderStat);
}
