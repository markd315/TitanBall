package client.graphical;

import client.TitanballClient;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

public class ScreenConst {
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

    public void drawString(Graphics2D g2D, String payload, int x, int y) {
        x = adjX(x);
        y = adjY(y);
        g2D.drawString(payload, x, y);
    }

    public void setFont(Graphics2D g2D, Font verdana) {
        int size = verdana.getSize();
        size = adjY(size);//doing both X and Y seems to leave it too big?
        g2D.setFont(new Font(verdana.getName(), verdana.getStyle(), size));
    }

    public void drawImage(Graphics2D g2D, Image image, int x, int y, TitanballClient context) {
        drawImage(g2D, image, x, y, 1.0, 1.0, context);
    }

    public void drawImage(Graphics2D g2D, Image image, int x, int y, double wMult, double hMult, TitanballClient context) {
        x = adjX(x);
        y = adjY(y);
        int w = (int) (image.getWidth(context) * wMult);
        int h = (int) (image.getHeight(context) * hMult);
        BufferedImage bi = Images.resize(toBi(image), adjX(w), adjY(h));
        g2D.drawImage(bi, x, y, context);
    }

    private BufferedImage toBi(Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }

        // Create a buffered image with transparency
        BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        // Draw the image on to the buffered image
        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(img, 0, 0, null);
        bGr.dispose();

        // Return the buffered image
        return bimage;
    }

    public void drawImage(Graphics2D g2D, Image image, AffineTransform at, TitanballClient context) {
        double x = adjX((int) at.getTranslateX());
        double y = adjY((int) at.getTranslateY());
        at.setToTranslation(x, y);
        int w = image.getWidth(context);
        int h = image.getHeight(context);
        BufferedImage bi = Images.resize(toBi(image), w, h);
        g2D.drawImage(bi, at, context);
    }

    public double adjX(double v) {
        return v / (double) calX * (double) X;
    }

    private int adjX(int i) {
        return (int) (((double)i * X) / calX);
    }

    public int adjY(int i) {
        return (int) (((double)i * Y) / calY);
    }

    public int invertMouseX(int i){
        return (int) (((double)i * calX) / X);
    }

    public int invertMouseY(int i){
        return (int) (((double)i * calY) / Y);
    }

    public int X;
    public int Y;
    public static int calX = 1920; //client originally calibrated for this resolution
    public static int calY = 1080;

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

    public void draw(Graphics2D g2D, GoalSprite goal) {
        goal.height = adjY((int) goal.height);
        goal.width = adjX(goal.width);
        goal.x = adjX(goal.x);
        goal.y = adjY((int) goal.y);
        g2D.draw(goal);
    }

    public void fill(Graphics2D g2D, Rectangle rectangle) {
        rectangle = new Rectangle((int) adjX(rectangle.getX()),
                adjY((int) rectangle.getY()),
                (int) adjX(rectangle.getWidth()),
                adjY((int) rectangle.getHeight()));
        g2D.fill(rectangle);
    }

    public void fill(Graphics2D g2D, Ellipse2D.Double ell) {
        ell = new Ellipse2D.Double(adjX(ell.getX()),
                adjY((int) ell.getY()),
                adjX(ell.getWidth()),
                adjY((int) ell.getHeight()));
        g2D.fill(ell);
    }
}
