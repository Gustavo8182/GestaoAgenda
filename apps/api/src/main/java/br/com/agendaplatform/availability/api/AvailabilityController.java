package br.com.agendaplatform.availability.api;

import br.com.agendaplatform.availability.application.BlockManager;
import br.com.agendaplatform.availability.application.BlockSummary;
import br.com.agendaplatform.availability.application.BusinessHoursEntry;
import br.com.agendaplatform.availability.application.BusinessHoursManager;
import br.com.agendaplatform.availability.domain.BlockNotFoundException;
import br.com.agendaplatform.availability.domain.InvalidBlockException;
import br.com.agendaplatform.availability.domain.InvalidBusinessHoursException;
import br.com.agendaplatform.shared.web.ErrorResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/availability")
class AvailabilityController {

    private final BusinessHoursManager businessHoursManager;
    private final BlockManager blockManager;

    AvailabilityController(BusinessHoursManager businessHoursManager, BlockManager blockManager) {
        this.businessHoursManager = businessHoursManager;
        this.blockManager = blockManager;
    }

    @GetMapping("/business-hours")
    List<BusinessHoursEntry> listBusinessHours() {
        return businessHoursManager.list();
    }

    @PutMapping("/business-hours")
    List<BusinessHoursEntry> replaceBusinessHours(@RequestBody List<@Valid BusinessHoursEntryRequest> entries) {
        return businessHoursManager.replace(entries.stream()
                .map(entry -> new BusinessHoursEntry(entry.dayOfWeek(), entry.startTime(), entry.endTime()))
                .toList());
    }

    @GetMapping("/blocks")
    List<BlockSummary> listBlocks() {
        return blockManager.list();
    }

    @PostMapping("/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    BlockSummary createBlock(@Valid @RequestBody CreateBlockRequest request) {
        return blockManager.create(request.startAt(), request.endAt(), request.reason());
    }

    @DeleteMapping("/blocks/{blockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeBlock(@PathVariable UUID blockId) {
        blockManager.remove(blockId);
    }

    @ExceptionHandler({InvalidBusinessHoursException.class, InvalidBlockException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleInvalid(RuntimeException exception) {
        return new ErrorResponse("invalid_availability", exception.getMessage());
    }

    @ExceptionHandler(BlockNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse handleNotFound(BlockNotFoundException exception) {
        return new ErrorResponse("block_not_found", exception.getMessage());
    }
}
