package br.com.agendaplatform.relationships.api;

import br.com.agendaplatform.relationships.application.RelationshipService;
import br.com.agendaplatform.relationships.application.RelationshipSummary;
import br.com.agendaplatform.relationships.domain.InvalidRelationshipContactException;
import br.com.agendaplatform.relationships.domain.RelationshipContactNotConvertibleException;
import br.com.agendaplatform.relationships.domain.RelationshipContactNotFoundException;
import br.com.agendaplatform.scheduling.AppointmentSummary;
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
@RequestMapping("/api/v1/relationships")
class RelationshipController {

    private final RelationshipService relationshipService;

    RelationshipController(RelationshipService relationshipService) {
        this.relationshipService = relationshipService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RelationshipSummary create(@Valid @RequestBody CreateRelationshipContactRequest request) {
        return relationshipService.create(request.name(), request.phone(), request.origin());
    }

    @GetMapping
    List<RelationshipSummary> list() {
        return relationshipService.list();
    }

    @PostMapping("/{contactId}/update")
    RelationshipSummary update(
            @PathVariable UUID contactId, @RequestBody UpdateRelationshipContactRequest request) {
        return relationshipService.update(contactId, request.status(), request.nextAction(), request.nextActionAt());
    }

    @PostMapping("/{contactId}/convert")
    @ResponseStatus(HttpStatus.CREATED)
    AppointmentSummary convert(
            @PathVariable UUID contactId, @Valid @RequestBody ConvertRelationshipContactRequest request) {
        return relationshipService.convert(contactId, request.serviceId(), request.startAt(), request.endAt());
    }

    @ExceptionHandler({InvalidRelationshipContactException.class, RelationshipContactNotConvertibleException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleInvalid(RuntimeException exception) {
        return new ErrorResponse("invalid_relationship_contact", exception.getMessage());
    }

    @ExceptionHandler(RelationshipContactNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse handleNotFound(RelationshipContactNotFoundException exception) {
        return new ErrorResponse("relationship_contact_not_found", exception.getMessage());
    }

    /**
     * Cobre falhas do cadastro de cliente ou do agendamento criado ao converter (telefone
     * inválido, conflito, bloqueio etc.) — exceções dos módulos clients/scheduling, que este
     * controller não pode importar diretamente (são internas àqueles módulos). A mensagem
     * original já é adequada para exibição.
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse handleConversionFailure(RuntimeException exception) {
        return new ErrorResponse("relationship_conversion_failed", exception.getMessage());
    }
}
