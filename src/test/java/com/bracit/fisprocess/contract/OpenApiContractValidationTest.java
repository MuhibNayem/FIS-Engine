package com.bracit.fisprocess.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenAPI Contract Validation Tests")
class OpenApiContractValidationTest {

    private static final Path OPENAPI_PATH = Path.of("src/main/resources/static/openapi.yaml");

    @Test
    @DisplayName("openapi.yaml should be parseable and include mandatory top-level sections")
    void openApiYamlShouldBeParseable() throws Exception {
        assertThat(Files.exists(OPENAPI_PATH)).isTrue();
        String yamlText = Files.readString(OPENAPI_PATH);
        Object loaded = new Yaml().load(yamlText);
        assertThat(loaded).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) loaded;
        assertThat(root).containsKeys("openapi", "paths", "components");
        assertThat(root.get("paths")).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> paths = (Map<String, Object>) root.get("paths");
        assertThat(paths).isNotEmpty();
    }
}
