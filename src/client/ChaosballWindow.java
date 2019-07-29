package client;
/* client.src.ChaosballWindow for Java main client
 * Chaosball Alpha testing version
 * */

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class ChaosballWindow extends JFrame {
    public ChaosballWindow() throws IOException {
        initUI();
    }

    private void initUI() throws IOException {
        int xSize = 1920;
        int ySize = 1080;
        add(new client.ChaosballClient(xSize, ySize));
        setTitle("Chaosball Alpha");
        //GraphicsConfiguration config = this.getGraphicsConfiguration();
        //Rectangle usableBounds = SunGraphicsEnvironment.getUsableBounds(config.getDevice());
        setSize(xSize, ySize);
        //setAlwaysOnTop(true);
        //setOpaque(false);
        //getContentPane().add(BorderLayout.CENTER, this);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setUndecorated(true);
        setResizable(false);
    }

    public static void main(String[] args) {

        EventQueue.invokeLater(() -> {
            ChaosballWindow ex = null;
            try {
                ex = new ChaosballWindow();
            } catch (IOException e) {
                e.printStackTrace();
            }
            ex.setVisible(true);
        });
    }
}

//field locations: Y=618 is center Y
//400 units wide (too short on top as is)

//---------------------------------------------------------------------
