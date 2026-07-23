package br.com.agendaplatform.identity.api;

import br.com.agendaplatform.identity.application.InvitationService;
import br.com.agendaplatform.identity.domain.InvalidUserInvitationException;
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
@RequestMapping("/api/v1/auth/invitations")
class InvitationController {

    private final InvitationService invitationService;

    InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/accept")
    InvitationMessageResponse accept(@Valid @RequestBody AcceptInvitationRequest request) {
        invitationService.acceptInvitation(request.token(), request.newPassword());
        return new InvitationMessageResponse("Convite aceito. Você já pode entrar com a nova senha.");
    }

    @ExceptionHandler(InvalidUserInvitationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleInvalidToken(InvalidUserInvitationException exception) {
        return new ErrorResponse("invalid_invitation_token", exception.getMessage());
    }
}
