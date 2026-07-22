package br.com.agendaplatform.clients.domain;

/**
 * Normaliza telefones para comparação de duplicidade. Remove tudo que não é dígito e,
 * quando sobrarem 12 ou 13 dígitos começando com "55", remove o código do país — um
 * número local de 10 ou 11 dígitos (DDD + número) nunca cai nesse tamanho, então não há
 * risco de confundir um DDD "55" legítimo com o código do país.
 */
public final class PhoneNormalizer {

    private PhoneNormalizer() {
    }

    public static String normalize(String rawPhone) {
        String digits = rawPhone == null ? "" : rawPhone.replaceAll("\\D", "");
        if ((digits.length() == 12 || digits.length() == 13) && digits.startsWith("55")) {
            return digits.substring(2);
        }
        return digits;
    }
}
