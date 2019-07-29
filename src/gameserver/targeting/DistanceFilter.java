package gameserver.targeting;

public class DistanceFilter {
    private boolean lessThanNotGreaterThan;
    private boolean strict;
    private int dist;

    public DistanceFilter(){this(0);}

    public DistanceFilter(int i) {
        this(i, false);
    }

    public DistanceFilter(int i, boolean lessThanNotGreaterThan) {
        this(i, lessThanNotGreaterThan, false);
    }

    public DistanceFilter(int i, boolean lessThanNotGreaterThan, boolean strict) {
        this.dist = i;
        this.lessThanNotGreaterThan = lessThanNotGreaterThan;
        this.strict = strict;
    }

    public boolean isLessThanNotGreaterThan() {
        return lessThanNotGreaterThan;
    }

    public void setLessThanNotGreaterThan(boolean lessThanNotGreaterThan) {
        this.lessThanNotGreaterThan = lessThanNotGreaterThan;
    }

    public boolean isStrict() {
        return strict;
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    public int getDist() {
        return dist;
    }

    public void setDist(int dist) {
        this.dist = dist;
    }
}
