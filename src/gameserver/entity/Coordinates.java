package gameserver.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Coordinates {
    @JsonProperty
    public double X, Y;

    public Coordinates(double x, double y) {
        this.X=x;
        this.Y=y;
    }
    public Coordinates() {
        this(0,0);
    }

    public double getX() {
        return X;
    }

    public void setX(double x) {
        X = x;
    }

    public double getY() {
        return Y;
    }

    public void setY(double y) {
        Y = y;
    }
}
