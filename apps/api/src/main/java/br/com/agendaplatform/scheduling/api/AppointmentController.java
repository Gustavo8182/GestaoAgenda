package br.com.agendaplatform.scheduling.api;

import br.com.agendaplatform.scheduling.AppointmentSummary;
import br.com.agendaplatform.scheduling.application.AppointmentScheduler;
import br.com.agendaplatform.scheduling.domain.AppointmentConflictException;
import br.com.agendaplatform.scheduling.domain.AppointmentNotFoundException;
import br.com.agendaplatform.scheduling.domain.BlockedTimeException;
import br.com.agendaplatform.scheduling.domain.InvalidAppointmentRangeException;
import br.com.agendaplatform.scheduling.domain.InvalidAppointmentStateException;
import br.com.agendaplatform.scheduling.domain.OutsideBusinessHoursException;
import br.com.agendaplatform.scheduling.domain.UnknownReferenceException;
import br.com.agendaplatform.shared.web.ErrorResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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
@RequestMapping("/api/v1/appointments")
class AppointmentController {

    private final AppointmentScheduler appointmentScheduler;

    AppointmentController(AppointmentScheduler appointmentScheduler) {
        this.appointmentScheduler = appointmentScheduler;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AppointmentSummary create(@Valid @RequestBody CreateAppointmentRequest request) {
        return appointmentScheduler.create(request.clientId(), request.serviceId(), request.startAt(), request.endAt());
    }

    @GetMapping
    List<AppointmentSummary> list() {
        return appointmentScheduler.list();
    }

    @PostMapping("/{appointmentId}/reschedule")
    AppointmentSummary reschedule(
            @PathVariable UUID appointmentId, @Valid @RequestBody RescheduleAppointmentRequest request) {
        return appointmentScheduler.reschedule(appointmentId, request.startAt(), request.endAt());
    }

    @PostMapping("/{appointmentId}/cancel")
    AppointmentSummary cancel(
            @PathVariable UUID appointmentId, @Valid @RequestBody CancelAppointmentRequest request) {
        return appointmentScheduler.cancel(appointmentId, request.reason());
    }

    @ExceptionHandler({
        InvalidAppointmentRangeException.class,
        InvalidAppointmentStateException.class,
        OutsideBusinessHoursException.class,
        UnknownReferenceException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleInvalidRequest(RuntimeException exception) {
        return new ErrorResponse("invalid_appointment", exception.getMessage());
    }

    @ExceptionHandler(AppointmentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse handleNotFound(AppointmentNotFoundException exception) {
        return new ErrorResponse("appointment_not_found", exception.getMessage());
    }

    @ExceptionHandler(BlockedTimeException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse handleBlocked(BlockedTimeException exception) {
        return new ErrorResponse("blocked_time", exception.getMessage());
    }

    @ExceptionHandler({AppointmentConflictException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse handleConflict() {
        return new ErrorResponse("appointment_conflict", "Já existe um agendamento nesse horário.");
    }
}
