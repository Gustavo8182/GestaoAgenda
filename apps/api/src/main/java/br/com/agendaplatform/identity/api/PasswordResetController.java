package br.com.agendaplatform.identity.api;

import br.com.agendaplatform.identity.application.PasswordResetService;
import br.com.agendaplatform.identity.domain.InvalidPasswordResetTokenException;
import br.com.agendaplatform.shared.web.ErrorResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
class PasswordResetController {

    private static final String GENERIC_MESSAGE =
            "Se este e-mail estiver cadastrado, você vai receber instruções para redefinir a senha.";

    private final PasswordResetService passwordResetService;

    PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/request")
    PasswordResetMessageResponse request(@Valid @RequestBody RequestPasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return new PasswordResetMessageResponse(GENERIC_MESSAGE);
    }

    @PostMapping("/confirm")
    PasswordResetMessageResponse confirm(@Valid @RequestBody ConfirmPasswordResetRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return new PasswordResetMessageResponse("Senha redefinida com sucesso.");
    }

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleInvalidToken(InvalidPasswordResetTokenException exception) {
        return new ErrorResponse("invalid_password_reset_token", exception.getMessage());
    }
}
