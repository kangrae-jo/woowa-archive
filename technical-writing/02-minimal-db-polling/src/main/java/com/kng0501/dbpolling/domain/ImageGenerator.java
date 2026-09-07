package com.kng0501.dbpolling.domain;

@FunctionalInterface
public interface ImageGenerator {

    String generate(final String prompt);
}
