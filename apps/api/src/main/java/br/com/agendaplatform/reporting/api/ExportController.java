package br.com.agendaplatform.reporting.api;

import br.com.agendaplatform.reporting.application.ExportService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/export")
class ExportController {

    private static final MediaType CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ExportService exportService;

    ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/clients")
    ResponseEntity<String> exportClients() {
        return csvResponse("clientes.csv", exportService.exportClients());
    }

    @GetMapping("/appointments")
    ResponseEntity<String> exportAppointments() {
        return csvResponse("agendamentos.csv", exportService.exportAppointments());
    }

    @GetMapping("/waitlist")
    ResponseEntity<String> exportWaitlist() {
        return csvResponse("lista-de-espera.csv", exportService.exportWaitlist());
    }

    @GetMapping("/relationships")
    ResponseEntity<String> exportRelationships() {
        return csvResponse("relacionamento.csv", exportService.exportRelationships());
    }

    private ResponseEntity<String> csvResponse(String filename, String csv) {
        return ResponseEntity.ok()
                .contentType(CSV_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv);
    }
}
