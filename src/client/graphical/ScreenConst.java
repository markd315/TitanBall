package client.graphical;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;

import java.io.File;

public class ScreenConst {

    private int X;
    private int Y;
    public static int calX = 1920;
    public static int calY = 1080;

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
        return v / calX * X;
    }

    public double adjY(double v) {
        return v / calY * Y;
    }

    public int adjX(int v) {
        return v / calX * X;
    }

    public int adjY(int v) {
        return v / calY * Y;
    }

    public int invertMouseX(int i){
        return (int) (((double)i * calX) / X);
    }

    public int invertMouseY(int i){
        return (int) (((double)i * calY) / Y);
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

    public void fill(GraphicsContext gc, Polygon p) {
        double[] adjustedXPoints = new double[p.getPoints().size() / 2];
        double[] adjustedYPoints = new double[p.getPoints().size() / 2];

        for (int i = 0; i < p.getPoints().size(); i += 2) {
            adjustedXPoints[i / 2] = adjX(p.getPoints().get(i));
            adjustedYPoints[i / 2] = adjY(p.getPoints().get(i + 1));
        }
        gc.fillPolygon(adjustedXPoints, adjustedYPoints, adjustedXPoints.length);
    }

    public Image getScaledImage(GraphicsContext gc, Image srcImg, int width, int height) {
        WritableImage scaledImage = new WritableImage(width, height);
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        gc.getCanvas().snapshot(params, scaledImage);
        return scaledImage;
    }

    public void drawImage(GraphicsContext gc, Image image, int x, int y, int sizeX, int sizeY) {
        Image scaledImage = getScaledImage(gc, image, sizeX, sizeY);
        gc.drawImage(scaledImage, x, y, sizeX, sizeY);
    }
}
