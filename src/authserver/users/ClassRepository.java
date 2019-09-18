package authserver.users;

import authserver.models.ClassStat;
import org.springframework.dao.DataAccessException;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface ClassRepository extends Repository<ClassStat, Integer> {
    void save(ClassStat classStats) throws DataAccessException;
    void delete(ClassStat classStats) throws DataAccessException;
    ClassStat findByRole(String className) throws DataAccessException;
    Optional<ClassStat> findById(Long id);
}
