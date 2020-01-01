package authserver;

import authserver.jwt.JwtTokenProvider;
import authserver.matchmaking.Matchmaker;
import authserver.models.DTO.ActivationRequest;
import authserver.models.DTO.LoginRequest;
import authserver.models.DTO.RenewRequest;
import authserver.models.DTO.UserDTO;
import authserver.models.User;
import authserver.models.responses.JwtAuthenticationResponse;
import authserver.models.responses.UserResponse;
import authserver.users.UserService;
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

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
public class LoginController {
    private static final String M2M_AUTH = "VERY SECRET PAYPAL PRIVATE KEY";

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    UserService userService;

    @Autowired
    Matchmaker userPool;

    @Autowired
    JwtTokenProvider tokenProvider;

    @Autowired
    AuthenticationManager authenticationManager;

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
        return new ResponseEntity<>(userPool.findGame(auth), HttpStatus.OK);
    }

    @RequestMapping("/join")
    public ResponseEntity<String> joinLobby(Authentication auth, @RequestParam String tournamentCode) throws IOException {
        userPool.registerIntent(auth, tournamentCode);
        return new ResponseEntity<>(userPool.findGame(auth), HttpStatus.OK);
    }

    @RequestMapping("/leave")
    public ResponseEntity<String> leaveLobby(Authentication auth) {
        userPool.removeIntent(auth);
        String game = userPool.findGame(auth);
        if(!game.equals("NOT QUEUED")){
            return new ResponseEntity<>(game, HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>("Successfully withdrawn", HttpStatus.OK);
    }

    @RequestMapping(value = "/stat", method = RequestMethod.POST)
    public ResponseEntity<UserResponse> userStats(@RequestBody @Valid UserDTO userinput) {
        User user = userService.findUserByUsername(userinput.getUsername());
        return new ResponseEntity<>(new UserResponse(user), HttpStatus.OK);
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
            return new ResponseEntity<>(new UserResponse(user), headers, HttpStatus.BAD_REQUEST);
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
        return new ResponseEntity<>(new UserResponse(user), headers, HttpStatus.CREATED);
    }



}

// URL: ec2-18-223-131-64.us-east-2.compute.amazonaws.com:8080/usersAPI/login
