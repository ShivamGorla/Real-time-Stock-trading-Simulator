package com.stocktrading.simulator.assistant.tool.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetPortfolioArgs {

    @JsonProperty("include_closed")
    @JsonAlias({"includeClosed", "include_closed"})
    private Boolean includeClosed = false;

    public GetPortfolioArgs() {}

    public GetPortfolioArgs(Boolean includeClosed) {
        this.includeClosed = includeClosed;
    }

    public Boolean getIncludeClosed() {
        return includeClosed != null && includeClosed;
    }

    public void setIncludeClosed(Boolean includeClosed) {
        this.includeClosed = includeClosed;
    }
}
