package github.vijay_papanaboina.cloud_storage_api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/**
 * Web configuration for Spring Data pagination support.
 * Enables DTO serialization mode for Page objects to ensure stable JSON
 * structure.
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class WebConfig {
    // Configuration class - no additional methods needed
}
