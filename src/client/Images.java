package client;// client.src.Images
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class Images {
    protected int x;
    protected int y;  
    protected int width;
    protected int height;   
    protected Image image;     
    public Images() {
	}
    protected void loadImage(String imageName) {		
        ImageIcon ii = new ImageIcon(imageName);
        image = ii.getImage();
    }
    public Image getImage() {
        return image;
    }
    public void SetX(int Q) {
        x= Q;
    }
    public void  SetY(int W) {
        y= W;
    }	
    public int getX() {
        return x;
    }
    public int getY() {
        return y;
    }		
    // Metodi per la restituzione del quadrato
    protected void getImageDimensions() {
		width = image.getWidth(null);
        height = image.getHeight(null);
    }  
	// Un metodo che crea un rettangolo intorno allo sprite.
    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    public static BufferedImage resize(BufferedImage source,
                                       int width, int height) {
        return progressiveResize(source, width, height,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    private static BufferedImage progressiveResize(BufferedImage source,
                                                   int width, int height, Object hint) {
        int w = Math.max(source.getWidth()/2, width);
        int h = Math.max(source.getHeight()/2, height);
        BufferedImage im = commonResize(source, w, h, hint);
        while (w != width || h != height) {
            BufferedImage prev = im;
            w = Math.max(w/2, width);
            h = Math.max(h/2, height);
            im = commonResize(prev, w, h, hint);
            prev.flush();
        }
        return im;
    }

    private static BufferedImage commonResize(BufferedImage source,
                                              int width, int height, Object hint) {
        BufferedImage im = new BufferedImage(width, height,
                source.getType());
        Graphics2D g = im.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return im;
    }

    public static BufferedImage getBufferedFrom(Image im, int x, int y){
        // Create a buffered image with transparency
        if(im == null || im.getHeight(null) == -1){
            return null;
        }
        BufferedImage bimage = new BufferedImage(im.getWidth(null), im.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        // Draw the image on to the buffered image
        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(im, 0, 0, null);
        bGr.dispose();
        BufferedImage ima = resize(bimage, x, y);
        return ima;
    }
}
    
  
