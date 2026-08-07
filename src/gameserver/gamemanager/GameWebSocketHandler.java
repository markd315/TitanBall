package gameserver.gamemanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import networking.ClientPacket;
import networking.WebSocketPlayerConnection;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import authserver.jwt.JwtTokenProvider;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JwtTokenProvider jwtTokenProvider;

    public GameWebSocketHandler(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("New WebSocket connection: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload.contains("\"type\":\"ping\"")) {
            session.sendMessage(new TextMessage(payload.replace("\"ping\"", "\"pong\"")));
            return;
        }
        ClientPacket packet = mapper.readValue(payload, ClientPacket.class);
        if (packet.token != null && !packet.token.isEmpty()) {
            if (jwtTokenProvider.validateToken(packet.token)) {
                WebSocketPlayerConnection connection = new WebSocketPlayerConnection(session);
                ServerApplication.delegatePacket(connection, packet);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        System.out.println("WebSocket connection closed: " + session.getId());
    }
}
