package com.capgemini.futura.mws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ws.config.annotation.EnableWs;

/**
 * Spring Boot entry point for MWS Mock SOAP Service
 *
 * The service will be available at:
 * - WSDL: http://localhost:8011/mwsbasic
 * - SOAP Endpoint: http://localhost:8011/mwsbasic/mwsprocess
 */
@SpringBootApplication
@EnableWs
public class MwsMockServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MwsMockServiceApplication.class, args);
    }
}
