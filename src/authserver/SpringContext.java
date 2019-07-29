package authserver;

import authserver.matchmaking.Matchmaker;
import authserver.users.PersistenceManager;
import authserver.users.UserService;

public interface SpringContext {
    UserService getUserService();
    Matchmaker getMatchmaker();
    PersistenceManager getPersistenceManager();
}
