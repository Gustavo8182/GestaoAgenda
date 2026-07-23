package br.com.agendaplatform.shared.web;

import java.util.List;

/**
 * Escrita mínima de CSV (RFC 4180): aspas só quando o campo contém vírgula, aspas ou quebra de
 * linha, com aspas internas duplicadas. Não usa nenhuma biblioteca externa porque as exportações
 * deste projeto são sempre linhas simples de texto/data, sem necessidade de nada mais elaborado.
 */
public final class CsvWriter {

    private static final String LINE_BREAK = "\r\n";

    private CsvWriter() {
    }

    public static String toCsv(List<String> header, List<List<String>> rows) {
        StringBuilder csv = new StringBuilder();
        writeRow(csv, header);
        for (List<String> row : rows) {
            writeRow(csv, row);
        }
        return csv.toString();
    }

    private static void writeRow(StringBuilder csv, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escape(fields.get(i)));
        }
        csv.append(LINE_BREAK);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!needsQuoting) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
