package com.prideuniversal.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Serves interactive API docs at /swagger-ui.html.
 *
 * <p>Worth having early: it is how you (and later the React Native client) can
 * exercise every endpoint without writing a client first.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI prideUniversalOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Pride Universal API")
                        .version("0.1.0")
                        .description("Housing society management backend"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
