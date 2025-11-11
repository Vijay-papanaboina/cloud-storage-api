package github.vijay_papanaboina.cloud_storage_api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration for SpringDoc.
 * Configures API metadata and security schemes for Swagger UI documentation.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Configures OpenAPI metadata and security schemes.
     *
     * @return OpenAPI bean with API information and security definitions
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cloud Storage API")
                        .description("A comprehensive cloud storage platform for file management. " +
                                "Supports file upload, download, organization, search, and transformations. " +
                                "Uses JWT Bearer tokens and API Keys for authentication.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Cloud Storage API")
                                .email("support@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Bearer token authentication. " +
                                        "Obtain a token via /api/auth/login or /api/auth/register. " +
                                        "Include the token in the Authorization header as: Bearer <token>"))
                        .addSecuritySchemes("api-key", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("API Key authentication. " +
                                        "Generate an API key via /api/auth/api-keys. " +
                                        "Include the key in the X-API-Key header.")));
    }
}
