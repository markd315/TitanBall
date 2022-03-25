package gameserver.engine;

import java.io.Serializable;

public enum TeamAffiliation implements Serializable {
    HOME, AWAY, UNAFFILIATED, ANY, SAME, ENEMIES, OPPONENT, IMMUNE
}
