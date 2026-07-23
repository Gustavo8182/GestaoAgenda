package br.com.agendaplatform.membership.api;

import br.com.agendaplatform.identity.EmailAlreadyRegisteredException;
import br.com.agendaplatform.membership.application.MemberSummary;
import br.com.agendaplatform.membership.application.MembershipService;
import br.com.agendaplatform.membership.domain.InvalidMembershipActionException;
import br.com.agendaplatform.membership.domain.MemberNotFoundException;
import br.com.agendaplatform.shared.web.ErrorResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/members")
class MembershipController {

    private final MembershipService membershipService;

    MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MemberSummary invite(@Valid @RequestBody InviteMemberRequest request) {
        return membershipService.invite(request.email(), request.displayName());
    }

    @GetMapping
    List<MemberSummary> list() {
        return membershipService.list();
    }

    @PostMapping("/{memberId}/disable")
    MemberSummary disable(@PathVariable UUID memberId) {
        return membershipService.disable(memberId);
    }

    @PostMapping("/{memberId}/reactivate")
    MemberSummary reactivate(@PathVariable UUID memberId) {
        return membershipService.reactivate(memberId);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse handleEmailAlreadyRegistered(EmailAlreadyRegisteredException exception) {
        return new ErrorResponse("email_already_registered", exception.getMessage());
    }

    @ExceptionHandler(MemberNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse handleMemberNotFound(MemberNotFoundException exception) {
        return new ErrorResponse("member_not_found", exception.getMessage());
    }

    @ExceptionHandler(InvalidMembershipActionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleInvalidAction(InvalidMembershipActionException exception) {
        return new ErrorResponse("invalid_membership_action", exception.getMessage());
    }
}
