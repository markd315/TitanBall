package authserver.models.DTO;

import javax.validation.constraints.NotBlank;

public class RenewRequest {
    @NotBlank
    private String usernameOrEmail;

    @NotBlank
    private String m2mAuth;

    @NotBlank
    private int days;

    public String getUsernameOrEmail() {
        return usernameOrEmail;
    }

    public void setUsernameOrEmail(String usernameOrEmail) {
        this.usernameOrEmail = usernameOrEmail;
    }

    public String getM2mAuth() {
        return m2mAuth;
    }

    public void setM2mAuth(String m2mAuth) {
        this.m2mAuth = m2mAuth;
    }

    public int getDays() {
        return this.days;
    }
}
