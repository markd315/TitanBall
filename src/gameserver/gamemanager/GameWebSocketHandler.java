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
        ClientPacket packet = mapper.readValue(payload, ClientPacket.class);
        if (packet.token != null && !packet.token.isEmpty()) {
            boolean valid = false;
            Object cachedToken = session.getAttributes().get("validToken");
            Long expiry = (Long) session.getAttributes().get("tokenExpiry");
            long now = System.currentTimeMillis();
            if (packet.token.equals(cachedToken) && expiry != null && now < expiry) {
                valid = true;
            } else if (jwtTokenProvider.validateToken(packet.token)) {
                session.getAttributes().put("validToken", packet.token);
                session.getAttributes().put("tokenExpiry", now + 60_000L);
                valid = true;
            }
            if (valid) {
                WebSocketPlayerConnection connection = (WebSocketPlayerConnection) session.getAttributes()
                        .computeIfAbsent("playerConn", k -> new WebSocketPlayerConnection(session));
                ServerApplication.delegatePacket(connection, packet);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        System.out.println("WebSocket connection closed: " + session.getId());
    }
}
