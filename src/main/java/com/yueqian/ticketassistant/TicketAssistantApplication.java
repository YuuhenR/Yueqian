package com.yueqian.ticketassistant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.yueqian.ticketassistant.mapper")
@SpringBootApplication
public class TicketAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketAssistantApplication.class, args);
    }
}
