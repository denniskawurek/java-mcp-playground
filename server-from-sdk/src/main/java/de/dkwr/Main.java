package de.dkwr;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import jakarta.servlet.http.HttpServlet;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import reactor.core.publisher.Mono;
import org.apache.catalina.startup.*;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        new McpService().init();
    }
}

class McpService {

    public void init() {

        final var transportProvider = transportProvider();
        startServletContainer((HttpServlet) transportProvider);

        System.err.println("Initializing MCP server...");

        initSyncServer(transportProvider);
    }

    private McpServerTransportProvider transportProvider() {
        return new HttpServletSseServerTransportProvider(new ObjectMapper(), "/msg", "/sse");
    }

    private void startServletContainer(HttpServlet transportProvider) {
        // any servlet container works - use Tomcat as in SDK samples
        var port = 8080;
        var tomcat = new Tomcat();
        tomcat.setPort(port);

        String baseDir = System.getProperty("java.io.tmpdir");
        tomcat.setBaseDir(baseDir);

        Context context = tomcat.addContext("", baseDir);

        // Add transport servlet to Tomcat
        org.apache.catalina.Wrapper wrapper = context.createWrapper();
        wrapper.setName("mcpServlet");
        wrapper.setServlet(transportProvider);
        wrapper.setLoadOnStartup(1);
        wrapper.setAsyncSupported(true);
        context.addChild(wrapper);
        context.addServletMappingDecoded("/*", "mcpServlet");

        var connector = tomcat.getConnector();
        connector.setAsyncTimeout(3000);

        // Add your servlets/contexts here

        try {
            tomcat.start();
        } catch (LifecycleException e) {
        }
    }


    // Init the sync server in the same way as it's done in the official documentation
    private void initSyncServer(McpServerTransportProvider transportProvider) {
        McpSyncServer syncServer = McpServer.sync(transportProvider)
                .serverInfo("my-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .resources(true, false)     // Enable resource support
                        .tools(true)         // Enable tool support
                        .prompts(true)       // Enable prompt support
                        .logging()           // Enable logging support
                        .build())
                .build();

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
        var syncToolSpecification = new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("basic-calculator", "Basic calculator", schema),
                (exchange, arguments) -> {
                    // Tool implementation
                    System.out.println("Calling basic-calculator");
                    return new McpSchema.CallToolResult("result...", false);
                }
        );

        // Sync resource specification
        var syncResourceSpecification = new McpServerFeatures.SyncResourceSpecification(
                new McpSchema.Resource("custom://resource", "name", "description", "mime-type", null),
                (exchange, request) -> {
                    // Resource read implementation
                    return new McpSchema.ReadResourceResult(List.of());
                }
        );

        // Sync prompt specification
        var syncPromptSpecification = new McpServerFeatures.SyncPromptSpecification(
                new McpSchema.Prompt("greeting", "description", List.of(
                        new McpSchema.PromptArgument("name", "description", true)
                )),
                (exchange, request) -> {
                    // Prompt implementation
                    return new McpSchema.GetPromptResult("description", List.of());
                }
        );

        // Register tools, resources, and prompts
        syncServer.addTool(syncToolSpecification);
        syncServer.addResource(syncResourceSpecification);
        syncServer.addPrompt(syncPromptSpecification);

        syncServer.loggingNotification(McpSchema.LoggingMessageNotification.builder()
                .level(McpSchema.LoggingLevel.INFO)
                .logger("custom-logger")
                .data("Custom log message")
                .build());

        var capabilities = McpSchema.ServerCapabilities.builder()
                .resources(false, true)  // Resource support with list changes notifications
                .tools(true)            // Tool support with list changes notifications
                .prompts(true)          // Prompt support with list changes notifications
                .logging()              // Enable logging support (enabled by default with logging level INFO)
                .build();

        // Define a tool that uses sampling
        var calculatorTool = new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("ai-calculator", "Performs calculations using AI", schema),
                (exchange, arguments) -> {
                    // Check if client supports sampling
                    if (exchange.getClientCapabilities().sampling() == null) {
                        return new McpSchema.CallToolResult("Client does not support AI capabilities", false);
                    }

                    // Create a sampling request
                    McpSchema.CreateMessageRequest request = McpSchema.CreateMessageRequest.builder()
                            .messages(List.of(new McpSchema.SamplingMessage(McpSchema.Role.USER,
                                    new McpSchema.TextContent("Calculate: " + arguments.get("expression")))))
                            .modelPreferences(McpSchema.ModelPreferences.builder()
                                    .hints(List.of(
                                            McpSchema.ModelHint.of("claude-3-sonnet"),
                                            McpSchema.ModelHint.of("claude")
                                    ))
                                    .intelligencePriority(0.8)  // Prioritize intelligence
                                    .speedPriority(0.5)         // Moderate speed importance
                                    .build())
                            .systemPrompt("You are a helpful calculator assistant. Provide only the numerical answer.")
                            .maxTokens(100)
                            .build();

                    // Request sampling from the client
                    McpSchema.CreateMessageResult result = exchange.createMessage(request);

                    // Process the result
                    String answer = ((McpSchema.TextContent) result.content()).text();
                    return new McpSchema.CallToolResult(answer, false);
                }
        );

        // Add the tool to the server

        var tool = new McpServerFeatures.AsyncToolSpecification(
                new McpSchema.Tool("logging-test", "Test logging notifications", "{}"),
                (exchange, request) -> {

                    exchange.loggingNotification( // Use the exchange to send log messages
                                    McpSchema.LoggingMessageNotification.builder()
                                            .level(McpSchema.LoggingLevel.DEBUG)
                                            .logger("test-logger")
                                            .data("Debug message")
                                            .build())
                            .block();

                    return Mono.just(new McpSchema.CallToolResult("Logging test completed", false));
                });
    }
}
