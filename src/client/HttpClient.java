package client;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.json.JSONObject;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

public class HttpClient {

    public String token, refreshToken = null;
    public String gameId;
    public final int PORT = 443;
    public String springEndpoint() {
        return "https://zanzalaz.com:" + PORT + "/";
    }

    static{
        SSLContext sslcontext = null;
        try {
            sslcontext = SSLContexts.custom()
                    .loadTrustMaterial(null, new TrustSelfSignedStrategy())
                    .build();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }

        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext);
        CloseableHttpClient httpclient = HttpClients.custom()
                .setSSLSocketFactory(sslsf)
                .build();
        Unirest.setHttpClient(httpclient);
    }

    public String authenticate(String un, String pass) {
        try {
            HttpResponse<JsonNode> response = Unirest.post(springEndpoint() + "login")
                    .header("accept", "application/json")
                    .header("Content-Type", "application/json")
                    .body("{\"usernameOrEmail\":\"" + un + "\"," +
                            " \"password\" :\"" + pass + "\"}")
                    .asJson();
            System.out.println("statusCode = " + response.getStatus());
            token = response.getBody().getObject().get("accessToken").toString();
            refreshToken = response.getBody().getObject().get("refreshToken").toString();
        } catch (Exception ex) {
        }
        return token;
    }

    public int refresh() throws UnirestException {
        return refresh(this.refreshToken);
    }

    public int refresh(String inputRt) throws UnirestException {
        try {
            HttpResponse<JsonNode> response = Unirest.post(springEndpoint() + "refresh")
                    .header("Authorization", "Bearer " + inputRt)
                    .asJson();
            System.out.println("statusCode = " + response.getStatus());
            JSONObject body = response.getBody().getObject();
            this.token = body.getString("accessToken");
            this.refreshToken = body.getString("refreshToken");
            return response.getStatus();
        }catch (Exception e){
            return 401;
        }
    }

    public void join() throws UnirestException {
        while (joinRequest() == 401) {
            token = null;
            System.out.println("Session expired, refreshing token");
            refresh(refreshToken);
        }
    }

    public int joinRequest() throws UnirestException {
        try {
            HttpResponse<String> response = Unirest.get(springEndpoint() + "join")
                    .header("Authorization", "Bearer " + token)
                    .asString();
            System.out.println("statusCode = " + response.getStatus());
            gameId = response.getBody();
            System.out.println("gameId = " + gameId);
            return response.getStatus();
        }catch (Exception e){
            return 401;
        }
    }

    public void leave() throws UnirestException {
        while (leaveRequest() == 401) {
            token = null;
            System.out.println("Session expired, refreshing token");
            refresh(refreshToken);
        }
    }

    public int leaveRequest() throws UnirestException {
        try {
            HttpResponse<String> response = Unirest.get(springEndpoint() + "leave")
                    .header("Authorization", "Bearer " + token)
                    .asString();
            System.out.println("statusCode = " + response.getStatus());
            gameId = response.getBody();
            System.out.println("gameId = " + gameId);
            return response.getStatus();
        }catch (Exception e){
            return 401;
        }
    }

    public void stat() throws UnirestException {
        while (statRequest() == 401) {
            token = null;
            System.out.println("Session expired, refreshing token");
            refresh(refreshToken);
        }
    }

    public int statRequest(){
        try {
            HttpResponse<String> response = Unirest.get(springEndpoint() + "stat")
                    .header("Authorization", "Bearer " + token)
                    .asString();

        System.out.println("statusCode = " + response.getStatus());
        gameId = response.getBody();
        System.out.println("gameId = " + gameId);
        return response.getStatus();
        }
    catch (Exception e){
        return 401;
    }
    }

    public void check() throws UnirestException {
        while (checkRequest() == 401) {
            token = null;
            System.out.println("Session expired, refreshing token");
            refresh(refreshToken);
        }
    }

    public int checkRequest(){
        try {
            HttpResponse<String> response = Unirest.get(springEndpoint() + "gamecheck")
                    .header("Authorization", "Bearer " + token)
                    .asString();
            System.out.println("statusCode = " + response.getStatus());
            gameId = response.getBody();
            System.out.println("gameId = " + gameId);
            return response.getStatus();
        }
        catch (Exception e){
            return 401;
        }
    }
}
