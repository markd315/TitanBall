package gameserver.gamemanager;

import io.socket.engineio.server.EngineIoServer;
import io.socket.engineio.server.EngineIoServerOptions;
import io.socket.engineio.server.JettyWebSocketHandler;
import io.socket.socketio.server.SocketIoServer;
import org.eclipse.jetty.http.pathmap.ServletPathSpec;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeFilter;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.EnumSet;

public class ServerWrapper {
    private final int mPort;
    private final Server mServer;
    private final EngineIoServerOptions eioOptions;
    private final EngineIoServer mEngineIoServer;
    private final SocketIoServer mSocketIoServer;

    public ServerWrapper(String ip, int port) {
        mPort = port;
        mServer = new Server(new InetSocketAddress(ip, mPort));
        eioOptions = EngineIoServerOptions.newFromDefault();
        eioOptions.setCorsHandlingDisabled(true); // Disable Engine.IO CORS handling

        mEngineIoServer = new EngineIoServer(eioOptions);
        mSocketIoServer = new SocketIoServer(mEngineIoServer);

        // Set up Jetty server logging and handlers
        System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog");
        System.setProperty("org.eclipse.jetty.LEVEL", "OFF");

        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        servletContextHandler.setContextPath("/");

        // Configure CrossOriginFilter to handle CORS for all paths
        FilterHolder cors = new FilterHolder(new CrossOriginFilter());
        cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        cors.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "OPTIONS, GET, POST, HEAD");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With, Content-Type, Accept, Origin, Cache-Control");
        cors.setInitParameter(CrossOriginFilter.CHAIN_PREFLIGHT_PARAM, "false");
        servletContextHandler.addFilter(cors, "/*", EnumSet.of(DispatcherType.REQUEST));

        servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
                mEngineIoServer.handleRequest(new HttpServletRequestWrapper(request) {
                    @Override
                    public boolean isAsyncSupported() {
                        return false;
                    }
                }, response);
            }
        }), "/*");

        try {
            WebSocketUpgradeFilter webSocketUpgradeFilter = WebSocketUpgradeFilter.configureContext(servletContextHandler);
            webSocketUpgradeFilter.addMapping(
                    new ServletPathSpec("/*"),
                    (servletUpgradeRequest, servletUpgradeResponse) -> new JettyWebSocketHandler(mEngineIoServer));
        } catch (ServletException ex) {
            ex.printStackTrace();
        }

        HandlerList handlerList = new HandlerList();
        handlerList.setHandlers(new Handler[]{servletContextHandler});
        mServer.setHandler(handlerList);
    }

    public void startServer() throws Exception {
        mServer.start();
    }

    public void stopServer() throws Exception {
        mServer.stop();
    }

    public int getPort() {
        return mPort;
    }

    public SocketIoServer getSocketIoServer() {
        return mSocketIoServer;
    }

}