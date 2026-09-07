package authserver.users.masteries;

import authserver.models.MasteriesStat;

public interface MasteriesStatService {
    MasteriesStat findStatsTrackerByRole(String role);
    void saveMasteriesStat(MasteriesStat masteriesStat);
}
