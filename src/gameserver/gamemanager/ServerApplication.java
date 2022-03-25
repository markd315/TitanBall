package gameserver.gamemanager;

import authserver.LoginApp;
import authserver.SpringContextBridge;
import authserver.jwt.JwtTokenProvider;
import authserver.matchmaking.Matchmaker;
import authserver.users.PersistenceManager;
import org.springframework.boot.SpringApplication;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ServerApplication {
    public static final boolean PAYWALL = false;


    static Matchmaker matchmaker;

    static PersistenceManager persistenceManager;

    static JwtTokenProvider tp = new JwtTokenProvider();

    static Properties prop;
    static String appSecret;

    static {
        try {
            prop = new Properties();
            prop.load(new FileInputStream(new File("application.properties")));
            appSecret = prop.getProperty("app.jwtSecret");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args){
        SpringApplication.run(LoginApp.class, args);
    }

    public static void instantiateSpringContext() {
        persistenceManager = SpringContextBridge.services().getPersistenceManager();
        matchmaker = SpringContextBridge.services().getMatchmaker();
    }

    public static PersistenceManager getPersistenceManager(){
        return persistenceManager;
    }
}
