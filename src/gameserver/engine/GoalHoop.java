package gameserver.engine;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.time.LocalDateTime;

public class GoalHoop implements Serializable {
    @JsonProperty
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    public LocalDateTime nextAvailable;
    @JsonProperty
    public boolean onCooldown, frozen;
    @JsonProperty
    public TeamAffiliation team;

    @JsonProperty
    public int x, y, w, h;

    public GoalHoop(int x, int y, int w, int h, TeamAffiliation team) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h= h;
        this.team = team;
    }

    public void trigger() {
        onCooldown = true;

        LocalDateTime now = LocalDateTime.now();
        nextAvailable = now.plusNanos(1000000L * 1000);
    }

    public void freeze() {
        onCooldown = true;
        frozen = true;
        LocalDateTime now = LocalDateTime.now();
        nextAvailable = now.plusNanos(1000000L * 5000);
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
    public GoalHoop(){
    }

    public LocalDateTime getNextAvailable(){
        return nextAvailable;
    }

    public Rectangle2D asRect() {
        return new Rectangle2D.Double(x, y, w, h);
    }
}
