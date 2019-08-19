package networking;


public class KeyDifferences {
    public int UP = 0, LEFT = 0, DOWN = 0, RIGHT = 0, E = 0, R = 0, CAM = 0,
            STEAL = 0, TAB = 0, SWITCH = 0, BOOST =0, BOOST_LOCK =0, MV_CLICK=0, MV_BALL=0;

    public KeyDifferences(ClientPacket act, ClientPacket old) {
        if (act.UP) {
            UP++;
        }
        if (act.LEFT) {
            LEFT++;
        }
        if (act.DOWN) {
            DOWN++;
        }
        if (act.RIGHT) {
            RIGHT++;
        }
        if (act.E) {
            E++;
        }
        if (act.R) {
            R++;
        }
        if (act.CAM) {
            CAM++;
        }
        if (act.STEAL) {
            STEAL++;
        }
        if (act.SWITCH) {
            SWITCH++;
        }
        if(act.BOOST){
            BOOST++;
        }
        if(act.BOOST_LOCK){
            BOOST_LOCK++;
        }
        if (old != null) {
            if (old.UP) {
                UP--;
            }
            if (old.LEFT) {
                LEFT--;
            }
            if (old.DOWN) {
                DOWN--;
            }
            if (old.RIGHT) {
                RIGHT--;
            }
            if (old.E) {
                E--;
            }
            if (old.R) {
                R--;
            }
            if (old.CAM) {
                CAM--;
            }
            if (old.STEAL) {
                STEAL--;
            }
            if (old.SWITCH) {
                SWITCH--;
            }
            if (old.BOOST) {
                BOOST--;
            }
            if(old.BOOST_LOCK){
                BOOST_LOCK--;
            }
            if (old.MV_CLICK) {
                MV_CLICK--;
            }
            if(old.MV_BALL){
                MV_BALL--;
            }
        }
    }
}
