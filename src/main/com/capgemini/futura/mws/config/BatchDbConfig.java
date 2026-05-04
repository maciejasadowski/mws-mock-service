package com.capgemini.futura.mws.config;

import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Configures the embedded H2 database that simulates the CoCo OUT-database (ModusOne).
 *
 * The batch adapter (MessageWorker) inserts processed AUFTRAGSEINGANG records here
 * instead of the real CoCo Oracle database.
 *
 * Connection details for the batch adapter adapter.ini:
 *   messagedb.url.out.ModusOne.<fachmodul>      = jdbc:h2:tcp://localhost:${batch.db.coco.tcp-port}/mem:cocodb;SCHEMA=COCO_OUT
 *   messagedb.username.out.ModusOne.<fachmodul> = ${batch.db.coco.username}
 *   messagedb.password.out.ModusOne.<fachmodul> = (empty)
 */
@Configuration
public class BatchDbConfig {

    private static final Logger logger = LoggerFactory.getLogger(BatchDbConfig.class);

    @Value("${batch.db.coco.url}")
    private String url;

    @Value("${batch.db.coco.username}")
    private String username;

    @Value("${batch.db.coco.password}")
    private String password;

    @Value("${batch.db.coco.tcp-port}")
    private int tcpPort;

    @Bean(name = "cocoDataSource")
    @Primary
    public DataSource cocoDataSource() throws SQLException {
        SimpleDriverDataSource ds = new SimpleDriverDataSource();
        ds.setDriverClass(org.h2.Driver.class);
        ds.setUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);

        // Step 1: create the schema (connect without SCHEMA= in URL)
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("db/coco-out-schema.sql"));
        populator.execute(ds);

        // Step 2: switch the datasource to always use COCO_OUT as default schema
        ds.setUrl(url + ";SCHEMA=COCO_OUT");

        logger.info("CoCo OUT database (H2) initialised - schema: COCO_OUT");
        return ds;
    }

    @Bean(name = "cocoJdbcTemplate")
    public JdbcTemplate cocoJdbcTemplate() throws SQLException {
        return new JdbcTemplate(cocoDataSource());
    }

    /**
     * Exposes the H2 database over TCP so the batch adapter JAR (running in a
     * separate JVM) can connect to it via JDBC.
     * Adapter JDBC URL: jdbc:h2:tcp://localhost:9092/mem:cocodb;SCHEMA=COCO_OUT
     */
    @Bean(destroyMethod = "stop")
    public Server h2TcpServer() throws SQLException {
        Server server = Server.createTcpServer(
                "-tcp",
                "-tcpAllowOthers",
                "-tcpPort", String.valueOf(tcpPort),
                "-ifNotExists"
        ).start();
        logger.info("H2 TCP server started on port {} for CoCo OUT mock", tcpPort);
        return server;
    }
}
