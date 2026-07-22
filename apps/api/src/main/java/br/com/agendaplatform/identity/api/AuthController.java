package br.com.agendaplatform.identity.api;

import br.com.agendaplatform.identity.application.CurrentUser;
import br.com.agendaplatform.identity.application.SessionAuthenticationService;
import br.com.agendaplatform.shared.web.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final SessionAuthenticationService sessionAuthenticationService;

    AuthController(SessionAuthenticationService sessionAuthenticationService) {
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    @PostMapping("/login")
    CurrentUser login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return sessionAuthenticationService.login(request.email(), request.password(), httpRequest, httpResponse);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        sessionAuthenticationService.logout(request, response, authentication);
    }

    @GetMapping("/me")
    CurrentUser me(Authentication authentication) {
        return sessionAuthenticationService.currentUser(authentication);
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ErrorResponse handleAuthenticationException() {
        return new ErrorResponse("invalid_credentials", "E-mail ou senha inválidos.");
    }
}
