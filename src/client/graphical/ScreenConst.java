package client.graphical;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;

import java.io.File;
import java.io.Serializable;

public class ScreenConst implements Serializable {
    public static final long serialVersionUID = 1L;
    private int X;
    private int Y;
    public static final int CAL_X = 1920;
    public static final int CAL_Y = 1080;

    public ScreenConst(int xSize, int ySize) {
        this.X = xSize;
        this.Y = ySize;
        RESULT_IMG_X = adjX(300);
        RESULT_IMG_Y = adjY(200);
        STATS_FONT = adjY(18);
        STATS_MEDAL = 290;
        BALL_PTR_X = 40;
        BALL_PTR_Y = 18;
        GOAL_TXT_X = adjX(220);
        GOAL_TXT_Y = adjY(250);
        CAM_MAX_X = adjX(630);
        CAM_MAX_Y = adjY(350);
        STAT_CAT_FONT = adjY(20);
        STAT_EXTERNAL_H = adjY(9);
        STAT_INTERNAL_H = adjY(5);
        STAT_EXTERNAL_W = adjX(106);
        STAT_INTERNAL_SC = adjX(1.0);
        STAT_CAT_X = adjX(1045);
        STAT_FX_X = adjY(1060);
        STAT_Y_SCL = adjY(100);
        STAT_BAR_X = adjX(1100);
        DESC_ABIL_X = adjX(752);
        ICON_ABIL_X = adjX(712);
        OVR_DESC_FONT = adjY(22);
        ABIL_DESC_FONT = adjY(13);
        OVR_DESC_Y = adjY(690);
        E_ABIL_Y = adjY(545);
        E_DESC_Y = adjY(560);
        R_ABIL_Y = adjY(605);
        R_DESC_Y = adjY(620);
        STAT_INT_X = adjX(1103);
    }

    public void drawString(GraphicsContext gc, String payload, double x, double y) {
        x = adjX(x);
        y = adjY(y);
        gc.fillText(payload, x, y);
    }

    public void setFont(GraphicsContext gc, Font font) {
        double size = font.getSize();
        size = adjY(size);
        gc.setFont(new Font(font.getName(), size));
    }

    public Image loadImage(String path) {
        try {
            // Debugging output
            File file = new File(path);
            //System.out.println("Absolute path: " + file.getAbsolutePath());
            java.net.URL resource = file.toURI().toURL();
            if (resource == null) {
                System.err.println("Resource not found: " + path);
                return null;
            }
            //System.out.println("Resource found at: " + resource);
            return new Image(resource.toString());
        } catch (Exception e) {
            System.err.println("Error loading image: " + e.getMessage());
            return null;
        }
    }

    public void drawImage(GraphicsContext gc, Image image, double x, double y) {
        x = adjX(x);
        y = adjY(y);
        gc.drawImage(image, x, y);
    }

    public double adjX(double v) {
        return v / CAL_X * X;
    }

    public double adjY(double v) {
        return v / CAL_Y * Y;
    }

    public int adjX(int v) {
        return (int) ((double)v / (double) CAL_X * X);
    }

    public int adjY(int v) {
        return (int) ((double)v / (double) CAL_Y * Y);
    }

    public int invertMouseX(int i){
        return (int) (((double)i * CAL_X) / X);
    }

    public int invertMouseY(int i){
        return (int) (((double)i * CAL_Y) / Y);
    }

    public final int RESULT_IMG_X;
    public final int RESULT_IMG_Y;
    public final int STATS_FONT;
    public final double STATS_MEDAL;
    public final int BALL_PTR_X;
    public final int BALL_PTR_Y;
    public final int GOAL_TXT_X;
    public final int GOAL_TXT_Y;
    public final double CAM_MAX_X;
    public final double CAM_MAX_Y;
    public final int STAT_CAT_FONT;
    public final int STAT_EXTERNAL_H;
    public final int STAT_INTERNAL_H;
    public final int STAT_EXTERNAL_W;
    public final double STAT_INTERNAL_SC;
    public final int STAT_CAT_X;
    public final int STAT_FX_X;
    public final int STAT_Y_SCL;
    public final int STAT_BAR_X;
    public final int DESC_ABIL_X;
    public final int ICON_ABIL_X;
    public final int OVR_DESC_FONT;
    public final int ABIL_DESC_FONT;
    public final int OVR_DESC_Y;
    public final int E_ABIL_Y;
    public final int E_DESC_Y;
    public final int R_ABIL_Y;
    public final int R_DESC_Y;
    public final int STAT_INT_X;

    public void draw(GraphicsContext gc, GoalSprite goal) {
        goal.setRadiusY(adjY(goal.getRadiusY()));
        goal.setRadiusX(adjX(goal.getRadiusX()));
        goal.setCenterX(adjX(goal.getCenterX()));
        goal.setCenterY(adjY(goal.getCenterY()));
        gc.strokeOval(goal.getCenterX() - goal.getRadiusX(), goal.getCenterY() - goal.getRadiusY(), goal.getRadiusX() * 2, goal.getRadiusY() * 2);
    }

    public void fill(GraphicsContext gc, Rectangle rectangle) {
        rectangle = new Rectangle(adjX(rectangle.getX()),
                adjY(rectangle.getY()),
                adjX(rectangle.getWidth()),
                adjY(rectangle.getHeight()));
        gc.fillRect(rectangle.getX(), rectangle.getY(), rectangle.getWidth(), rectangle.getHeight());
    }

    public void fill(GraphicsContext gc, Ellipse ellipse) {
        ellipse = new Ellipse(adjX(ellipse.getCenterX()),
                adjY(ellipse.getCenterY()),
                adjX(ellipse.getRadiusX()),
                adjY(ellipse.getRadiusY()));
        gc.fillOval(ellipse.getCenterX() - ellipse.getRadiusX(),
                    ellipse.getCenterY() - ellipse.getRadiusY(),
                    ellipse.getRadiusX() * 2,
                    ellipse.getRadiusY() * 2);
    }


    public Image getScaledImage(Image srcImg, int width, int height) {
        // Create a new writable image with the desired dimensions
        WritableImage scaledImage = new WritableImage(width, height);

        // Draw the scaled image onto a temporary canvas
        Canvas tempCanvas = new Canvas(width, height);
        GraphicsContext tempGc = tempCanvas.getGraphicsContext2D();

        tempGc.drawImage(srcImg, 0, 0, width, height); // Scale and draw

        // Snapshot the canvas onto the writable image
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        tempCanvas.snapshot(params, scaledImage);

        return scaledImage;
}

    public void drawImage(GraphicsContext gc, Image image, int x, int y, int sizeX, int sizeY) {
        Image scaledImage = getScaledImage(image, sizeX, sizeY);
        gc.drawImage(scaledImage, x, y, sizeX, sizeY);
    }
}
