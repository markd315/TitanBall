package gameserver.entity;

import javafx.scene.paint.Color;

public class RangeCircle {

    private int radius;
    private double[] color = new double[4]; // Store color as RGBA values (0 to 1 range)

    // Constructor with color and radius
    public RangeCircle(Color color, int radius) {
        this.radius = radius;
        this.color[0] = color.getRed();    // Red channel (0 to 1)
        this.color[1] = color.getGreen();  // Green channel (0 to 1)
        this.color[2] = color.getBlue();   // Blue channel (0 to 1)
        this.color[3] = color.getOpacity(); // Alpha (opacity channel, 0 to 1)
    }

    // Constructor with just radius (default gray color with low opacity)
    public RangeCircle(int radius) {
        this.radius = radius;
        this.color[0] = 0.5;  // Default gray
        this.color[1] = 0.5;  // Default gray
        this.color[2] = 0.5;  // Default gray
        this.color[3] = 1;
    }

    // Default constructor (no radius and a default color)
    public RangeCircle() {
        this.radius = 0;
        this.color[0] = 0.5;  // Default gray
        this.color[1] = 0.5;  // Default gray
        this.color[2] = 0.5;  // Default gray
        this.color[3] = 1;
    }

    // Get the radius of the circle
    public int getRadius() {
        return radius;
    }

    // Set the radius of the circle
    public void setRadius(int radius) {
        this.radius = radius;
    }

    // Get the color as a JavaFX Color object
    public Color getColor() {
        return new Color(color[0], color[1], color[2], color[3]);
    }

    // Set the color from a JavaFX Color object
    public void setColor(Color color) {
        this.color[0] = color.getRed();    // Red channel (0 to 1)
        this.color[1] = color.getGreen();  // Green channel (0 to 1)
        this.color[2] = color.getBlue();   // Blue channel (0 to 1)
        this.color[3] = color.getOpacity(); // Alpha channel (0 to 1)
    }
}
