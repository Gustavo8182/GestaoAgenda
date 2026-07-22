package br.com.agendaplatform.identity;

import java.util.UUID;

public record UserRef(UUID id, String displayName) {
}
