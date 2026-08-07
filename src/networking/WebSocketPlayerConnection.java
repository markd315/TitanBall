package networking;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

public class WebSocketPlayerConnection extends PlayerDivider {
    private WebSocketSession session;

    public WebSocketPlayerConnection(WebSocketSession session) {
        this.session = session;
        this.id = session.getId().hashCode();
    }

    public WebSocketPlayerConnection(java.util.List<Integer> possibleSelection, WebSocketPlayerConnection other, String email) {
        super(possibleSelection);
        this.session = other.session;
        this.id = other.id;
        this.email = email;
    }

    private final Object sendLock = new Object();

    public void setClient(WebSocketPlayerConnection other) {
        synchronized (sendLock) {
            this.session = other.session;
            this.id = other.id;
        }
    }

    public boolean isConnected() {
        synchronized (sendLock) {
            return session != null && session.isOpen();
        }
    }

    public void sendJson(String json) {
        synchronized (sendLock) {
            try {
                if (session != null && session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void close() {
        synchronized (sendLock) {
            try {
                if (session != null && session.isOpen()) {
                    session.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
