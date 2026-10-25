package com.sondurukedella.security.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Used only by the login check. Disabled users are returned too: the provider checks the password
// first and the status after, so both cases cost the same and get the same generic answer.
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository repository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return repository.findByUsername(Usernames.normalize(username))
                .map(AppUserPrincipal::withPassword)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
