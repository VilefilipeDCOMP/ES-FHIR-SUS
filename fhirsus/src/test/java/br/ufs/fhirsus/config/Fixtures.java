package br.ufs.fhirsus.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class Fixtures {
    private Fixtures() {}

    static String ler(String nome) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + nome)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("Fixture não encontrada: " + nome, e);
        }
    }
}
