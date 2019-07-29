package client;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

public class HttpClient {

    public static String token = null;
    public static String gameId;

    public static String springEndpoint() {
        return "http://" + System.getenv("host") + ":8080/";
    }

    public static String authenticate() {
        try {
            String un = System.getenv("user");
            String pass = System.getenv("pass");
            HttpResponse<JsonNode> response = Unirest.post(springEndpoint() + "login")
                    .header("accept", "application/json")
                    .header("Content-Type", "application/json")
                    .body("{\"usernameOrEmail\":\"" + un + "\"," +
                            " \"password\" :\"" + pass + "\"}")
                    .asJson();
            System.out.println("statusCode = " + response.getStatus());
            System.out.println("auth result");
            System.out.println(response.getBody().getObject().toString());
            System.out.println(response.getBody().getObject().get("accessToken").toString());
            token = response.getBody().getObject().get("accessToken").toString();
            System.out.println("token = " + token);
        } catch (Exception ex) {
        }
        return token;
    }

    public static void join() throws UnirestException {
        while (joinRequest() == 401) {
            token = null;
            System.out.println("Session expired, refreshing token");
            authenticate();
        }
    }

    public static int joinRequest() throws UnirestException {
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

    public static void leave() throws UnirestException {
        while (leaveRequest() == 401) {
            token = null;
            System.out.println("Session expired, refreshing token");
            authenticate();
        }
    }

    public static int leaveRequest() throws UnirestException {
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

    public static void stat() throws UnirestException {
        while (statRequest() == 401) {
            token = null;
            System.out.println("Session expired, refreshing token");
            authenticate();
        }
    }

    public static int statRequest(){
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

    public static void check() throws UnirestException {
        while (checkRequest() == 401) {
            token = null;
            System.out.println("Session expired, refreshing token");
            authenticate();
        }
    }

    public static int checkRequest(){
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
