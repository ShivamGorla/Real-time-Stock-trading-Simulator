package com.stocktrading.simulator.assistant.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocktrading.simulator.assistant.client.model.ToolDefinition;
import com.stocktrading.simulator.assistant.tool.model.GetPortfolioArgs;
import com.stocktrading.simulator.dto.PortfolioResponse;
import com.stocktrading.simulator.service.PortfolioService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Assistant tool providing access to the authenticated user's complete portfolio summary.
 */
@Component
public class PortfolioTools extends AbstractAssistantTool {

    public static final String TOOL_NAME = "get_portfolio";
    public static final String TOOL_DESCRIPTION =
            "Retrieve the authenticated user's portfolio summary, including wallet cash balance, " +
            "total invested value, current market valuation, unrealized profit/loss, cumulative realized P&L, " +
            "total account equity, and position holdings.";

    private final PortfolioService portfolioService;
    private final ToolDefinition toolDefinition;

    public PortfolioTools(PortfolioService portfolioService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.portfolioService = portfolioService;
        this.toolDefinition = buildToolDefinition();
    }

    private ToolDefinition buildToolDefinition() {
        Map<String, Object> parameters = Map.of(
                "type", "object",
                "properties", Map.of(
                        "include_closed", Map.of(
                                "type", "boolean",
                                "description", "Whether to include closed positions (holdings with quantity zero). Defaults to false."
                        )
                ),
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
            GetPortfolioArgs args = parseArguments(argumentsJson, GetPortfolioArgs.class, false);
            PortfolioResponse portfolio = portfolioService.getPortfolio(username, args.getIncludeClosed());
            return ToolResult.success(serializeResult(portfolio));
        } catch (IllegalArgumentException ex) {
            return ToolResult.error(ex.getMessage());
        } catch (Exception ex) {
            return ToolResult.error("Unable to retrieve portfolio at this time.");
        }
    }
}
