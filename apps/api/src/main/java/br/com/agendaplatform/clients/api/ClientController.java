package br.com.agendaplatform.clients.api;

import br.com.agendaplatform.clients.application.ClientRegistry;
import br.com.agendaplatform.clients.application.ClientSummary;
import br.com.agendaplatform.clients.application.CreateClientResult;
import br.com.agendaplatform.clients.domain.ClientNotFoundException;
import br.com.agendaplatform.clients.domain.InvalidPhoneException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clients")
class ClientController {

    private final ClientRegistry clientRegistry;

    ClientController(ClientRegistry clientRegistry) {
        this.clientRegistry = clientRegistry;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreateClientResult create(@Valid @RequestBody CreateClientRequest request) {
        return clientRegistry.create(
                request.name(), request.phone(), request.alternatePhone(), request.origin(), request.notes());
    }

    @GetMapping
    List<ClientSummary> list(@RequestParam(required = false) String query) {
        return clientRegistry.list(query);
    }

    @PostMapping("/{clientId}/restrict-contact")
    ClientSummary restrictContact(@PathVariable UUID clientId, @RequestBody RestrictClientContactRequest request) {
        return clientRegistry.restrictContact(clientId, request.reason());
    }

    @PostMapping("/{clientId}/lift-contact-restriction")
    ClientSummary liftContactRestriction(@PathVariable UUID clientId) {
        return clientRegistry.liftContactRestriction(clientId);
    }

    @ExceptionHandler(InvalidPhoneException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleInvalidPhone(InvalidPhoneException exception) {
        return new ErrorResponse("invalid_phone", exception.getMessage());
    }

    @ExceptionHandler(ClientNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse handleClientNotFound(ClientNotFoundException exception) {
        return new ErrorResponse("client_not_found", exception.getMessage());
    }
}
