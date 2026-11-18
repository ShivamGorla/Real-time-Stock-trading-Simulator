package com.stocktrading.simulator.assistant.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocktrading.simulator.assistant.client.model.ToolDefinition;
import com.stocktrading.simulator.assistant.tool.model.GetHoldingsArgs;
import com.stocktrading.simulator.dto.HoldingResponse;
import com.stocktrading.simulator.dto.PortfolioResponse;
import com.stocktrading.simulator.service.PortfolioService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Assistant tool providing access to the authenticated user's stock holdings.
 */
@Component
public class HoldingsTools extends AbstractAssistantTool {

    public static final String TOOL_NAME = "get_holdings";
    public static final String TOOL_DESCRIPTION =
            "Retrieve the authenticated user's individual stock holdings with position quantities, " +
            "average purchase costs, current market valuations, and unrealized profit/loss.";

    private final PortfolioService portfolioService;
    private final ToolDefinition toolDefinition;

    public HoldingsTools(PortfolioService portfolioService, ObjectMapper objectMapper) {
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
                                "description", "Whether to include closed holdings (quantity zero). Defaults to false."
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
            GetHoldingsArgs args = parseArguments(argumentsJson, GetHoldingsArgs.class, false);
            PortfolioResponse portfolio = portfolioService.getPortfolio(username, args.getIncludeClosed());
            List<HoldingResponse> holdings = portfolio.getHoldings() != null ? portfolio.getHoldings() : List.of();
            return ToolResult.success(serializeResult(holdings));
        } catch (IllegalArgumentException ex) {
            return ToolResult.error(ex.getMessage());
        } catch (Exception ex) {
            return ToolResult.error("Unable to retrieve holdings at this time.");
        }
    }
}
