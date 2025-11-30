package com.orbvpn.api.service.social_login;

import com.orbvpn.api.domain.dto.OAuthToken;
import org.apache.poi.util.NotImplemented;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import static com.orbvpn.api.domain.OAuthConstants.*;

@Service
public class TokenServiceV2 {

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    public String getAmazonToken(String code) {
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId("amazon");

        String tokenUri = clientRegistration.getProviderDetails().getTokenUri();

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientRegistration.getClientId());
        params.add("client_secret", clientRegistration.getClientSecret());
        params.add("redirect_uri", amazonRedirectURL);
        params.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<OAuthToken> response = restTemplate.exchange(
                tokenUri,
                HttpMethod.POST,
                request,
                OAuthToken.class);
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return response.getBody().getAccess_token();
        } else {
            throw new RuntimeException(
                    "Failed to retrieve Amazon access token, status code: " + response.getStatusCode());
        }
    }

    public String getLinkedinToken(String code) {

        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId("linkedin");

        String tokenUri = clientRegistration.getProviderDetails().getTokenUri();

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientRegistration.getClientId());
        params.add("client_secret", clientRegistration.getClientSecret());
        params.add("redirect_uri", linkedinRedirectURL);
        params.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<OAuthToken> response = restTemplate.exchange(
                tokenUri,
                HttpMethod.POST,
                request,
                OAuthToken.class);
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return response.getBody().getId_token();
        } else {
            throw new RuntimeException(
                    "Failed to retrieve Linkedin access token, status code: " + response.getStatusCode());
        }
    }

    @NotImplemented
    public String getAppleToken(String code) {
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId("apple");

        String tokenUri = clientRegistration.getProviderDetails().getTokenUri();

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientRegistration.getClientId());
        params.add("client_secret", clientRegistration.getClientSecret());
        params.add("redirect_uri", appleRedirectURL);
        params.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<OAuthToken> response = restTemplate.exchange(
                tokenUri,
                HttpMethod.POST,
                request,
                OAuthToken.class);
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return response.getBody().getId_token();
        } else {
            throw new RuntimeException(
                    "Failed to retrieve Apple access token, status code: " + response.getStatusCode());
        }
    }

    public String getFacebookToken(String code) {

        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId("facebook");

        String tokenUri = clientRegistration.getProviderDetails().getTokenUri();

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientRegistration.getClientId());
        params.add("client_secret", clientRegistration.getClientSecret());
        params.add("redirect_uri", facebookRedirectURL);
        params.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<OAuthToken> response = restTemplate.exchange(
                tokenUri,
                HttpMethod.POST,
                request,
                OAuthToken.class);
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            // Facebook returns access_token, not id_token
            return response.getBody().getAccess_token();
        } else {
            throw new RuntimeException(
                    "Failed to retrieve Facebook access token, status code: " + response.getStatusCode());
        }
    }

    public String getGoogleToken(String code) {

        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId("google");

        String tokenUri = clientRegistration.getProviderDetails().getTokenUri();

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientRegistration.getClientId());
        params.add("client_secret", clientRegistration.getClientSecret());
        params.add("redirect_uri", googleRedirectURL);
        params.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<OAuthToken> response = restTemplate.exchange(
                tokenUri,
                HttpMethod.POST,
                request,
                OAuthToken.class);
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return response.getBody().getId_token();
        } else {
            throw new RuntimeException(
                    "Failed to retrieve Google access token, status code: " + response.getStatusCode());
        }
    }

    public String getGithubToken(String code) {
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId("github");

        String tokenUri = clientRegistration.getProviderDetails().getTokenUri();

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientRegistration.getClientId());
        params.add("client_secret", clientRegistration.getClientSecret());
        params.add("redirect_uri", githubRedirectURL);
        params.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<OAuthToken> response = restTemplate.exchange(
                tokenUri,
                HttpMethod.POST,
                request,
                OAuthToken.class);
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return response.getBody().getAccess_token();
        } else {
            throw new RuntimeException(
                    "Failed to retrieve Github access token, status code: " + response.getStatusCode());
        }
    }
}
