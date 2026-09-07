package authserver.users.masteries;

import authserver.models.MasteriesStat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasteriesStatServiceImpl implements MasteriesStatService {
    @Autowired
    MasteriesStatRepository masteriesStatRepository;

    @Override
    @Transactional(readOnly = true)
    public MasteriesStat findStatsTrackerByRole(String role) {
        MasteriesStat stat = null;
        try {
            stat = masteriesStatRepository.findFirstByRole(role);
            if (stat == null) {
                stat = masteriesStatRepository.findByRole(role);
            }
        } catch (Exception e) {
            return null;
        }
        return stat;
    }

    @Override
    @Transactional
    public void saveMasteriesStat(MasteriesStat masteriesStat) {
        masteriesStatRepository.save(masteriesStat);
    }
}
