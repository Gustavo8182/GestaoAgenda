package br.com.agendaplatform.clients;

public record ClientExportRow(
        String name, String phone, String alternatePhone, String origin, String notes) {
}
