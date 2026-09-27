package com.cryptoportfoliohub;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.persistence.repository.UserRepository;
import com.cryptoportfoliohub.security.GoogleLoginUserProvisioner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

@SpringBootTest(properties = {
        "app.auth.google.enabled=true",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class GoogleAuthenticationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GoogleLoginUserProvisioner userProvisioner;

    @Test
    void configuredGoogleLoginStartsAnAuthorizationCodeRedirect() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Location"))
                        .contains("accounts.google.com", "response_type=code", "scope="));
    }

    @Test
    void unauthenticatedUserCannotReadCurrentUser() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void currentUserIsResolvedByGoogleSubjectAndIgnoresClientSuppliedUserId() throws Exception {
        User owner = saveUser("owner-sub-" + UUID.randomUUID(), "owner@example.test");
        User other = saveUser("other-sub-" + UUID.randomUUID(), "other@example.test");

        mockMvc.perform(get("/api/v1/auth/me")
                        .param("userId", other.getId().toString())
                        .with(oidcLogin().idToken(token -> token.subject(owner.getGoogleSubject())
                                .claim("email", owner.getEmail()).claim("email_verified", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.getId().toString()))
                .andExpect(jsonPath("$.email").value(owner.getEmail()))
                .andExpect(jsonPath("$.googleSubject").doesNotExist());
    }

    @Test
    void csrfTokenEndpointProvidesHeaderNameAndToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void logoutRequiresCsrfTokenAndInvalidatesAuthenticatedSession() throws Exception {
        User user = saveUser("logout-sub-" + UUID.randomUUID(), "logout@example.test");
        var loginResult = mockMvc.perform(get("/api/v1/auth/me")
                        .with(oidcLogin().idToken(token -> token.subject(user.getGoogleSubject())
                                .claim("email", user.getEmail()))))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sameGoogleSubjectKeepsTheSameUserWhenEmailChangesAndSharedEmailDoesNotMergeUsers() {
        String subject = "stable-sub-" + UUID.randomUUID();
        var first = mock(org.springframework.security.oauth2.core.oidc.user.OidcUser.class);
        when(first.getSubject()).thenReturn(subject);
        when(first.getEmail()).thenReturn("old-email@example.test");
        when(first.getFullName()).thenReturn("Portfolio User");
        when(first.getPicture()).thenReturn("https://example.test/avatar.png");
        User created = userProvisioner.provision(first);

        var updated = mock(org.springframework.security.oauth2.core.oidc.user.OidcUser.class);
        when(updated.getSubject()).thenReturn(subject);
        when(updated.getEmail()).thenReturn("new-email@example.test");
        when(updated.getFullName()).thenReturn("Portfolio User Updated");
        when(updated.getPicture()).thenReturn("https://example.test/avatar-updated.png");
        User sameIdentity = userProvisioner.provision(updated);

        var another = mock(org.springframework.security.oauth2.core.oidc.user.OidcUser.class);
        when(another.getSubject()).thenReturn("different-sub-" + UUID.randomUUID());
        when(another.getEmail()).thenReturn("new-email@example.test");
        User distinctIdentity = userProvisioner.provision(another);

        assertThat(sameIdentity.getId()).isEqualTo(created.getId());
        assertThat(sameIdentity.getEmail()).isEqualTo("new-email@example.test");
        assertThat(sameIdentity.getDisplayName()).isEqualTo("Portfolio User Updated");
        assertThat(sameIdentity.getLastLoginAt()).isNotNull();
        assertThat(distinctIdentity.getId()).isNotEqualTo(created.getId());
        assertThat(userRepository.findByGoogleSubject(subject)).hasValueSatisfying(
                persisted -> assertThat(persisted.getId()).isEqualTo(created.getId()));
        assertThat(userRepository.findByGoogleSubject(distinctIdentity.getGoogleSubject())).hasValueSatisfying(
                persisted -> assertThat(persisted.getId()).isEqualTo(distinctIdentity.getId()));
    }

    @Test
    void missingRequiredGoogleIdentityClaimsDoNotCreateAUser() {
        String subject = "missing-email-sub-" + UUID.randomUUID();
        var googleUser = mock(org.springframework.security.oauth2.core.oidc.user.OidcUser.class);
        when(googleUser.getSubject()).thenReturn(subject);

        assertThatThrownBy(() -> userProvisioner.provision(googleUser))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(userRepository.findByGoogleSubject(subject)).isEmpty();
    }

    private User saveUser(String subject, String email) {
        return userRepository.saveAndFlush(new User(subject, email, "Test User", null));
    }
}
