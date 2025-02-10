package client.forms;

import client.AuthServerInterface;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;


public class LoginListener implements EventHandler<ActionEvent> {
    private final Runnable onLoginSuccess;
    private AuthServerInterface client;
    private TextField tf1;
    private PasswordField p1;
    private LoginForm form;
    private String token;

    public LoginListener(AuthServerInterface client, TextField tf1, PasswordField p1, LoginForm form, Runnable onLoginSuccess) {
        this.client = client;
        this.tf1 = tf1;
        this.p1 = p1;
        this.form = form;
        this.onLoginSuccess = onLoginSuccess;
    }

    @Override
    public void handle(ActionEvent event) {
        String username = tf1.getText();
        String password = p1.getText();
        this.token = client.authenticate(username, password);
        if (this.token != null) {
            onLoginSuccess.run();
        }
        form.close();
    }

    public void setForm(LoginForm authForm) {
        this.tf1 = authForm.tf1;
        this.p1 = authForm.p1;
        this.form = authForm;
    }
}
