package br.com.agendaplatform.clients;

/**
 * Permite que outros módulos registrem uma nova cliente (ex.: ao converter um contato de
 * relacionamento) reaproveitando a mesma validação e normalização de telefone já existentes,
 * sem acessar as classes internas do módulo clients.
 */
public interface ClientRegistration {

    ClientRef register(String name, String phone, String alternatePhone, String origin, String notes);
}
