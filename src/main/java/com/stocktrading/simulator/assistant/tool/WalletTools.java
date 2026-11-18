package com.stocktrading.simulator.assistant.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocktrading.simulator.assistant.client.model.ToolDefinition;
import com.stocktrading.simulator.assistant.tool.model.EmptyArgs;
import com.stocktrading.simulator.dto.WalletResponse;
import com.stocktrading.simulator.service.WalletService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Assistant tool providing access to the authenticated user's virtual wallet balance.
 */
@Component
public class WalletTools extends AbstractAssistantTool {

    public static final String TOOL_NAME = "get_wallet_balance";
    public static final String TOOL_DESCRIPTION =
            "Retrieve the authenticated user's virtual wallet cash balance and account currency.";

    private final WalletService walletService;
    private final ToolDefinition toolDefinition;

    public WalletTools(WalletService walletService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.walletService = walletService;
        this.toolDefinition = buildToolDefinition();
    }

    private ToolDefinition buildToolDefinition() {
        Map<String, Object> parameters = Map.of(
                "type", "object",
                "properties", Map.of(),
                "additionalProperties", false
        );
        return new ToolDefinition(TOOL_NAME, TOOL_DESCRIPTION, parameters);
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return TOOL_DESCRIPTION;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public ToolResult execute(String username, String argumentsJson) {
        try {
            parseArguments(argumentsJson, EmptyArgs.class, false);
            WalletResponse wallet = walletService.getWalletResponseByUsername(username);
            return ToolResult.success(serializeResult(wallet));
        } catch (IllegalArgumentException ex) {
            return ToolResult.error(ex.getMessage());
        } catch (Exception ex) {
            return ToolResult.error("Unable to retrieve wallet balance at this time.");
        }
    }
}
