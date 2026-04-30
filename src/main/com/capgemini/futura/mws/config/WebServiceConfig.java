package com.capgemini.futura.mws.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.SimpleWsdl11Definition;

/**
 * Web Service Configuration for SOAP endpoints
 * Configures the SOAP message dispatcher and WSDL definition
 */
@Configuration
@EnableWs
public class WebServiceConfig extends WsConfigurerAdapter {

    /**
     * Register the SOAP message dispatcher servlet
     */
    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
            ApplicationContext applicationContext) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/mwsbasic/*");
    }

    /**
     * Define the WSDL11 definition for the service
     * Makes WSDL available at: http://localhost:8011/mwsbasic/mwsbasic.wsdl
     */
    @Bean(name = "mwsbasic")
    public SimpleWsdl11Definition defaultWsdl11Definition() {
        // Serve a static WSDL from the classpath
        return new SimpleWsdl11Definition(new ClassPathResource("mwsbasic.wsdl"));
    }
}