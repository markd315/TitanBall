package authserver.users.premades;

import authserver.models.Premade;

import javax.naming.InsufficientResourcesException;
import java.util.List;
import java.util.Optional;

public interface PremadeService {
    void savePremade(Premade premade) throws Exception;
    void deletePremade(Premade premade) throws Exception;
    Optional<Premade> findPremadeByTeamname(String teamname);
    List<Premade> findAll();
    boolean enterTeamDetails(Premade premade, String by) throws Exception;
}
