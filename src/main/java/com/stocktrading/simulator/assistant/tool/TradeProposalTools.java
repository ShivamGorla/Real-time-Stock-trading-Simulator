package com.stocktrading.simulator.assistant.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocktrading.simulator.assistant.client.model.ToolDefinition;
import com.stocktrading.simulator.assistant.service.TradeProposalService;
import com.stocktrading.simulator.assistant.service.dto.TradeProposalResponse;
import com.stocktrading.simulator.assistant.tool.model.TradeProposalArgs;
import com.stocktrading.simulator.exception.ResourceNotFoundException;
import com.stocktrading.simulator.model.OrderSide;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assistant tool generating advisory, non-executable trade proposal previews.
 * Strictly calculates estimates and validates eligibility without persisting any orders or mutating balances.
 */
@Component
public class TradeProposalTools extends AbstractAssistantTool {

    public static final String TOOL_NAME = "preview_trade_proposal";
    public static final String TOOL_DESCRIPTION =
            "Generate a non-executable advisory preview of a proposed BUY or SELL stock trade. " +
            "Calculates estimated cost or proceeds and checks cash or share availability without executing any trade.";

    private final TradeProposalService tradeProposalService;
    private final ToolDefinition toolDefinition;

    public TradeProposalTools(TradeProposalService tradeProposalService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.tradeProposalService = tradeProposalService;
        this.toolDefinition = buildToolDefinition();
    }

    private ToolDefinition buildToolDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("symbol", Map.of(
                "type", "string",
                "description", "Stock ticker symbol to trade (e.g. RELIANCE, TCS, INFY)"
        ));
        properties.put("side", Map.of(
                "type", "string",
                "enum", List.of("BUY", "SELL"),
                "description", "Proposed trade side: BUY or SELL"
        ));
        properties.put("quantity", Map.of(
                "type", "integer",
                "minimum", 1,
                "maximum", TradeProposalService.MAX_PROPOSAL_QUANTITY,
                "description", "Number of shares to trade (positive integer between 1 and " + TradeProposalService.MAX_PROPOSAL_QUANTITY + ")"
        ));

        Map<String, Object> parameters = Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("symbol", "side", "quantity"),
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
            TradeProposalArgs args = parseArguments(argumentsJson, TradeProposalArgs.class, true);
            if (args.getSymbol() == null || args.getSymbol().isBlank()) {
                return ToolResult.error("Missing required parameter 'symbol'");
            }
            if (args.getSide() == null || args.getSide().isBlank()) {
                return ToolResult.error("Missing required parameter 'side' (BUY or SELL)");
            }
            if (args.getQuantity() == null) {
                return ToolResult.error("Missing required parameter 'quantity'");
            }

            OrderSide side = args.resolveOrderSide();
            TradeProposalResponse response = tradeProposalService.generateProposal(
                    username, args.getSymbol(), side, args.getQuantity()
            );

            return ToolResult.success(serializeResult(response));
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            return ToolResult.error(ex.getMessage());
        } catch (Exception ex) {
            return ToolResult.error("Failed to generate trade proposal.");
        }
    }
}
