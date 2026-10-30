package com.sondurukedella.security.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Takes a username or an email. Returns disabled users too - the provider checks the password
// first, so disabled and wrong-password take the same time and give the same error.
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository repository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail) {
        String login = Usernames.normalize(usernameOrEmail);
        return (Emails.looksLikeEmail(login) ? repository.findByEmail(login) : repository.findByUsername(login))
                .map(AppUserPrincipal::withPassword)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
