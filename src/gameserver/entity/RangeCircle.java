package gameserver.entity;

import java.awt.*;
import java.io.Serializable;

public class RangeCircle  implements Serializable {
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

    public Color getColor() {

        return new Color(color[0], color[1], color[2], color[3]);
    }

    public void setColor(Color color) {
        this.color[0] = color.getRed()/256.0f;
        this.color[1] = color.getGreen()/256.0f;
        this.color[2] = color.getBlue()/256.0f;
        this.color[3] = color.getAlpha()/256.0f;
    }
}
