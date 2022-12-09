package org.egov.project.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private ProjectRepository projectRepository;


    @BeforeEach
    void setUp() {
        projectRepository = new ProjectRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("should return true if project exists")
    void shouldReturnTrueIfProjectExists() {
        String projectId = "projectId";
        Map<String, String> params = Collections.singletonMap("projectId", projectId);

        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PROJECT WHERE id=:projectId", params, Integer.class)).thenReturn(1);
        boolean projectExists = projectRepository.checkIfProjectExists(projectId);
        assertEquals(projectExists, true);
    }

    @Test
    @DisplayName("should return false if project dont exists")
    void shouldReturnFalseIfProjectDontExists() {
        String projectId = "projectId";
        Map<String, String> params = Collections.singletonMap("projectId", projectId);

        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PROJECT WHERE id=:projectId", params, Integer.class)).thenReturn(0);
        boolean projectExists = projectRepository.checkIfProjectExists(projectId);
        assertEquals(projectExists, false);
    }

}