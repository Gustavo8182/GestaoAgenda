package br.com.agendaplatform.identity;

/**
 * Permite que outros módulos criem uma nova usuária convidada (ex.: ao convidar uma secretária
 * para a organização), sem acessar as classes internas do módulo identity. A usuária criada
 * nasce com status {@code INVITED} — só pode autenticar depois de aceitar o convite e definir
 * uma senha.
 */
public interface UserProvisioning {

    /**
     * Cria a usuária convidada e já envia o e-mail de convite (com o nome da organização, para
     * contexto de quem recebe).
     *
     * @throws EmailAlreadyRegisteredException se já existir uma usuária (de qualquer status) com
     *     este e-mail.
     */
    UserRef inviteUser(String email, String displayName, String organizationName);
}
