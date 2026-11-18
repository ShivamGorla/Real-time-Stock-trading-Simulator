package com.stocktrading.simulator.assistant.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocktrading.simulator.assistant.client.model.ToolDefinition;
import com.stocktrading.simulator.assistant.tool.model.GetOrderHistoryArgs;
import com.stocktrading.simulator.dto.OrderResponse;
import com.stocktrading.simulator.exception.ResourceNotFoundException;
import com.stocktrading.simulator.model.OrderSide;
import com.stocktrading.simulator.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only assistant tool providing bounded, paginated access to the authenticated user's order history
 * via the canonical {@link OrderService#getUserOrders(String)} query.
 */
@Component
public class OrderTools extends AbstractAssistantTool {

    public static final String TOOL_NAME = "get_order_history";
    public static final String TOOL_DESCRIPTION =
            "Retrieve the authenticated user's paginated historical stock trade orders, " +
            "with optional filtering by order type (BUY or SELL) and stock ticker symbol.";
    public static final int MAX_PAGE_SIZE = 50;

    private final OrderService orderService;
    private final ToolDefinition toolDefinition;

    public OrderTools(OrderService orderService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.orderService = orderService;
        this.toolDefinition = buildToolDefinition();
    }

    private ToolDefinition buildToolDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("type", Map.of(
                "type", "string",
                "enum", List.of("BUY", "SELL"),
                "description", "Optional filter by order side: BUY or SELL"
        ));
        properties.put("symbol", Map.of(
                "type", "string",
                "description", "Optional filter by stock ticker symbol (e.g. RELIANCE, TCS, INFY)"
        ));
        properties.put("page", Map.of(
                "type", "integer",
                "minimum", 0,
                "description", "Zero-based page index. Defaults to 0."
        ));
        properties.put("size", Map.of(
                "type", "integer",
                "minimum", 1,
                "maximum", MAX_PAGE_SIZE,
                "description", "Number of orders per page (1 to " + MAX_PAGE_SIZE + "). Defaults to 20."
        ));

        Map<String, Object> parameters = Map.of(
                "type", "object",
                "properties", properties,
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
            GetOrderHistoryArgs args = parseArguments(argumentsJson, GetOrderHistoryArgs.class, false);

            if (args.getPage() < 0) {
                return ToolResult.error("Page index must be zero or positive");
            }
            if (args.getSize() <= 0) {
                return ToolResult.error("Page size must be greater than zero");
            }
            int effectiveSize = Math.min(args.getSize(), MAX_PAGE_SIZE);
            OrderSide sideFilter = args.resolveOrderSide();
            String symbolFilter = (args.getSymbol() != null && !args.getSymbol().isBlank())
                    ? args.getSymbol().trim().toUpperCase()
                    : null;

            Page<OrderResponse> pageResult = orderService.getUserOrdersPage(
                    username, sideFilter, symbolFilter, args.getPage(), effectiveSize);

            Map<String, Object> responseMap = new LinkedHashMap<>();
            responseMap.put("orders", pageResult.getContent());
            responseMap.put("page", args.getPage());
            responseMap.put("size", effectiveSize);
            responseMap.put("totalElements", pageResult.getTotalElements());
            responseMap.put("totalPages", pageResult.getTotalPages());

            return ToolResult.success(serializeResult(responseMap));
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            return ToolResult.error(ex.getMessage());
        } catch (Exception ex) {
            return ToolResult.error("Failed to retrieve order history.");
        }
    }
}
