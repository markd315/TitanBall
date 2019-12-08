package gameserver.entity;

import java.awt.*;

public class RangeCircle {
    protected int radius;
    protected float[] color = new float[4];

    public RangeCircle(Color color, int radius){
        this.radius = radius;
        this.color[0] = color.getRed()/256.0f;
        this.color[1] = color.getGreen()/256.0f;
        this.color[2] = color.getBlue()/256.0f;
        this.color[3] = color.getAlpha()/256.0f;
    }
    public RangeCircle(int radius){
        this.radius = radius;
        this.color[0] = 0.5f;
        this.color[1] = 0.5f;
        this.color[2] = 0.5f;
        this.color[3] = 0.0001f;
    }
    public RangeCircle(){
        this.radius = 0;
        this.color[0] = 0.5f;
        this.color[1] = 0.5f;
        this.color[2] = 0.5f;
        this.color[3] = 0.0001f;
    }
    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public Color readColor() {
        return new Color(color[0], color[1], color[2], color[3]);
    }

    //Dumb name so Jackson won't serialize
    public void establishColor(Color color) {
        this.color[0] = color.getRed()/256.0f;
        this.color[1] = color.getGreen()/256.0f;
        this.color[2] = color.getBlue()/256.0f;
        this.color[3] = color.getAlpha()/256.0f;
    }
}
