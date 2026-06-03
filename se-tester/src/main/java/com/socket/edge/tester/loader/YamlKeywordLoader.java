package com.socket.edge.tester.loader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.socket.edge.tester.model.Keyword;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class YamlKeywordLoader {

    private final ObjectMapper mapper;

    public YamlKeywordLoader() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, false);
    }

    public Keyword load(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            return mapper.readValue(is, Keyword.class);
        }
    }
}
