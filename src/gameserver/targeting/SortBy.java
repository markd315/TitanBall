package gameserver.targeting;

import java.io.Serializable;

public enum SortBy  implements Serializable {
    NEAREST,
    NEAREST_MOUSE,
    FURTHEST,
    FURTHEST_MOUSE,
    NEAREST_BALL,
    FURTHEST_BALL,
    LOWEST_HP,
    HIGHEST_HP,

}
