package br.com.agendaplatform.membership.application;

import java.util.UUID;

public record MemberSummary(UUID id, String email, String displayName, String role, String status) {
}
