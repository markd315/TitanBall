package client;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

public class HttpClient {

    public String token, refreshToken = null;
    public String gameId;
    public final int PORT = 444;
    private com.fasterxml.jackson.databind.JsonNode stat;

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
            stat(getEmailFromToken(token));
        } catch (Exception ex) {
        }
        return token;
    }

    private String getEmailFromToken(String jwtToken){
        java.util.Base64.Decoder decoder = java.util.Base64.getUrlDecoder();
        String[] parts = jwtToken.split("\\."); // split out the "parts" (header, payload and signature)

        String payloadJson = new String(decoder.decode(parts[1]));
        ObjectMapper mapper = new ObjectMapper();
        try {
            com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(payloadJson);
            return json.get("sub").textValue();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "uh oh";
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
            stat(getEmailFromToken(token));
            return response.getStatus();
        }catch (Exception e){
            return 401;
        }
    }

    public void join(String tournamentCode) throws UnirestException {
        while (joinRequest(tournamentCode) == 401) {
            token = null;
            System.out.println("Session expired, refreshing token");
            refresh(refreshToken);
        }
    }

    public int joinRequest(String tournamentCode) throws UnirestException {
        try {
            HttpResponse<String> response = Unirest.get(springEndpoint() + "join")
                    .header("Authorization", "Bearer " + token)
                    .queryString("tournamentCode", tournamentCode)
                    .asString();
            System.out.println("statusCode = " + response.getStatus());
            gameId = response.getBody();
            System.out.println("gameId = " + gameId);
            return response.getStatus();
        }catch (Exception e){
            return 401;
        }
    }

    public void leave(){
        while (leaveRequest() == 401) {
            token = null;
            System.out.println("Session expired, refreshing token");
            try {
                refresh(refreshToken);
            } catch (UnirestException e) {
                e.printStackTrace();
            }
        }
    }

    public int leaveRequest(){
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

    public void stat(String email) throws UnirestException {
        while (statRequest(email) == 401) {
            token = null;
            System.out.println("Session expired, refreshing token");
            refresh(refreshToken);
        }
    }

    public int statRequest(String email){
        try {
            HttpResponse<String> response = Unirest.post(springEndpoint() + "stat")
                    .header("Authorization", "Bearer " + token)
                    .header("accept", "application/json")
                    .header("Content-Type", "application/json")
                    .body("{\"email\":\"" + email + "\"}")
                    .asString();

        System.out.println("statusCode = " + response.getStatus());
        ObjectMapper mapper = new ObjectMapper();
        stat = mapper.readTree(response.getBody());
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
            this.gameId = response.getBody();
            System.out.println("gameId = " + gameId);
            return response.getStatus();
        }
        catch (Exception e){
            return 401;
        }
    }

    public enum Rank {
        BRONZE,
        SILVER,
        GOLD,
        GOLD_HALO,
        DIAMOND,
        MASTER,
        GRANDMASTER
    }

    public Rank getRank1v1(){
        int games = stat.get("wins_1v1").intValue() +
                stat.get("losses_1v1").intValue()  +
                stat.get("ties_1v1").intValue();
        return rank(games, stat.get("rating_1v1").doubleValue() );
    }

    public int getTopten1v1(){
        return stat.get("rank1v1").intValue();
    }

    public int getTopten(){
        return stat.get("rank").intValue();
    }

    public Rank getRank3v3(){
        int games = stat.get("wins").intValue() +
                stat.get("losses").intValue()  +
                stat.get("ties").intValue();
        return rank(games, stat.get("rating").doubleValue() );
    }

    public Rank rank(int games, double rating){
        if(games < 10 || rating < 950.0){ //should be the bottom 40% or so, plus noobs
            return Rank.BRONZE;
        }
        if(rating < 1100.0) { //should be the bottom 75%
            return Rank.SILVER;
        }
        if(rating < 1200.0) { //should be 75 - 87
            return Rank.GOLD;
        }
        if(rating < 1250.0) { //should be 87-90
            return Rank.GOLD_HALO;
        }
        if(rating < 1380.0) { //top 5% //1400 would be 93-96.5
            return Rank.DIAMOND;
        }
        if(rating < 1550.0) { //1500 would be 96.5 - 98
            return Rank.MASTER;
        }else{
            return Rank.GRANDMASTER; //top 2% or so
        }
    }
}
