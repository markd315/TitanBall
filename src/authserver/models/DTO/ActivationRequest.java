package authserver.models.DTO;

import javax.validation.constraints.NotBlank;

public class ActivationRequest {
    @NotBlank
    private String usernameOrEmail;

    @NotBlank
    private String key;

    public String getUsernameOrEmail() {
        return usernameOrEmail;
    }

    public void setUsernameOrEmail(String usernameOrEmail) {
        this.usernameOrEmail = usernameOrEmail;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
