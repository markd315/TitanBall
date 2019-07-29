package networking;


import client.ClientPacket;

public class KeyDifferences {
    public int W = 0, A = 0, S = 0, D = 0, E = 0, R = 0, SPACE = 0, Q = 0, TAB = 0, Z = 0;

    public KeyDifferences(ClientPacket act, ClientPacket old) {
        if (act.W) {
            W++;
        }
        if (act.A) {
            A++;
        }
        if (act.S) {
            S++;
        }
        if (act.D) {
            D++;
        }
        if (act.E) {
            E++;
        }
        if (act.R) {
            R++;
        }
        if (act.SPACE) {
            SPACE++;
        }
        if (act.Q) {
            Q++;
        }
        if (act.Z) {
            Z++;
        }
        if (old != null) {
            if (old.W) {
                W--;
            }
            if (old.A) {
                A--;
            }
            if (old.S) {
                S--;
            }
            if (old.D) {
                D--;
            }
            if (old.E) {
                E--;
            }
            if (old.R) {
                R--;
            }
            if (old.SPACE) {
                SPACE--;
            }
            if (old.Q) {
                Q--;
            }
            if (old.Z) {
                Z--;
            }
        }
    }
}
