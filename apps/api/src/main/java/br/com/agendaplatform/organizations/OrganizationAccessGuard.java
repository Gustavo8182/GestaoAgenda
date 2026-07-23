package br.com.agendaplatform.organizations;

/**
 * Permite que outros módulos restrinjam uma ação à proprietária da organização atual (menor
 * privilégio: secretária opera o dia a dia, mas não decisões estruturais do negócio). Lança
 * {@link org.springframework.security.access.AccessDeniedException} quando o papel atual não é
 * suficiente — já mapeada para 403 pelo filtro de segurança global, sem precisar de tratamento
 * de exceção próprio em cada controller.
 */
public interface OrganizationAccessGuard {

    void requireOwner();
}
