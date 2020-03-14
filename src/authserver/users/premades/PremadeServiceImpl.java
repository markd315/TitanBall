package authserver.users.premades;

import authserver.models.Premade;
import authserver.users.identities.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PremadeServiceImpl implements PremadeService {

    @Autowired
    UserRepository userRepository;

    @Autowired
    PremadeRepository premadeRepository;

    @Override
    public boolean enterTeamDetails(Premade premade, String by) throws Exception {
        //does not exist (start)
        //exists (add to)
        //  check full
        //    y: calculate ratings
        //    n: flip bit
        //already full return 204
        Optional<Premade> exists = premadeRepository.findByTeamname(premade.getTeamname());
        if(exists.isPresent()){
            boolean isComplete = exists.get().confirmAs(by); //throws if teamname taken
            if(isComplete){
                premade.copyConfirms(exists);
                premade.confirmAs(by);
                premade.injectRatings(userRepository);
                premadeRepository.delete(exists.get());
                premadeRepository.save(premade);
                return true;
            }
            boolean validUser = premade.copyConfirms(exists);
            if(validUser){
                premadeRepository.delete(exists.get());
                premadeRepository.save(premade);
                return false;
            }
        }else{
            premade.confirmAs(by);
            premadeRepository.save(premade);
            return false;
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Premade> findPremadeByTeamname(String email) throws DataAccessException {
        Optional<Premade> ret;
        try {
            ret = premadeRepository.findByTeamname(email);
        } catch (ObjectRetrievalFailureException | EmptyResultDataAccessException e) {
            // just ignore not found exceptions for Jdbc/Jpa realization
            return null;
        }
        return ret;
    }

    @Override
    public void savePremade(Premade premade) throws Exception {
        premadeRepository.save(premade);
    }

    @Override
    public void deletePremade(Premade premade) throws Exception {
        premadeRepository.delete(premade);
    }

    @Override
    public List<Premade> findAll() {
        return premadeRepository.findAll();
    }

}