package de.dkwr.serverfromspringai;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.List;

@SpringBootApplication
public class ServerFromSpringAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServerFromSpringAiApplication.class, args);
    }
}

@Component
class McpServer {

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> tools() {
        var schema = """
                    {
                      "type" : "object",
                      "id" : "urn:jsonschema:Operation",
                      "properties" : {
                        "operation" : {
                          "type" : "string"
                        },
                        "a" : {
                          "type" : "number"
                        },
                        "b" : {
                          "type" : "number"
                        }
                      }
                    }
                    """;

        var tool = new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("calculator", "Run a calculation for the given operation and parameters", schema),
                (exchange, arguments) -> {
                    // Tool implementation
                    return new McpSchema.CallToolResult("The result would appear here...", true);
                }
        );

        return List.of(tool);
    }
}
