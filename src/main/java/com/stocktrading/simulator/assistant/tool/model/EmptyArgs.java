package com.stocktrading.simulator.assistant.tool.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Empty argument DTO for tools that accept no parameters (e.g. get_wallet_balance).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmptyArgs {

    public EmptyArgs() {}
}
