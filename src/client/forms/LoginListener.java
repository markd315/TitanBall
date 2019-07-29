package client.forms;

import client.HttpClient;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class LoginListener implements ActionListener {
    String un;
    String pw;
    HttpClient client;

    public void actionPerformed(ActionEvent e) {
        client.authenticate(un, pw);
    }
}
