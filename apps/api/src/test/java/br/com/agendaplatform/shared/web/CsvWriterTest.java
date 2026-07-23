package br.com.agendaplatform.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

    @Test
    void writesHeaderAndRowsSeparatedByCrlf() {
        String csv = CsvWriter.toCsv(List.of("Nome", "Telefone"), List.of(List.of("Fulana", "21999999999")));

        assertThat(csv).isEqualTo("Nome,Telefone\r\nFulana,21999999999\r\n");
    }

    @Test
    void quotesFieldsContainingCommas() {
        String csv = CsvWriter.toCsv(List.of("Observações"), List.of(List.of("Prefere manhãs, com antecedência")));

        assertThat(csv).isEqualTo("Observações\r\n\"Prefere manhãs, com antecedência\"\r\n");
    }

    @Test
    void escapesDoubleQuotesByDoublingThem() {
        String csv = CsvWriter.toCsv(List.of("Nome"), List.of(List.of("Fulana \"Fefe\" da Silva")));

        assertThat(csv).isEqualTo("Nome\r\n\"Fulana \"\"Fefe\"\" da Silva\"\r\n");
    }

    @Test
    void quotesFieldsContainingLineBreaks() {
        String csv = CsvWriter.toCsv(List.of("Notas"), List.of(List.of("Linha 1\nLinha 2")));

        assertThat(csv).isEqualTo("Notas\r\n\"Linha 1\nLinha 2\"\r\n");
    }

    @Test
    void treatsNullFieldsAsEmptyStrings() {
        String csv = CsvWriter.toCsv(List.of("Origem"), java.util.Collections.singletonList(java.util.Collections.singletonList(null)));

        assertThat(csv).isEqualTo("Origem\r\n\r\n");
    }

    @Test
    void writesMultipleRowsInOrder() {
        String csv = CsvWriter.toCsv(
                List.of("Nome"), List.of(List.of("Ana"), List.of("Beatriz"), List.of("Carla")));

        assertThat(csv).isEqualTo("Nome\r\nAna\r\nBeatriz\r\nCarla\r\n");
    }
}
