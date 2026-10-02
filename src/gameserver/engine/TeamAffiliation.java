package gameserver.engine;

import com.fasterxml.jackson.annotation.*;

public enum TeamAffiliation {
    HOME, AWAY, UNAFFILIATED, ANY, SAME, ENEMIES, OPPONENT, IMMUNE;

    public TeamAffiliation opposite() {
        if (this == HOME) return AWAY;
        if (this == AWAY) return HOME;
        return this;
    }

    public boolean isHome() {
        return this == HOME;
    }

    public boolean isAway() {
        return this == AWAY;
    }
}
