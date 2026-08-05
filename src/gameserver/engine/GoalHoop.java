package gameserver.engine;


import org.joda.time.Instant;
import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GoalHoop  {
    public Instant nextAvailable;
    public boolean onCooldown, frozen;
    public TeamAffiliation team;

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

        Instant now = Instant.now();
        nextAvailable = now.plus(1000);
    }

    public void freeze() {
        onCooldown = true;
        frozen = true;
        Instant now = Instant.now();
        nextAvailable = now.plus(5000);
    }

    public boolean checkReady() {
        Instant currentTimestamp = Instant.now();
        if (frozen) {
            if (currentTimestamp.plus(1000).isAfter(nextAvailable)) {
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

    public CollisionMath.EllipseData ellipseData() {
        return new CollisionMath.EllipseData(this.x + (double) this.w /2, this.y + (double) this.h /2,
                (double) this.w / 2, (double) this.h / 2);
    }
}
