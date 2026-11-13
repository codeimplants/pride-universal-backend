package com.prideuniversal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

// No UserDetailsService: authentication is OTP + JWT (and Nexus's service token).
// Without the exclusion Boot creates an in-memory user and logs its password at startup.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
@EnableCaching
public class PrideUniversalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrideUniversalApplication.class, args);
    }
}
