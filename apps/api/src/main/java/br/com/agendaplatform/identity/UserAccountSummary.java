package br.com.agendaplatform.identity;

import java.util.UUID;

public record UserAccountSummary(UUID id, String email, String displayName, String status) {
}
