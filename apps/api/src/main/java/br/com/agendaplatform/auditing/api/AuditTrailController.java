package br.com.agendaplatform.auditing.api;

import br.com.agendaplatform.auditing.application.AuditEntry;
import br.com.agendaplatform.auditing.application.AuditTrailService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-log")
class AuditTrailController {

    private final AuditTrailService auditTrailService;

    AuditTrailController(AuditTrailService auditTrailService) {
        this.auditTrailService = auditTrailService;
    }

    @GetMapping
    List<AuditEntry> recent() {
        return auditTrailService.recent();
    }
}
