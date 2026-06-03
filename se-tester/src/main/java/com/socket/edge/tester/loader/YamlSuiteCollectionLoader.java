package com.socket.edge.tester.loader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.socket.edge.tester.model.SuiteCollection;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class YamlSuiteCollectionLoader {

    private final ObjectMapper mapper;

    public YamlSuiteCollectionLoader() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public SuiteCollection load(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            return mapper.readValue(is, SuiteCollection.class);
        }
    }
}
