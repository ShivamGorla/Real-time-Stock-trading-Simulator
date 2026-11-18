package com.stocktrading.simulator.assistant.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocktrading.simulator.assistant.client.model.ToolDefinition;
import com.stocktrading.simulator.assistant.service.TradeProposalService;
import com.stocktrading.simulator.assistant.tool.model.GetStockInformationArgs;
import com.stocktrading.simulator.dto.StockResponse;
import com.stocktrading.simulator.exception.ResourceNotFoundException;
import com.stocktrading.simulator.service.StockService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Assistant tool providing read-only access to the canonical StockSim simulated stock catalog.
 */
@Component
public class StockTools extends AbstractAssistantTool {

    public static final String TOOL_NAME = "get_stock_information";
    public static final String TOOL_DESCRIPTION =
            "Retrieve stock catalog information for a requested stock ticker symbol, " +
            "including company name and current simulation trading price.";

    private final StockService stockService;
    private final ToolDefinition toolDefinition;

    public StockTools(StockService stockService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.stockService = stockService;
        this.toolDefinition = buildToolDefinition();
    }

    private ToolDefinition buildToolDefinition() {
        Map<String, Object> parameters = Map.of(
                "type", "object",
                "properties", Map.of(
                        "symbol", Map.of(
                                "type", "string",
                                "description", "Stock ticker symbol (e.g. RELIANCE, TCS, INFY, HDFCBANK, ICICIBANK)"
                        )
                ),
                "required", List.of("symbol"),
                "additionalProperties", false
        );
        return new ToolDefinition(TOOL_NAME, TOOL_DESCRIPTION, parameters);
    }

    @Override
    public String getName() { return TOOL_NAME; }

    @Override
    public String getDescription() { return TOOL_DESCRIPTION; }

    @Override
    public ToolDefinition getToolDefinition() { return toolDefinition; }

    @Override
    public ToolResult execute(String username, String argumentsJson) {
        try {
            GetStockInformationArgs args = parseArguments(argumentsJson, GetStockInformationArgs.class, true);
            if (args.getSymbol() == null || args.getSymbol().isBlank()) {
                return ToolResult.error("Missing required parameter 'symbol'");
            }
            String normalizedSymbol = TradeProposalService.normalizeAndValidateSymbol(args.getSymbol());
            StockResponse response = stockService.getStockBySymbol(normalizedSymbol);
            return ToolResult.success(serializeResult(response));
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            return ToolResult.error(ex.getMessage());
        } catch (Exception ex) {
            return ToolResult.error("Failed to retrieve stock information.");
        }
    }
}
