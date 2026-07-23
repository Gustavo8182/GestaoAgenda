package br.com.agendaplatform.organizations;

import java.util.UUID;

public record MemberRef(UUID id, UUID userId, String role, String status) {
}
