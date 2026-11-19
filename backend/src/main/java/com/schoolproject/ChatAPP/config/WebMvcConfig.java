package com.schoolproject.ChatAPP.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration.
 * Private attachments under /uploads/files/** are intentionally NOT exposed as static
 * resources; all downloads are served exclusively by FileController with authentication
 * and conversation/channel membership authorization.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
}
