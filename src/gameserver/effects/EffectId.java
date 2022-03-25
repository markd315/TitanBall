package gameserver.effects;

import java.io.Serializable;

public enum EffectId implements Serializable {
    BURN, FLARE, BOMB, COOLDOWN_E, COOLDOWN_R,
    STUN, DEAD, ROOT, CURSED, FAST, DEFENSE, HEAL,
    STEALTHED, SLOW, SHOOT, COOLDOWN_CURVE, HIDE_BALL, BLIND, COOLDOWN_Q, ATTACKED, STEAL;
}
