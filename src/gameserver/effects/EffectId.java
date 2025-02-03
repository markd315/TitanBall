package gameserver.effects;

import java.io.Serializable;

public enum EffectId implements Serializable {
    BURN, FLARE, BOMB, COOLDOWN_Q, COOLDOWN_W,
    STUN, DEAD, ROOT, CURSED, FAST, DEFENSE, HEAL,
    STEALTHED, SLOW, SHOOT, COOLDOWN_CURVE, HIDE_BALL, COOLDOWN_STEAL, BLIND, ATTACKED, STEAL;
}
