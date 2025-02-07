package client.forms;

import client.HttpClient;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.geometry.Pos;


public class LoginForm extends Stage {
    private Label l1, l2, l3;
    TextField tf1;
    PasswordField p1;
    private Button btn1;

    public LoginForm(HttpClient client, Runnable onLoginSuccess) {

        // Setup Stage
        setTitle("Login Form");

        // Create labels and fields
        l1 = new Label("Login Form");
        l1.setStyle("-fx-font-size: 20px; -fx-text-fill: blue;");

        l2 = new Label("Username");
        tf1 = new TextField();

        l3 = new Label("Password");
        p1 = new PasswordField();

        btn1 = new Button("Login");

        // Layout
        VBox layout = new VBox(10);
        layout.getChildren().addAll(l1, l2, tf1, l3, p1, btn1);
        layout.setAlignment(Pos.CENTER);

        Scene scene = new Scene(layout, 400, 300);
        setScene(scene);

        // Add LoginListener to the button
        LoginListener listener = new LoginListener(client, tf1, p1, this, onLoginSuccess);
        btn1.setOnAction(listener);

        show();
    }
}
