package gameserver.engine;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GameOptions {
    //TODO tournament best of n
    //Updating these defaults? ALSO CHANGE ServerApplication:107 default!
    @JsonProperty
    public int playerIndex, bestOfIndex, goalieIndex,
            playToIndex, winByIndex, hardWinIndex, suddenDeathIndex, tieIndex;


    protected static final int[] playersVal = {3,4,5,0,1,2};
    protected static final int[] goalieVal = {1, 0, 2};
    protected static final int[] bestOfVal = {1, 3, 5, 7, 9};
    protected static final int[] playToVal = {5, 10, 20, 30, 50, 100, 1};
    protected static final int[] winByVal = {2, 3, 4, 5, 10, 12, 15, 1};
    protected static final int[] hardWinVal = {9999, 5, 10, 20, 30, 50, 100};
    protected static final int[] suddenDeathVal = {10, 12, 15, 18, 20, 25, 30, 35, 60, 9999, 3, 5};
    protected static final int[] tieVal = {12, 15, 18, 20, 25, 30, 35, 60, 9999, 3, 5, 10};
    protected static final String[] playersDisp = {"3v3", "4v4", "5v5 (sep goalie)", "Single-player", "1v1", "2v2"};
    protected static final String[] goalieDisp = {"Goalies on", "Goalies off", "Permanent goalies"};
    protected static final String[] bestOfDisp = {"Best of 1", "Best of 3", "Best of 5", "Best of 7", "Best of 9"} ;
    protected static final String[] playToDisp = {"Play to 5", "Play to 10", "Play to 20", "Play to 30", "Play to 50", "Play to 100", "Play to 1"};
    protected static final String[] winByDisp = {"Win by 2", "Win by 3", "Win by 4", "Win by 5", "Win by 10", "Win by 12", "Win by 15", "Win by 1"};
    protected static final String[] hardWinDisp = {"OFF", "End at 5", "End at 10", "End at 20", "End at 30", "End at 50", "End at 100"};
    protected static final String[] suddenDeathDisp = {"Sudden Death 10:00", "Sudden Death 12:00", "Sudden Death 15:00", "Sudden Death 18:00", "Sudden Death 20:00", "Sudden Death 25:00", "Sudden Death 30:00", "Sudden Death 35:00", "Sudden Death 60:00", "Sudden Death OFF", "Sudden Death 3:00", "Sudden Death 5:00"};
    protected static final String[] tieDisp = {"Draw at 12:00", "Draw at 15:00", "Draw at 18:00", "Draw at 20:00", "Draw at 25:00", "Draw at 30:00", "Draw at 35:00", "Draw at 60:00", "Draw OFF", "Draw at 3:00", "Draw at 5:00", "Draw at 10:00"};

    public GameOptions() {
    }

    public GameOptions(String tournamentCode) {
        String[] split = tournamentCode.split("/");
        if(split.length != 9){
            throw new IllegalArgumentException("invalid tournament code (options)");
        }
        //Indexes are set to literals on serverside
        this.playerIndex = Integer.parseInt(split[1]);
        this.goalieIndex = Integer.parseInt(split[2]);
        this.bestOfIndex = Integer.parseInt(split[3]);
        this.playToIndex = Integer.parseInt(split[4]);
        this.winByIndex = Integer.parseInt(split[5]);
        this.hardWinIndex = Integer.parseInt(split[6]);
        this.suddenDeathIndex = Integer.parseInt(split[7]);
        this.tieIndex = Integer.parseInt(split[8]);
    }

    public String disp(int optionLine){
        switch (optionLine){
            case 0:
                return playersDisp[playerIndex];
            case 1:
                return goalieDisp[goalieIndex];
            case 2:
                return bestOfDisp[bestOfIndex];
            case 3:
                return playToDisp[playToIndex];
            case 4:
                return winByDisp[winByIndex];
            case 5:
                return hardWinDisp[hardWinIndex];
            case 6:
                return suddenDeathDisp[suddenDeathIndex];
            case 7:
                return tieDisp[tieIndex];
        }
        return "bad";
    }

    public void advance(int optionLine, int dist){
        switch (optionLine){
            case 0:
                playerIndex = circleAdvance(playerIndex, dist, playersVal);
                break;
            case 1:
                goalieIndex = circleAdvance(goalieIndex, dist, goalieVal);
                break;
            case 2:
                bestOfIndex = circleAdvance(bestOfIndex, dist, bestOfVal);
                break;
            case 3:
                playToIndex = circleAdvance(playToIndex, dist, playToVal);
                break;
            case 4:
                winByIndex = circleAdvance(winByIndex, dist, winByVal);
                break;
            case 5:
                hardWinIndex = circleAdvance(hardWinIndex, dist, hardWinVal);
                break;
            case 6:
                suddenDeathIndex = circleAdvance(suddenDeathIndex, dist, suddenDeathVal);
                break;
            case 7:
                tieIndex = circleAdvance(tieIndex, dist, tieVal);
                break;
        }
    }

    public int circleAdvance(int curr, int inc, int[] bounds){
        curr += inc;
        if(curr >= bounds.length){
            curr -= bounds.length;
        }
        if(curr < 0){
            curr += bounds.length;
        }
        return curr;
    }

    public void getDisplay(int code){

    }
    public String toStringSrv(){
        StringBuilder sb = new StringBuilder();
        sb.append("/");
        sb.append(playerIndex);
        sb.append("/");
        sb.append(bestOfIndex);
        sb.append("/");
        sb.append(goalieIndex);
        sb.append("/");
        sb.append(playToIndex);
        sb.append("/");
        sb.append(winByIndex);
        sb.append("/");
        sb.append(hardWinIndex);
        sb.append("/");
        sb.append(suddenDeathIndex);
        sb.append("/");
        sb.append(tieIndex);
        return new String(sb);
    }

    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append("/");
        sb.append(playersVal[playerIndex]);
        sb.append("/");
        sb.append(bestOfVal[bestOfIndex]);
        sb.append("/");
        sb.append(goalieVal[goalieIndex]);
        sb.append("/");
        sb.append(playToVal[playToIndex]);
        sb.append("/");
        sb.append(winByVal[winByIndex]);
        sb.append("/");
        sb.append(hardWinVal[hardWinIndex]);
        sb.append("/");
        sb.append(suddenDeathVal[suddenDeathIndex]);
        sb.append("/");
        sb.append(tieVal[tieIndex]);
        return new String(sb);
    }

    public static int[] getPlayersVal() {
        return playersVal;
    }

    public static int[] getGoalieVal() {
        return goalieVal;
    }

    public static int[] getBestOfVal() {
        return bestOfVal;
    }

    public static int[] getPlayToVal() {
        return playToVal;
    }

    public static int[] getWinByVal() {
        return winByVal;
    }

    public static int[] getHardWinVal() {
        return hardWinVal;
    }

    public static int[] getSuddenDeathVal() {
        return suddenDeathVal;
    }

    public static int[] getTieVal() {
        return tieVal;
    }

    public static String[] getPlayersDisp() {
        return playersDisp;
    }

    public static String[] getGoalieDisp() {
        return goalieDisp;
    }

    public static String[] getBestOfDisp() {
        return bestOfDisp;
    }

    public static String[] getPlayToDisp() {
        return playToDisp;
    }

    public static String[] getWinByDisp() {
        return winByDisp;
    }

    public static String[] getHardWinDisp() {
        return hardWinDisp;
    }

    public static String[] getSuddenDeathDisp() {
        return suddenDeathDisp;
    }

    public static String[] getTieDisp() {
        return tieDisp;
    }
}

