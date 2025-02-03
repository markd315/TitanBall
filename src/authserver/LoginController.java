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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
public class LoginController {
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
    private boolean shutDownMode = false;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsernameOrEmail(),
                        loginRequest.getPassword()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);
        String ref = tokenProvider.generateRefreshToken(authentication);
        return ResponseEntity.ok(new JwtAuthenticationResponse(jwt, ref));
    }

     @PostMapping("/shutdown")
    public ResponseEntity<?> gracefulShutdownServer(@Valid @RequestBody LoginRequest loginRequest) {
         //respond with 503 for all future requests because the server is shutting down
         //TODO require root level credentials to shut down server
         shutDownMode = true;
         userPool.clearWaitingPools();
         return ResponseEntity.ok(new JwtAuthenticationResponse("Shutting down", null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> reauthenticateUser(@RequestHeader String authorization) {
        String[] newTokens = tokenProvider.bothRefreshed(authorization);
        if(newTokens.length != 2){
            return ResponseEntity.status(401).body("Refresh token not/no longer valid");
        }
        return ResponseEntity.ok(new JwtAuthenticationResponse(newTokens[0], newTokens[1]));
    }

    @PostMapping("/activate")
    public ResponseEntity<?> activateUser(@Valid @RequestBody ActivationRequest activationRequest) throws Exception {
        User user;
        try{
            user = userService.findUserByEmail(activationRequest.getUsernameOrEmail());
        }
        catch (Exception ex1){
            user = userService.findUserByUsername(activationRequest.getUsernameOrEmail());
        }
        if(user == null){
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        if(user.activate(activationRequest.getKey())){
            user.setEnabled(true);
            user.renew(14);
            userService.saveUser(user);
            return new ResponseEntity<>(HttpStatus.OK);
        }else{
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping("/renew")
    public ResponseEntity<?> renewUser(@Valid @RequestBody RenewRequest loginRequest) throws Exception {
        User user;
        try{
            user = userService.findUserByEmail(loginRequest.getUsernameOrEmail());
        }
        catch (Exception ex1){
            user = userService.findUserByUsername(loginRequest.getUsernameOrEmail());
        }
        if(user == null){
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        if(loginRequest.getM2mAuth().equals(M2M_AUTH)){
            user.renew(loginRequest.getDays());
            userService.saveUser(user);
            return new ResponseEntity<>(HttpStatus.OK);
        }else{
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }

    @RequestMapping("/gamecheck")
    public ResponseEntity<String> gameCheck(Authentication auth) {
        if (shutDownMode) {
            return new ResponseEntity<>("Shutting down", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return new ResponseEntity<>(userPool.findGame(auth), HttpStatus.OK);
    }

    @RequestMapping("/join")
    public ResponseEntity<String> joinLobby(Authentication auth,
              @RequestParam String tournamentCode, @RequestParam(required = false) String teamname) throws IOException {
        if (shutDownMode) {
            return new ResponseEntity<>("Shutting down", HttpStatus.SERVICE_UNAVAILABLE);
        }
        userPool.registerIntent(auth, tournamentCode, teamname);
        return new ResponseEntity<>(userPool.findGame(auth), HttpStatus.OK);
    }

    @RequestMapping("/leave")
    public ResponseEntity<String> leaveLobby(Authentication auth) {
        if (shutDownMode) {
            return new ResponseEntity<>("Shutting down", HttpStatus.SERVICE_UNAVAILABLE);
        }
        userPool.removeIntent(auth);
        String game = userPool.findGame(auth);
        if(!game.equals("NOT QUEUED")){
            return new ResponseEntity<>(game, HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>("Successfully withdrawn", HttpStatus.OK);
    }

    @RequestMapping(value = "/stat", method = RequestMethod.POST)
    public ResponseEntity<UserResponse> userStats(@RequestBody @Valid UserDTO userinput) {
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
            if(rate3v3.get(i).getEmail().equals(user.getEmail())){ //TODO npe here
                rating = i+1;
            }
        }
        int rating1v1 = 999;
        for(int i=0; i< rate1v1.size(); i++){
            if(rate1v1.get(i).getEmail().equals(user.getEmail())){
                rating1v1 = i+1;
            }
        }
        return new ResponseEntity<>(new UserResponse(user, rating, rating1v1), HttpStatus.OK);
    }

    @RequestMapping(value = "", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<UserResponse> addUser(@RequestBody @Valid UserDTO userinput, BindingResult bindingResult, Authentication auth) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        /*if(!isRole(auth, "ADMIN")){
            return new ResponseEntity<>(null, headers, HttpStatus.FORBIDDEN);
        }*/
        User user = new User();
        if (bindingResult.hasErrors() || (userinput == null) || userinput.getUsername() == null || userinput.getPassword() == null) {
            //errors.addAllErrors(bindingResult);
            //headers.add("errors", errors.toJSON());
            return new ResponseEntity<>(new UserResponse(user, 999, 999), headers, HttpStatus.BAD_REQUEST);
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
        return new ResponseEntity<>(new UserResponse(user, 999, 999), headers, HttpStatus.CREATED);
    }

    @RequestMapping(value = "/teamcheck", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<Premade> checkTeam(
            @RequestBody PremadeDTO input, Authentication auth) {
        String email = auth.getName();
        //return code and body
        Optional<Premade> getback = this.premadeService.findPremadeByTeamname(input.teamname);
        if(getback.isPresent()){
            Premade pm = getback.get();
            if(email.equals(pm.getTopuser()) ||
                    email.equals(pm.getMiduser()) ||
                    email.equals(pm.getBotuser())){
                return new ResponseEntity<>(getback.get(), HttpStatus.CREATED);
            }
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }else{
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @RequestMapping(value = "/teamup", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<Premade> registerTeam(@RequestBody @Valid PremadeDTO input,
                                                Authentication auth, @RequestParam(required = false) boolean queue) {
        if(input.teamname == null || input.teamname.equals("")){
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        String username = auth.getName();
        Premade premade = new Premade(input, queue ? username : "");
        try {
            this.premadeService.enterTeamDetails(premade, username);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        //return code and body
        Optional<Premade> getback = this.premadeService.findPremadeByTeamname(premade.getTeamname());
        return getback.map(value -> new ResponseEntity<>(value, HttpStatus.CREATED))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}

// URL: ec2-18-223-131-64.us-east-2.compute.amazonaws.com:8080/usersAPI/login
