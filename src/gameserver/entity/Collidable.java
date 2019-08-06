package gameserver.entity;

import gameserver.GameEngine;

public interface Collidable {

    void triggerCollide(GameEngine context, Box entity);
}
