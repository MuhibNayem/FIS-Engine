package com.bracit.fisprocess.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenAPI Controller Coverage Tests")
class OpenApiControllerCoverageTest {

    private static final Path OPENAPI_PATH = Path.of("src/main/resources/static/openapi.yaml");
    private static final Path CONTROLLERS_DIR = Path.of("src/main/java/com/bracit/fisprocess/controller");

    private static final Pattern CLASS_MAPPING = Pattern.compile("@RequestMapping\\(\"([^\"]*)\"\\)");
    private static final Pattern METHOD_MAPPING = Pattern.compile(
            "@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)\\s*(\\(([^)]*)\\))?");
    private static final Pattern NAMED_PATH_ARG = Pattern.compile("(?:value|path)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern DIRECT_PATH_ARG = Pattern.compile("^\\s*\"([^\"]*)\"");

    @Test
    @DisplayName("every controller endpoint path should exist in openapi.yaml")
    void everyControllerPathShouldExistInOpenApi() throws Exception {
        Set<String> openApiPaths = loadOpenApiPaths();
        Set<String> controllerPaths = loadControllerPaths();

        Set<String> missing = new HashSet<>(controllerPaths);
        missing.removeAll(openApiPaths);

        assertThat(missing)
                .withFailMessage("Controller paths missing in OpenAPI: %s", missing)
                .isEmpty();
    }

    private Set<String> loadOpenApiPaths() throws IOException {
        String yamlText = Files.readString(OPENAPI_PATH);
        Object loaded = new Yaml().load(yamlText);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) loaded;
        @SuppressWarnings("unchecked")
        Map<String, Object> paths = (Map<String, Object>) root.get("paths");
        return paths.keySet();
    }

    private Set<String> loadControllerPaths() throws IOException {
        Set<String> result = new HashSet<>();
        try (Stream<Path> stream = Files.list(CONTROLLERS_DIR)) {
            for (Path file : stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .toList()) {
                String source = Files.readString(file);
                String classPrefix = extractClassPrefix(source);
                Matcher matcher = METHOD_MAPPING.matcher(source);
                while (matcher.find()) {
                    String args = matcher.group(3);
                    String methodPath = extractMethodPath(args);
                    String combined = normalizePath(classPrefix + methodPath);
                    if (combined.startsWith("/v1/") || "/v1".equals(combined)) {
                        String openApiStylePath = combined.substring("/v1".length());
                        if (openApiStylePath.isBlank()) {
                            openApiStylePath = "/";
                        }
                        result.add(openApiStylePath);
                    }
                }
            }
        }
        return result;
    }

    private String extractClassPrefix(String source) {
        Matcher matcher = CLASS_MAPPING.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String extractMethodPath(String args) {
        if (args == null || args.isBlank()) {
            return "";
        }
        Matcher named = NAMED_PATH_ARG.matcher(args);
        if (named.find()) {
            return named.group(1);
        }
        Matcher direct = DIRECT_PATH_ARG.matcher(args);
        if (direct.find()) {
            return direct.group(1);
        }
        return "";
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return "/";
        }
        String normalized = value.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        normalized = normalized.replaceAll("/{2,}", "/");
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
