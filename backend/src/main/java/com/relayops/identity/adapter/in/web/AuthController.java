package com.relayops.identity.adapter.in.web;

import com.relayops.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON login for the console.
 *
 * <p>The SPA cannot use the default form post (it needs a CSRF token and a redirect), so this
 * endpoint authenticates and stores the {@link SecurityContext} in the HTTP session explicitly.
 * The session itself is Redis-backed, so restarts do not log everyone out.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public ApiResponse<CurrentUser> login(@RequestBody LoginRequest request,
                                          HttpServletRequest servletRequest,
                                          HttpServletResponse servletResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, servletRequest, servletResponse);
        return ApiResponse.of(CurrentUser.of(authentication));
    }

    /** Returns the current account; unauthenticated calls get 401 from the entry point. */
    @GetMapping("/me")
    public ApiResponse<CurrentUser> me(Authentication authentication) {
        return ApiResponse.of(CurrentUser.of(authentication));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ApiResponse.of(null);
    }

    /** Login payload. */
    public record LoginRequest(String username, String password) {
    }

    /** Current account view. */
    public record CurrentUser(String username, String role) {

        static CurrentUser of(Authentication authentication) {
            if (authentication == null || !authentication.isAuthenticated()) {
                return new CurrentUser(null, null);
            }
            String role = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(authority -> authority.startsWith("ROLE_"))
                    .map(authority -> authority.substring("ROLE_".length()))
                    .findFirst()
                    .orElse("VIEWER");
            return new CurrentUser(authentication.getName(), role);
        }
    }
}
