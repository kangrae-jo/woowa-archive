package com.kng0501.dbqueue.domain;

@FunctionalInterface
public interface ImageGenerator {
    String generate(String prompt) throws Exception;
}
