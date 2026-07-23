package br.com.agendaplatform.waitlist.api;

import br.com.agendaplatform.scheduling.AppointmentSummary;
import br.com.agendaplatform.shared.web.ErrorResponse;
import br.com.agendaplatform.waitlist.application.WaitlistService;
import br.com.agendaplatform.waitlist.application.WaitlistSummary;
import br.com.agendaplatform.waitlist.domain.InvalidWaitlistEntryException;
import br.com.agendaplatform.waitlist.domain.UnknownReferenceException;
import br.com.agendaplatform.waitlist.domain.WaitlistEntryExpiredException;
import br.com.agendaplatform.waitlist.domain.WaitlistEntryNotFoundException;
import br.com.agendaplatform.waitlist.domain.WaitlistEntryNotWaitingException;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
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
@RequestMapping("/api/v1/waitlist")
class WaitlistController {

    private final WaitlistService waitlistService;

    WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WaitlistSummary create(@Valid @RequestBody CreateWaitlistEntryRequest request) {
        return waitlistService.create(
                request.clientId(),
                request.serviceId(),
                request.preferredStartDate(),
                request.preferredEndDate(),
                request.preferredStartTime(),
                request.preferredEndTime(),
                request.priority(),
                request.expiresAt());
    }

    @GetMapping
    List<WaitlistSummary> list() {
        return waitlistService.list();
    }

    @GetMapping("/compatible")
    List<WaitlistSummary> compatible(
            @RequestParam UUID serviceId, @RequestParam Instant startAt, @RequestParam Instant endAt) {
        return waitlistService.findCompatible(serviceId, startAt, endAt);
    }

    @PostMapping("/{entryId}/cancel")
    WaitlistSummary cancel(@PathVariable UUID entryId) {
        return waitlistService.cancel(entryId);
    }

    @PostMapping("/{entryId}/convert")
    @ResponseStatus(HttpStatus.CREATED)
    AppointmentSummary convert(@PathVariable UUID entryId, @Valid @RequestBody ConvertWaitlistEntryRequest request) {
        return waitlistService.convert(entryId, request.startAt(), request.endAt());
    }

    @ExceptionHandler({
        InvalidWaitlistEntryException.class,
        UnknownReferenceException.class,
        WaitlistEntryNotWaitingException.class,
        WaitlistEntryExpiredException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleInvalid(RuntimeException exception) {
        return new ErrorResponse("invalid_waitlist_entry", exception.getMessage());
    }

    @ExceptionHandler(WaitlistEntryNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse handleNotFound(WaitlistEntryNotFoundException exception) {
        return new ErrorResponse("waitlist_entry_not_found", exception.getMessage());
    }

    /**
     * Deixa a negação de acesso (SUPPORT sem permissão operacional, ações restritas à OWNER)
     * propagar para o filtro de segurança padrão, em vez de ser capturada pelo handler genérico
     * de {@link RuntimeException} abaixo — senão viraria 409 em vez de 403.
     */
    @ExceptionHandler(AccessDeniedException.class)
    void rethrowAccessDenied(AccessDeniedException exception) {
        throw exception;
    }

    /**
     * Cobre falhas do agendamento criado ao converter (conflito, bloqueio, fora do horário de
     * funcionamento etc.) — exceções do módulo scheduling, que este controller não pode importar
     * diretamente (são internas àquele módulo). A mensagem original já é adequada para exibição.
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse handleBookingFailure(RuntimeException exception) {
        return new ErrorResponse("waitlist_conversion_failed", exception.getMessage());
    }
}
