package gameserver.entity;

import gameserver.engine.GameEngine;

public interface Collidable {

    void triggerCollide(GameEngine context, Box entity);
}
