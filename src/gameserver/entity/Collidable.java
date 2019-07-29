package gameserver.entity;

import gameserver.Game;

public interface Collidable {

    void triggerCollide(Game context, Box entity);
}
