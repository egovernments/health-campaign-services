package org.egov.product.summaryreport.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * Datasource wiring for the daily summary report.
 * <p>
 * By default the report reuses the service's primary {@link DataSource} - the same
 * (correctly configured) connection the rest of the app uses - so the module needs zero
 * extra configuration to run in any environment.
 * <p>
 * To offload these reporting reads to a read replica, set {@code summary.report.datasource.url}
 * (and username/password). Only then is a dedicated, read-only Hikari pool created; the
 * report is called ~100 times a day, so the pool is intentionally small.
 */
@Configuration
@Slf4j
public class SummaryReportDataSourceConfig {

    @Value("${summary.report.datasource.url:}")
    private String url;

    @Value("${summary.report.datasource.username:}")
    private String username;

    @Value("${summary.report.datasource.password:}")
    private String password;

    @Value("${summary.report.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Value("${summary.report.datasource.maximum-pool-size:5}")
    private int maximumPoolSize;

    @Bean(name = "summaryReportJdbcTemplate")
    public NamedParameterJdbcTemplate summaryReportJdbcTemplate(DataSource primaryDataSource) {
        if (url == null || url.trim().isEmpty()) {
            log.info("summary.report.datasource.url not set - summary report reuses the primary datasource");
            return new NamedParameterJdbcTemplate(primaryDataSource);
        }
        log.info("summary.report.datasource.url set - summary report uses a dedicated read-only pool");
        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName(driverClassName)
                .url(url)
                .username(username)
                .password(password)
                .build();
        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setReadOnly(true);
        dataSource.setPoolName("summary-report-read-pool");
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
