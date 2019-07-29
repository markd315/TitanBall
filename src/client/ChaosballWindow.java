package client;
/* loginClient.src.ChaosballWindow for Java main client
 * Chaosball Alpha testing version
 * */

import client.forms.LoginForm;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Scanner;

public class ChaosballWindow extends JFrame {
    private static HttpClient loginClient;

    public ChaosballWindow(HttpClient loginClient) {
        this.loginClient = loginClient;
        initUI();
    }

    private void initUI() {
        int xSize = 1920;
        int ySize = 1080;
        ChaosballClient client = new ChaosballClient(xSize, ySize, loginClient);
        add(client);

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

    private static void writeRefreshToken(String refreshToken) throws IOException {
        FileOutputStream fio = new FileOutputStream(new File("session.jwt"));
        fio.write(refreshToken.getBytes());
        fio.close();
    }

    public static void main(String[] args) throws IOException {
        loginClient = new HttpClient();
        try{
            Scanner sc = new Scanner(new File("session.jwt"));
            loginClient.refreshToken = sc.nextLine();
            sc.close();
            if(loginClient.refresh(loginClient.refreshToken) != 200){
                throw new Exception("Refresh token invalid!");
            }
            writeRefreshToken(loginClient.refreshToken);
        }
        catch(Exception ex) {
            LoginForm authFrame = new LoginForm(loginClient);
            authFrame.setVisible(false);
            authFrame.dispose();
            authFrame = null;
            System.gc();
            writeRefreshToken(loginClient.refreshToken);
        }
        String finalToken = loginClient.token;
        String finalRefreshToken = loginClient.refreshToken;
        System.out.println(finalToken + " bearer");
        System.out.println(finalRefreshToken + " refresh");
        EventQueue.invokeLater(() -> {
            ChaosballWindow fr = new ChaosballWindow(loginClient);
            fr.setVisible(true);
        });
    }
}

//field locations: Y=618 is center Y
//400 units wide (too short on top as is)

//---------------------------------------------------------------------
