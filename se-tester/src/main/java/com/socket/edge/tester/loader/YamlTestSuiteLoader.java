package com.socket.edge.tester.loader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.socket.edge.tester.model.TestSuite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class YamlTestSuiteLoader {

    private final ObjectMapper mapper;

    public YamlTestSuiteLoader() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public TestSuite load(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            return mapper.readValue(is, TestSuite.class);
        }
    }
}
