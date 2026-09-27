package com.cryptoportfoliohub;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import com.cryptoportfoliohub.security.GoogleLoginUserProvisioner;
import com.cryptoportfoliohub.security.GoogleOidcUserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleOidcUserServiceTests {

    @Test
    void successfulGoogleOidcUserIsProvisionedAndReturned() {
        OidcUserRequest request = mock(OidcUserRequest.class);
        ClientRegistration registration = mock(ClientRegistration.class);
        OidcUser googleUser = mock(OidcUser.class);
        GoogleLoginUserProvisioner provisioner = mock(GoogleLoginUserProvisioner.class);
        when(request.getClientRegistration()).thenReturn(registration);
        when(registration.getRegistrationId()).thenReturn("google");

        GoogleOidcUserService service = new GoogleOidcUserService(ignored -> googleUser, provisioner);

        assertThat(service.loadUser(request)).isSameAs(googleUser);
        verify(provisioner).provision(googleUser);
    }

    @Test
    void failedGoogleUserInfoDoesNotProvisionAUser() {
        OidcUserRequest request = mock(OidcUserRequest.class);
        ClientRegistration registration = mock(ClientRegistration.class);
        GoogleLoginUserProvisioner provisioner = mock(GoogleLoginUserProvisioner.class);
        OAuth2AuthenticationException failure = new OAuth2AuthenticationException("provider_error");
        when(request.getClientRegistration()).thenReturn(registration);
        when(registration.getRegistrationId()).thenReturn("google");

        GoogleOidcUserService service = new GoogleOidcUserService(ignored -> { throw failure; }, provisioner);

        assertThatThrownBy(() -> service.loadUser(request)).isSameAs(failure);
        verify(provisioner, never()).provision(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsOtherOidcRegistrationsBeforeLoadingTheirUserInfo() {
        OidcUserRequest request = mock(OidcUserRequest.class);
        ClientRegistration registration = mock(ClientRegistration.class);
        GoogleLoginUserProvisioner provisioner = mock(GoogleLoginUserProvisioner.class);
        when(request.getClientRegistration()).thenReturn(registration);
        when(registration.getRegistrationId()).thenReturn("other-provider");

        GoogleOidcUserService service = new GoogleOidcUserService(
                ignored -> { throw new AssertionError("delegate must not be called"); }, provisioner);

        assertThatThrownBy(() -> service.loadUser(request)).isInstanceOf(OAuth2AuthenticationException.class);
        verify(provisioner, never()).provision(org.mockito.ArgumentMatchers.any());
    }
}
