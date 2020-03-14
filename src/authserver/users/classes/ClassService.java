package authserver.users.classes;

import authserver.models.ClassStat;

public interface ClassService {
    ClassStat findStatsTrackerByRole(String className);
    void saveClass(ClassStat classStat);
}
