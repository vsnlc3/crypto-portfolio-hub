package com.cryptoportfoliohub.security;

import java.time.Instant;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.persistence.repository.UserRepository;

@Service
public class GoogleLoginUserProvisioner {

    private final UserRepository userRepository;

    public GoogleLoginUserProvisioner(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User provision(OidcUser googleUser) {
        String subject = googleUser.getSubject();
        String email = googleUser.getEmail();
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_user_info"), "Google did not return required user identity claims");
        }

        Instant loggedInAt = Instant.now();
        User user = userRepository.findByGoogleSubject(subject)
                .orElseGet(() -> new User(subject, email, googleUser.getFullName(), googleUser.getPicture()));
        user.recordGoogleLogin(email, googleUser.getFullName(), googleUser.getPicture(), loggedInAt);
        return userRepository.save(user);
    }
}
