package org.egov.product.summaryreport.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * Dedicated read-only datasource for the daily summary report.
 * <p>
 * The summary report runs a handful of aggregate (COUNT / SUM) queries across the
 * household, individual, project_beneficiary, project_task and task_resource tables.
 * These are reporting reads and must never touch the primary/write datasource, so they
 * are pointed at a read replica via {@code summary.report.datasource.*}.
 * <p>
 * Every property falls back to the primary {@code spring.datasource.*} value, so this
 * module can be dropped into a service without any new configuration and still start up
 * (it just reads the primary until a replica url is supplied).
 */
@Configuration
public class SummaryReportDataSourceConfig {

    @Value("${summary.report.datasource.url:${spring.datasource.url}}")
    private String url;

    @Value("${summary.report.datasource.username:${spring.datasource.username}}")
    private String username;

    @Value("${summary.report.datasource.password:${spring.datasource.password}}")
    private String password;

    @Value("${summary.report.datasource.driver-class-name:${spring.datasource.driver-class-name:org.postgresql.Driver}}")
    private String driverClassName;

    @Value("${summary.report.datasource.maximum-pool-size:5}")
    private int maximumPoolSize;

    @Bean(name = "summaryReportDataSource")
    public DataSource summaryReportDataSource() {
        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName(driverClassName)
                .url(url)
                .username(username)
                .password(password)
                .build();
        // Small pool - this report is called ~100 times a day, not per request.
        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setReadOnly(true);
        dataSource.setPoolName("summary-report-read-pool");
        return dataSource;
    }

    @Bean(name = "summaryReportJdbcTemplate")
    public NamedParameterJdbcTemplate summaryReportJdbcTemplate(
            @Qualifier("summaryReportDataSource") DataSource summaryReportDataSource) {
        return new NamedParameterJdbcTemplate(summaryReportDataSource);
    }
}
