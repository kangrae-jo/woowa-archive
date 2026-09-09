package com.kng0501.dbqueue.worker.domain;

@FunctionalInterface
public interface ImageGenerator {
    String generate(final String prompt) throws Exception;
}
