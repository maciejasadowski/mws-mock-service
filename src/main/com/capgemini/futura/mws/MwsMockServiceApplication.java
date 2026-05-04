package com.capgemini.futura.mws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.ws.config.annotation.EnableWs;

/**
 * Spring Boot entry point for MWS Mock SOAP Service
 *
 * The service will be available at:
 * - WSDL: http://localhost:8011/mwsbasic.wsdl
 * - SOAP Endpoint: http://localhost:8011/mwsbasic/mwsprocess
 */
@SpringBootApplication
@EnableWs
@PropertySource("classpath:default-data.properties")
public class MwsMockServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MwsMockServiceApplication.class, args);
    }
}
