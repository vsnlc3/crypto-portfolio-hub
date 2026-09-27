package com.cryptoportfoliohub.security;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.persistence.repository.UserRepository;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireUser(OidcUser principal) {
        return userRepository.findByGoogleSubject(principal.getSubject())
                .orElseThrow(() -> new IllegalStateException("Authenticated Google user is not provisioned"));
    }
}
