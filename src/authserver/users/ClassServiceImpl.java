package authserver.users;

import authserver.models.ClassStat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassServiceImpl implements ClassService {
    @Autowired
    ClassRepository classRepository;

    @Override
    @Transactional(readOnly = true)
    public ClassStat findStatsTrackerByRole(String className) {
        ClassStat classStats;
        try {
            classStats = classRepository.findByRole(className);
        } catch (ObjectRetrievalFailureException | EmptyResultDataAccessException e) {
            // just ignore not found exceptions for Jdbc/Jpa realization
            return null;
        }
        return classStats;
    }

    @Override
    @Transactional
    public void saveClass(ClassStat classStat) {
        classRepository.save(classStat);
    }
}
