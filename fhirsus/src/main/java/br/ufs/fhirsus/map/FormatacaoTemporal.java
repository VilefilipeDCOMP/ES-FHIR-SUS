package br.ufs.fhirsus.map;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

final class FormatacaoTemporal {
    private static final String FHIR_DATE_TIME = "FHIR_DATE_TIME";
    private static final int LIMITE_OFFSET_FHIR_SEGUNDOS = 14 * 60 * 60;
    private static final DateTimeFormatter LER_DATA_HORA_FHIR = formatoDataHoraFhir(1);
    private static final DateTimeFormatter FORMATAR_DATA_HORA_FHIR = formatoDataHoraFhir(0);
    private static final Pattern CHAMADA = Pattern.compile("formatar_data\\((.*)\\)");
    private static final Pattern ARGUMENTO = Pattern.compile("\\s*(?:\"((?:[^\"]|\"\")*)\"|([^\",()]+?))\\s*(,|$)");

    private FormatacaoTemporal() {}

    static UnaryOperator<String> compilar(String chamada) {
        var partes = CHAMADA.matcher(chamada);
        if (!partes.matches()) {
            throw new IllegalArgumentException("Informe uma transformação do catálogo ou uma chamada formatar_data(...).");
        }

        List<String> argumentos = lerArgumentos(partes.group(1));
        if (argumentos.size() < 2 || argumentos.size() > 4) {
            throw new IllegalArgumentException("Informe os formatos de origem e destino e os fusos opcionais.");
        }

        String formatoOrigem = argumentos.get(0);
        String formatoDestino = argumentos.get(1);
        boolean origemFhir = FHIR_DATE_TIME.equals(formatoOrigem);
        boolean destinoFhir = FHIR_DATE_TIME.equals(formatoDestino);
        DateTimeFormatter origem = origemFhir ? LER_DATA_HORA_FHIR : criarFormatador(formatoOrigem);
        DateTimeFormatter destino = destinoFhir ? FORMATAR_DATA_HORA_FHIR : criarFormatador(formatoDestino);
        Fusos zonas = lerFusos(argumentos.subList(2, argumentos.size()));

        return valor -> {
            TemporalAccessor lido = origem.parse(valor);
            if (lido.query(TemporalQueries.localTime()) == null) {
                if (destinoFhir) {
                    throw new DateTimeException("O destino FHIR_DATE_TIME exige data e hora completas.");
                }
                return destino.format(LocalDate.from(lido));
            }

            TemporalAccessor dataHora = resolverDataHora(lido, zonas.origem());
            if (origemFhir) {
                verificarDataHoraFhir(dataHora);
            }
            if (zonas.destino() != null) {
                dataHora = ZonedDateTime.ofInstant(OffsetDateTime.from(dataHora).toInstant(), zonas.destino());
            }
            if (destinoFhir) {
                verificarDataHoraFhir(dataHora);
            }
            return destino.format(dataHora);
        };
    }

    private static List<String> lerArgumentos(String texto) {
        List<String> argumentos = new ArrayList<>();
        var argumento = ARGUMENTO.matcher(texto);
        int inicio = 0;
        do {
            if (!argumento.region(inicio, texto.length()).lookingAt()) {
                throw new IllegalArgumentException("Argumentos inválidos na chamada de formatação.");
            }
            String valor = argumento.group(1) == null
                    ? argumento.group(2).strip()
                    : argumento.group(1).replace("\"\"", "\"");
            if (valor.isBlank()) {
                throw new IllegalArgumentException("Os argumentos da formatação não podem estar vazios.");
            }
            argumentos.add(valor);
            inicio = argumento.end();
        } while (",".equals(argumento.group(3)));
        return argumentos;
    }

    private static DateTimeFormatter criarFormatador(String formato) {
        return DateTimeFormatter.ofPattern(formato, Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    }

    private static DateTimeFormatter formatoDataHoraFhir(int minimoDigitosFracao) {
        return new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .appendLiteral('T')
                .appendPattern("HH:mm:ss")
                .optionalStart()
                .appendFraction(ChronoField.NANO_OF_SECOND, minimoDigitosFracao, 9, true)
                .optionalEnd()
                .appendOffset("+HH:MM", "Z")
                .toFormatter(Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT);
    }

    private static Fusos lerFusos(List<String> argumentos) {
        ZoneId origem = null;
        ZoneId destino = null;
        for (int i = 0; i < argumentos.size(); i++) {
            String argumento = argumentos.get(i);
            if (argumento.startsWith("zonaDestino=") && destino == null) {
                destino = lerZona(argumento.substring("zonaDestino=".length()));
            } else if (argumento.startsWith("zonaOrigem=") && origem == null) {
                origem = lerZona(argumento.substring("zonaOrigem=".length()));
            } else if (i == 0 && !argumento.contains("=")) {
                origem = lerZona(argumento);
            } else {
                throw new IllegalArgumentException("Informe zonaOrigem e zonaDestino no máximo uma vez.");
            }
        }
        return new Fusos(origem, destino);
    }

    private static ZoneId lerZona(String nome) {
        try {
            return ZoneId.of(nome.strip());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Fuso inválido: " + nome, e);
        }
    }

    private static TemporalAccessor resolverDataHora(TemporalAccessor lido, ZoneId zonaOrigem) {
        LocalDateTime dataHora = LocalDateTime.from(lido);
        var offset = lido.query(TemporalQueries.offset());
        ZoneId zonaLida = lido.query(TemporalQueries.zoneId());
        if (offset != null) {
            return zonaLida == null ? dataHora.atOffset(offset) : ZonedDateTime.ofStrict(dataHora, offset, zonaLida);
        }

        ZoneId zona = zonaLida == null ? zonaOrigem : zonaLida;
        if (zona == null) {
            return dataHora;
        }

        var offsets = zona.getRules().getValidOffsets(dataHora);
        if (offsets.size() != 1) {
            throw new DateTimeException("Horário local ambíguo ou inexistente no fuso " + zona + ".");
        }
        return ZonedDateTime.ofStrict(dataHora, offsets.get(0), zona);
    }

    private static void verificarDataHoraFhir(TemporalAccessor dataHora) {
        var valor = OffsetDateTime.from(dataHora);
        int segundosOffset = valor.getOffset().getTotalSeconds();
        if (valor.getYear() < 1 || valor.getYear() > 9999
                || segundosOffset % 60 != 0 || Math.abs(segundosOffset) > LIMITE_OFFSET_FHIR_SEGUNDOS) {
            throw new DateTimeException("Ano ou offset fora do formato FHIR dateTime.");
        }
    }

    private record Fusos(ZoneId origem, ZoneId destino) {}
}
