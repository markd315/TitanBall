package client;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.util.Base64;

public class AuthServerInterface {

    public String token, refreshToken = null;
    public String gameId;
    public final int PORT = 444;
    private com.fasterxml.jackson.databind.JsonNode stat;

    public String springEndpoint() {
        return "https://zanzalaz.com:" + PORT + "/";
    }

    private static HttpClient client;

    static {
        try {
            // Set up TrustManager to trust all certificates (for testing or self-signed certificates)
            TrustManager[] trustAllCertificates = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            // Set up the SSL context with the TrustManager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCertificates, new java.security.SecureRandom());

            // Set up the HttpClient with SSL
            client = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();

            // You can store this client in a static field for later usage
            // This client will be used for authentication and other requests

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method to authenticate with username and password
    public String authenticate(String username, String password) {
        try {
            // Create the basic auth header using base64 encoding
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

            // Build the HTTP request for login
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(springEndpoint() + "login"))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + encodedAuth) // Set basic authentication header
                    .POST(HttpRequest.BodyPublishers.ofString("{\"usernameOrEmail\":\"" + username + "\", \"password\":\"" + password + "\"}"))
                    .build();

            // Send the request using HttpClient
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Print the response status and body
            System.out.println("statusCode = " + response.statusCode());
            String responseBody = response.body();

            // Assuming the response contains JSON with tokens
            // Parse the response body using some JSON parsing library (e.g., Jackson, org.json)
            ObjectMapper mapper = new ObjectMapper();
            JsonNode body = mapper.readTree(responseBody);
            token = body.get("accessToken").asText();
            refreshToken = body.get("refreshToken").asText();

            retry401Catch503("stat", getEmailFromToken(token));

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return token;
    }

    private String getEmailFromToken(String jwtToken) {
        Base64.Decoder decoder = Base64.getUrlDecoder();
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

    public int refresh() throws ConnectException {
        return refresh(this.refreshToken);
    }

    public int refresh(String inputRt) throws ConnectException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(springEndpoint() + "refresh"))
                    .header("Authorization", "Bearer " + inputRt)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())  // No body in the request
                    .build();


            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("statusCode = " + response.statusCode());

            // Parse the response body using Jackson
            ObjectMapper mapper = new ObjectMapper();
            JsonNode body = mapper.readTree(response.body());
            this.token = body.get("accessToken").asText();
            this.refreshToken = body.get("refreshToken").asText();

            // Retry 401 / handle 503
            retry401Catch503("stat", getEmailFromToken(token));

            return response.statusCode();
        } catch (Exception e) {
            e.printStackTrace();
            return 401; // Return unauthorized if something fails
        }
    }

    public void retry401Catch503(String type, String param) throws ConnectException {
        int response = 401;
        switch (type) {
            case "join":
                response = joinRequest(param);
                break;
            case "leave":
                response = leaveRequest();
                break;
            case "stat":
                response = statRequest(param);
                break;
            case "check":
                response = checkRequest();
                break;
        }
        while (response == 401) {
            token = null;
            System.out.println("Session expired, refreshing token");
            refresh(refreshToken);  // Refresh the token when expired
            switch (type) {
                case "join":
                    response = joinRequest(param);
                    break;
                case "leave":
                    response = leaveRequest();
                    break;
                case "stat":
                    response = statRequest(param);
                    break;
                case "check":
                    response = checkRequest();
                    break;
            }
        }
        if (response == 503) {
            throw new RuntimeException("Server is shutting down");  // Replace UnirestException with RuntimeException
        }
    }


    public int joinRequest(String tournamentCode) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(springEndpoint() + "join"))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .uri(URI.create(springEndpoint() + "join?tournamentCode=" + tournamentCode))
                    .GET()  // You can replace with .POST() or other HTTP methods as needed
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("statusCode = " + response.statusCode());
            gameId = response.body();
            System.out.println("gameId = " + gameId);

            return response.statusCode();
        } catch (Exception e) {
            e.printStackTrace();
            return 401; // Return an unauthorized status in case of exception
        }
    }

    public int leaveRequest() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(springEndpoint() + "leave"))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("statusCode = " + response.statusCode());
            gameId = response.body();
            System.out.println("gameId = " + gameId);

            return response.statusCode();
        } catch (Exception e) {
            e.printStackTrace();
            return 401; // Return an unauthorized status in case of exception
        }
    }

    public int statRequest(String email) {
        try {
            // JSON body
            String jsonBody = "{\"email\":\"" + email + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(springEndpoint() + "stat"))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("statusCode = " + response.statusCode());

            // Deserialize JSON response
            ObjectMapper mapper = new ObjectMapper();
            stat = mapper.readTree(response.body());
            System.out.println("gameId = " + gameId);

            return response.statusCode();
        } catch (Exception e) {
            e.printStackTrace();
            return 401; // Return an unauthorized status in case of exception
        }
    }

    public int checkRequest() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(springEndpoint() + "gamecheck"))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("statusCode = " + response.statusCode());
            this.gameId = response.body();
            System.out.println("gameId = " + gameId);

            return response.statusCode();
        } catch (Exception e) {
            e.printStackTrace();
            return 401; // Return an unauthorized status in case of exception
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

    public Rank getRank1v1() {
        int games = stat.get("wins_1v1").intValue() +
                stat.get("losses_1v1").intValue() +
                stat.get("ties_1v1").intValue();
        return rank(games, stat.get("rating_1v1").doubleValue());
    }

    public int getTopten1v1() {
        return stat.get("rank1v1").intValue();
    }

    public int getTopten() {
        return stat.get("rank").intValue();
    }

    public Rank getRank3v3() {
        int games = stat.get("wins").intValue() +
                stat.get("losses").intValue() +
                stat.get("ties").intValue();
        return rank(games, stat.get("rating").doubleValue());
    }

    public Rank rank(int games, double rating) {
        if (games < 10 || rating < 950.0) { //should be the bottom 40% or so, plus noobs
            return Rank.BRONZE;
        }
        if (rating < 1100.0) { //should be the bottom 75%
            return Rank.SILVER;
        }
        if (rating < 1200.0) { //should be 75 - 87
            return Rank.GOLD;
        }
        if (rating < 1250.0) { //should be 87-90
            return Rank.GOLD_HALO;
        }
        if (rating < 1380.0) { //top 5% //1400 would be 93-96.5
            return Rank.DIAMOND;
        }
        if (rating < 1550.0) { //1500 would be 96.5 - 98
            return Rank.MASTER;
        } else {
            return Rank.GRANDMASTER; //top 2% or so
        }
    }
}
