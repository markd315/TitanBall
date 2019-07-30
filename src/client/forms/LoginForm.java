package client.forms;

import client.HttpClient;

import javax.swing.*;
import java.awt.*;

public class LoginForm extends JFrame{
    public HttpClient client;
    JLabel l1, l2, l3;
    JTextField tf1;
    JButton btn1;
    JPasswordField p1;
    public LoginForm(HttpClient client) {
        this.client = client;
        JFrame frame = new JFrame("Login Form");
        l1 = new JLabel("Login Form");
        l1.setForeground(Color.blue);
        l1.setFont(new Font("Serif", Font.BOLD, 20));

        l2 = new JLabel("Username");
        l3 = new JLabel("Password");
        tf1 = new JTextField();
        p1 = new JPasswordField();
        btn1 = new JButton("Login");

        l1.setBounds(100, 30, 400, 30);
        l2.setBounds(80, 70, 200, 30);
        l3.setBounds(80, 110, 200, 30);
        tf1.setBounds(300, 70, 200, 30);
        p1.setBounds(300, 110, 200, 30);
        btn1.setBounds(150, 160, 100, 30);

        frame.add(l1);
        frame.add(l2);
        frame.add(tf1);
        frame.add(l3);
        frame.add(p1);
        frame.add(btn1);

        frame.setSize(650, 400);
        frame.setLayout(null);
        frame.setVisible(true);
        LoginListener listener = new LoginListener();
        listener.client = this.client;
        btn1.addActionListener(listener); //Wait for submit
        while(listener.client.token == null){
            try {
                listener.un = tf1.getText();
                listener.pw = String.valueOf(p1.getPassword());
            }catch (Exception ex){

            }
            //JOptionPane.showMessageDialog(this,"Incorrect login or password","Error",JOptionPane.ERROR_MESSAGE);
        }
        this.client = listener.client;
    }
}