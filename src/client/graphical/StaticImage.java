package client.graphical;
import javax.swing.*;
import java.awt.*;

public class StaticImage extends Images {
    public Image image;
    public StaticImage() {
	}	
    public void loadImage(String imageName) {
        ImageIcon rsi = new ImageIcon(imageName);
        image = rsi.getImage();
    }
    public void loadImage(String imageName, int x, int y) {
        ImageIcon rsi = new ImageIcon(imageName);
        image = getBufferedFrom(rsi.getImage(), x, y);
    }
    public Image getImage() {

        return image;
    }
}
