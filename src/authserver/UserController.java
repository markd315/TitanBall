package authserver;

import authserver.jwt.JwtTokenProvider;
import authserver.matchmaking.Matchmaker;
import authserver.models.DTO.ActivationRequest;
import authserver.models.DTO.LoginRequest;
import authserver.models.DTO.RenewRequest;
import authserver.models.DTO.UserDTO;
import authserver.models.Premade;
import authserver.models.PremadeDTO;
import authserver.models.User;
import authserver.models.responses.JwtAuthenticationResponse;
import authserver.models.responses.UserResponse;
import authserver.users.identities.UserService;
import authserver.users.premades.PremadeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Path("/users")
public class UserController {
    private static final String M2M_AUTH = "VERY SECRET PAYPAL PRIVATE KEY";

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    UserService userService;

    @Autowired
    PremadeService premadeService;

    @Autowired
    Matchmaker userPool;

    @Autowired
    JwtTokenProvider tokenProvider;

    @Autowired
    AuthenticationManager authenticationManager;

    @POST
    @Path("/entrypoint")
    public Response authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        System.out.println("reached method");
        System.out.println(loginRequest);
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsernameOrEmail(),
                        loginRequest.getPassword()
                )
        );
        System.out.println(authentication);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);
        String ref = tokenProvider.generateRefreshToken(authentication);
        return Response.status(200).type(javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE).entity(new JwtAuthenticationResponse(jwt, ref)).build();
    }

    @POST
    @Path("/refresh")
    public Response reauthenticateUser(@RequestHeader String authorization) {
        String[] newTokens = tokenProvider.bothRefreshed(authorization);
        if(newTokens.length != 2){
            return Response.status(401).entity("Refresh token not/no longer valid").build();
        }
        return Response.status(200).type(javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE).entity(new JwtAuthenticationResponse(newTokens[0], newTokens[1])).build();
    }

    @POST
    @Path("/activate")
    public Response activateUser(@Valid @RequestBody ActivationRequest activationRequest) throws Exception {
        User user;
        try{
            user = userService.findUserByEmail(activationRequest.getUsernameOrEmail());
        }
        catch (Exception ex1){
            user = userService.findUserByUsername(activationRequest.getUsernameOrEmail());
        }
        if(user == null){
            return Response.status(400).build();
        }
        if(user.activate(activationRequest.getKey())){
            user.setEnabled(true);
            user.renew(14);
            userService.saveUser(user);
            return Response.ok().build();
        }else{
            return Response.status(401).build();
        }
    }

    @POST
    @Path("/renew")
    public Response renewUser(@Valid @RequestBody RenewRequest loginRequest) throws Exception {
        User user;
        try{
            user = userService.findUserByEmail(loginRequest.getUsernameOrEmail());
        }
        catch (Exception ex1){
            user = userService.findUserByUsername(loginRequest.getUsernameOrEmail());
        }
        if(user == null){
            return Response.status(400).build();
        }
        if(loginRequest.getM2mAuth().equals(M2M_AUTH)){
            user.renew(loginRequest.getDays());
            userService.saveUser(user);
            return Response.ok().build();
        }else{
            return Response.status(401).build();
        }
    }

    @GET
    @Path("/gamecheck")
    public Response gameCheck() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Response.status(200).type(javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE).entity(userPool.findGame(auth)).build();
    }

    @GET
    @Path("/join")
    public Response joinLobby(@QueryParam("tournamentCode") String tournamentCode, @QueryParam("teamname") String teamname) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        userPool.registerIntent(auth, tournamentCode, teamname);
        return Response.ok().entity(userPool.findGame(auth)).build();
    }

    @GET
    @Path("/leave")
    public Response leaveLobby() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        userPool.removeIntent(auth);
        String game = userPool.findGame(auth);
        if(!game.equals("NOT QUEUED")){
            return Response.status(400).entity(game).build();
        }
        return Response.ok().type(javax.ws.rs.core.MediaType.TEXT_PLAIN_TYPE).entity("Successfully withdrawn").build();
    }

    @POST
    @Path("/stat")
    public Response userStats(@RequestBody @Valid UserDTO userinput) {
        User user = userService.findUserByEmail(userinput.getEmail());
        List<User> rate3v3 = userService.findAll();
        rate3v3.sort((User o1, User o2) -> (int) (o2.getRating()- o1.getRating()));
        List<User> rate1v1 = new ArrayList<>();
        rate1v1.addAll(rate3v3);
        rate1v1.sort((User o1, User o2) -> (int) (o2.getRating_1v1()- o1.getRating_1v1()));
        int rating = 999;
        for(int i=0; i< rate3v3.size(); i++){
            //System.out.println("hit");
            //System.out.println(user.getEmail());
            //System.out.println(rate3v3.get(i).getEmail());
            if(rate3v3.get(i).getEmail().equals(user.getEmail())){
                rating = i+1;
            }
        }
        int rating1v1 = 999;
        for(int i=0; i< rate1v1.size(); i++){
            if(rate1v1.get(i).getEmail().equals(user.getEmail())){
                rating1v1 = i+1;
            }
        }
        return Response.status(200).type(javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE).entity(new UserResponse(user, rating, rating1v1)).build();
    }

    @POST
    @Path("addUser")
    @Produces(MediaType.APPLICATION_JSON_UTF8_VALUE)
    public Response addUser(@RequestBody @Valid UserDTO userinput) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        /*if(!isRole(auth, "ADMIN")){
            return Response(null, headers, HttpStatus.FORBIDDEN);
        }*/
        User user = new User();
        if ((userinput == null) || userinput.getUsername() == null || userinput.getPassword() == null) {
            //errors.addAllErrors(bindingResult);
            //headers.add("errors", errors.toJSON());
            return Response.status(400).entity(new UserResponse(user, 999, 999)).build();
        }
        user.setUsername(userinput.getUsername());
        String hashed = passwordEncoder.encode(userinput.getPassword());
        user.setPassword(hashed);
        user.setEmail(userinput.getEmail());
        user.setRole(userinput.getRole());
        //TODO send activation code to email, don't just log it. Use work code?
        System.out.println("Activation: " + user.getActivation());
        if(user.getRole() == null){
            user.setRole("USER");//default
        }

        this.userService.saveUser(user);
        return Response.status(202).type(javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE).entity(new UserResponse(user, 999, 999)).build();
    }

    @POST
    @Path("/teamcheck")
    @Produces(MediaType.APPLICATION_JSON_UTF8_VALUE)
    public Response checkTeam(
            @RequestBody PremadeDTO input) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        //return code and body
        Optional<Premade> getback = this.premadeService.findPremadeByTeamname(input.teamname);
        if(getback.isPresent()){
            Premade pm = getback.get();
            if(email.equals(pm.getTopuser()) ||
                    email.equals(pm.getMiduser()) ||
                    email.equals(pm.getBotuser())){
                return Response.status(202).type(javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE).entity(getback.get()).build();
            }
            return Response.status(403).build();
        }else{
            return Response.status(404).build();
        }
    }

    @POST
    @Path("/teamup")
    @Produces(MediaType.APPLICATION_JSON_UTF8_VALUE)
    public Response registerTeam(@RequestBody @Valid PremadeDTO input, @QueryParam("queue") boolean queue) throws MalformedURLException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(input.teamname == null || input.teamname.equals("")){
            return Response.status(400).build();
        }
        String username = auth.getName();
        Premade premade = new Premade(input, queue ? username : "");
        try {
            this.premadeService.enterTeamDetails(premade, username);
        } catch (Exception e) {
            return Response.status(409).build();
        }
        //return code and body
        Optional<Premade> getback = this.premadeService.findPremadeByTeamname(premade.getTeamname());
        if(getback.isPresent()){
            return Response.status(202).build();
        }
        else{
            return Response.status(404).build();
        }
    }
}