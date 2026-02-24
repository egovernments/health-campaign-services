package org.egov.common.error.handler;

import org.egov.tracer.model.ErrorRes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataAccessExceptionHandlerTest {

        private final DataAccessExceptionHandler handler = new DataAccessExceptionHandler();

        @Test
        @DisplayName("should return ErrorRes with QUERY_EXECUTION_ERROR for BadSqlGrammarException")
        void shouldReturnErrorResForBadSqlGrammar() {
                DataAccessException ex = new BadSqlGrammarException(
                                "test", "SELECT * FROM missing_table",
                                new SQLException("relation \"missing_table\" does not exist"));

                ResponseEntity<ErrorRes> response = handler.handleDataAccessException(ex);

                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                assertNotNull(response.getBody());
                assertEquals(1, response.getBody().getErrors().size());
                assertEquals("QUERY_EXECUTION_ERROR", response.getBody().getErrors().get(0).getCode());
                assertTrue(response.getBody().getErrors().get(0).getMessage()
                                .contains("relation \"missing_table\" does not exist"));
        }

        @Test
        @DisplayName("should extract root cause message for QueryTimeoutException")
        void shouldExtractRootCauseMessage() {
                DataAccessException ex = new QueryTimeoutException("Query timed out",
                                new SQLException("canceling statement due to statement timeout"));

                ResponseEntity<ErrorRes> response = handler.handleDataAccessException(ex);

                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                assertEquals("QUERY_EXECUTION_ERROR", response.getBody().getErrors().get(0).getCode());
                assertTrue(response.getBody().getErrors().get(0).getMessage()
                                .contains("canceling statement due to statement timeout"));
        }

        @Test
        @DisplayName("should use fallback message when no nested cause")
        void shouldUseFallbackMessageWhenNoCause() {
                DataAccessException ex = new QueryTimeoutException("Connection pool exhausted");

                ResponseEntity<ErrorRes> response = handler.handleDataAccessException(ex);

                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                assertEquals("QUERY_EXECUTION_ERROR", response.getBody().getErrors().get(0).getCode());
                assertTrue(response.getBody().getErrors().get(0).getMessage()
                                .contains("Connection pool exhausted"));
        }

        @Test
        @DisplayName("should return failed status in ResponseInfo")
        void shouldReturnFailedStatusInResponseInfo() {
                DataAccessException ex = new QueryTimeoutException("test");

                ResponseEntity<ErrorRes> response = handler.handleDataAccessException(ex);

                assertNotNull(response.getBody().getResponseInfo());
                assertEquals("failed", response.getBody().getResponseInfo().getStatus());
        }
}
