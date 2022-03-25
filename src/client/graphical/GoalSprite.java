package client.graphical;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import gameserver.engine.GoalHoop;
import gameserver.engine.TeamAffiliation;

import java.awt.geom.Ellipse2D;
import java.time.LocalDateTime;

public class GoalSprite extends Ellipse2D.Double {
    @JsonProperty
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    public LocalDateTime nextAvailable;
    public boolean onCooldown, frozen;
    public TeamAffiliation team;

    public GoalSprite(GoalHoop payload, int camX, int camY, ScreenConst sconst){
        super(payload.x - camX, payload.y - camY, payload.w, payload.h);
        y = sconst.adjY(payload.y - camY);
        x = sconst.adjX(payload.x - camX);
        width = sconst.adjX(payload.w);
        height = sconst.adjY(payload.h);
        this.team = payload.team;
        this.nextAvailable = payload.nextAvailable;
        this.onCooldown = payload.onCooldown;
        this.frozen = payload.frozen;
    }

    public boolean checkReady() {
        LocalDateTime currentTimestamp = LocalDateTime.now();
        if (frozen) {
            if (currentTimestamp.plusNanos(1000000L * 1000).isAfter(nextAvailable)) {
                frozen = false;
            }
        }
        if (onCooldown) {
            if (currentTimestamp.isAfter(nextAvailable)) {
                onCooldown = false;
            }
        }
        return !onCooldown;
    }
}
