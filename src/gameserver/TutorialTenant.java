package gameserver;

public class TutorialTenant extends GameTenant{
    public static final ServerMode SERVER_MODE = ServerMode.ALL;

    public TutorialTenant(){
        super();
        serverMode = SERVER_MODE;
    }

}
