package authserver;

import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Component
@Path("/users")
public class UserController {
    @GET
    @Path("/world")
    public String test() {
        return "Hello world!";
    }
}