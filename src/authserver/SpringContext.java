package authserver;

import authserver.matchmaking.Matchmaker;
import authserver.users.UserService;

public interface SpringContext {
    UserService getUserService();
    Matchmaker getMatchmaker();
}
