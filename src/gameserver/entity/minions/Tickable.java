package gameserver.entity.minions;

import gameserver.engine.GameEngine;

public interface Tickable {
    void tick(GameEngine context);
}
