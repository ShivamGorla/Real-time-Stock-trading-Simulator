package com.stocktrading.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StockTradingSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockTradingSimulatorApplication.class, args);
    }
}
