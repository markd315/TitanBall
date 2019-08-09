package client;

public class ScreenConst {
    public ScreenConst(int xSize, int ySize) {
        this.X = xSize;
        this.Y = ySize;
        RESULT_IMG_X = adjX(300);
        RESULT_IMG_Y = adjY(200);
        STATS_Y = adjY(425);
        STATS_FONT = adjY(18);
        STATS_X = adjX(310);
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
        DESC_ABIL_X = adjX(750);
        ICON_ABIL_X = adjX(712);
        OVR_DESC_FONT = adjY(22);
        ABIL_DESC_FONT = adjY(14);
        OVR_DESC_Y = adjY(690);
        E_ABIL_Y = adjY(445);
        E_DESC_Y = adjY(460);
        R_ABIL_Y = adjY(505);
        R_DESC_Y = adjY(520);
        STAT_INT_X = adjX(1103);
    }

    private double adjX(double v) {
        return v/(double)calX*(double)X;
    }

    private int adjX(int i) {
        return (int) ((i/((double)calX))*X);
    }

    private int adjY(int i) {
        return (int) ((i/((double)calY))*Y);
    }


    public int X;
    public int Y;
    public static int calX = 1920; //client originally calibrated for this resolution
    public static int calY = 1080;

    public final int RESULT_IMG_X;
    public final int RESULT_IMG_Y;
    public final int STATS_Y;
    public final int STATS_FONT;
    public final int STATS_X;
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
}
