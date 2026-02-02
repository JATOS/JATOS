package mcp.gui;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class McpToolRegistry {

    private final List<McpTool> tools;

    @Inject
    public McpToolRegistry(McpToolLoader loader) {
        this.tools = loader.load();
    }

    public List<McpTool> getTools() {
        return tools;
    }
}
