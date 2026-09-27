package com.cryptoportfoliohub.security;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class GoogleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final GoogleLoginUserProvisioner userProvisioner;

    public GoogleOidcUserService(
            OAuth2UserService<OidcUserRequest, OidcUser> delegate,
            GoogleLoginUserProvisioner userProvisioner) {
        this.delegate = delegate;
        this.userProvisioner = userProvisioner;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        if (!"google".equals(userRequest.getClientRegistration().getRegistrationId())) {
            throw new OAuth2AuthenticationException(new OAuth2Error("unsupported_provider"));
        }
        OidcUser oidcUser = delegate.loadUser(userRequest);
        userProvisioner.provision(oidcUser);
        return oidcUser;
    }
}
