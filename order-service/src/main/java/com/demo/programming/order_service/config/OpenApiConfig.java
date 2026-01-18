package com.demo.programming.order_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .description("API for managing orders in the e-commerce platform")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Development Team")))
                .servers(List.of(
                        new Server().url("/").description("Default Server URL")));
    }
}
