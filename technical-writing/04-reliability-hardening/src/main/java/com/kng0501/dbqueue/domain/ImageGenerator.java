package com.kng0501.dbqueue.domain;

@FunctionalInterface
public interface ImageGenerator {
    String generate(final String prompt) throws Exception;
}
