package com.atakmap.android.scythe.model;

/** Response returned by /auth/register and /auth/login endpoints. */
public class AuthResponse {

    private String token;
    private String message;

    public AuthResponse() {}

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
