package com.sondurukedella.security.user;

import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.security.user.dto.PasswordResetRequest;
import com.sondurukedella.security.user.dto.UserCreateRequest;
import com.sondurukedella.security.user.dto.UserResponse;
import com.sondurukedella.security.user.dto.UserRoleRequest;
import com.sondurukedella.security.user.dto.UserStatusRequest;
import com.sondurukedella.security.user.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Super Admin only (SecurityConfig + checked again in the service). No delete, users get disabled.
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserAdministrationService service;

    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean enabled,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(q, role, enabled, pageable);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@AuthenticationPrincipal AppUserPrincipal actor,
            @Valid @RequestBody UserCreateRequest request) {
        UserResponse created = service.create(actorId(actor), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public UserResponse update(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable long id,
            @Valid @RequestBody UserUpdateRequest request) {
        return service.update(actorId(actor), id, request);
    }

    @PutMapping("/{id}/role")
    public UserResponse changeRole(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable long id,
            @Valid @RequestBody UserRoleRequest request) {
        return service.changeRole(actorId(actor), id, request);
    }

    @PutMapping("/{id}/status")
    public UserResponse setStatus(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable long id,
            @Valid @RequestBody UserStatusRequest request) {
        return service.setStatus(actorId(actor), id, request);
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable long id,
            @Valid @RequestBody PasswordResetRequest request) {
        service.resetPassword(actorId(actor), id, request);
        return ResponseEntity.noContent().build();
    }

    // mock users in tests have no AppUserPrincipal
    private static long actorId(AppUserPrincipal actor) {
        if (actor == null) {
            throw new AccessDeniedException("Sign in with a Super Admin account.");
        }
        return actor.id();
    }
}
