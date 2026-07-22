package br.com.agendaplatform.organizations;

/**
 * Resolve a organização da usuária autenticada na requisição atual. Nunca deriva a organização
 * de parâmetros enviados pelo navegador — sempre da sessão autenticada.
 */
public interface CurrentOrganizationProvider {

    CurrentOrganization current();
}
