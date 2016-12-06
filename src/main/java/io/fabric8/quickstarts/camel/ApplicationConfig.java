package io.fabric8.quickstarts.camel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "helloservice")
public class ApplicationConfig {

    private String message;

    public ApplicationConfig() {
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
