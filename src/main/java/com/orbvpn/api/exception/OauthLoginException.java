package com.orbvpn.api.exception;

public class OauthLoginException extends RuntimeException {

  public OauthLoginException() {
    super("OAuth login failed");
  }

  public OauthLoginException(String message) {
    super("OAuth login failed: " + message);
  }

  public OauthLoginException(String message, Throwable cause) {
    super("OAuth login failed: " + message, cause);
  }
}