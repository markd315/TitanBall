package gameserver.entity;

import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RangeCircle  {

    private static final long serialVersionUID = 1L;

    private int radius;
    private int radiusX;
    private int radiusY;
    private double[] color = new double[4]; // Store color as RGBA values (0 to 1 range)

    // Constructor with color and radius
    public RangeCircle(double r, double g, double b, double a, int radius) {
        this.radius = radius;
        this.radiusX = radius;
        this.radiusY = radius;
        this.color[0] = r;    // Red channel (0 to 1)
        this.color[1] = g;  // Green channel (0 to 1)
        this.color[2] = b;   // Blue channel (0 to 1)
        this.color[3] = a; // Alpha (opacity channel, 0 to 1)
    }

    // Constructor with color and elliptical radii
    public RangeCircle(double r, double g, double b, double a, int radiusX, int radiusY) {
        this.radius = Math.max(radiusX, radiusY);
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.color[0] = r;    // Red channel (0 to 1)
        this.color[1] = g;  // Green channel (0 to 1)
        this.color[2] = b;   // Blue channel (0 to 1)
        this.color[3] = a; // Alpha (opacity channel, 0 to 1)
    }

    public RangeCircle(int radius) {
        this.radius = radius;
        this.radiusX = radius;
        this.radiusY = radius;
        this.color[0] = 0.5;  // Default gray
        this.color[1] = 0.5;
        this.color[2] = 0.5;
        this.color[3] = 1.0;
    }

    public RangeCircle(int radiusX, int radiusY) {
        this.radius = Math.max(radiusX, radiusY);
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.color[0] = 0.5;  // Default gray
        this.color[1] = 0.5;
        this.color[2] = 0.5;
        this.color[3] = 1.0;
    }

    public RangeCircle() {
        this.radius = 0;
        this.radiusX = 0;
        this.radiusY = 0;
        this.color[0] = 0.5;  // Default gray
        this.color[1] = 0.5;
        this.color[2] = 0.5;
        this.color[3] = 1.0;
    }

    public int getRadius() {
        return radius;
    }

    public int getRadiusX() {
        return radiusX > 0 ? radiusX : radius;
    }

    public int getRadiusY() {
        return radiusY > 0 ? radiusY : radius;
    }

    public double[] getColorArray() {
        return color;
    }

    public void setColor(double r, double g, double b, double a) {
        this.color[0] = r;
        this.color[1] = g;
        this.color[2] = b;
        this.color[3] = a;
    }
}
