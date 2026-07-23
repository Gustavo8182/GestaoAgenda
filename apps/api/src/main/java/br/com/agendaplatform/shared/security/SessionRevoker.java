package br.com.agendaplatform.shared.security;

/**
 * Revoga todas as sessões HTTP ativas de uma usuária (por e-mail), forçando-a a autenticar de
 * novo na próxima requisição. Usado sempre que a credencial ou o acesso de alguém muda de forma
 * que sessões já abertas deixam de ser confiáveis (redefinição de senha, desativação de membro).
 */
public interface SessionRevoker {

    void revokeSessionsFor(String email);
}
