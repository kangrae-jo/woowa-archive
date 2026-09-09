package com.kng0501.dbpolling.worker.domain;

@FunctionalInterface
public interface ImageGenerator {

    String generate(final String prompt);
}
