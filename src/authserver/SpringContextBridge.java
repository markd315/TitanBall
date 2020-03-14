package authserver;

import authserver.matchmaking.Matchmaker;
import authserver.users.PersistenceManager;
import authserver.users.identities.UserService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContextBridge
        implements SpringContext, ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Autowired
    private UserService userService;

    @Autowired
    private Matchmaker matchmaker;

    @Autowired
    private PersistenceManager persistenceManager;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext)
            throws BeansException {
        this.applicationContext = applicationContext;
    }


    public static SpringContext services() {
        return applicationContext.getBean(SpringContext.class);
    }

    @Override
    public UserService getUserService() {
        return userService; //Return the Autowired userService
    }

    @Override
    public Matchmaker getMatchmaker() {
        return matchmaker; //Return the Autowired userService
    }

    @Override
    public PersistenceManager getPersistenceManager() {
        return persistenceManager; //Return the Autowired userService
    }
}
