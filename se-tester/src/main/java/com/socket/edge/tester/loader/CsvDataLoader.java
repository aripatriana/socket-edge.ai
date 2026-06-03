package com.socket.edge.tester.loader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a CSV file into a list of rows, each row as Map&lt;header, value&gt;.
 *
 * Rules:
 * - First non-empty, non-comment line = header
 * - Lines starting with # are skipped
 * - Supports double-quoted fields (allows comma inside)
 * - Trims leading/trailing whitespace from values
 */
public class CsvDataLoader {

    public List<Map<String, String>> load(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        String[] headers = null;
        List<Map<String, String>> rows = new ArrayList<>();

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] fields = parseLine(line);

            if (headers == null) {
                headers = fields;
                for (int i = 0; i < headers.length; i++) headers[i] = headers[i].trim();
                continue;
            }

            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++) {
                row.put(headers[i], i < fields.length ? fields[i].trim() : "");
            }
            rows.add(row);
        }

        return rows;
    }

    private String[] parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                // Handle escaped quote ""
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}
