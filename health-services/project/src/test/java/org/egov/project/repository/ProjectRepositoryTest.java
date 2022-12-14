package org.egov.project.repository;

import org.egov.project.mapper.ProjectIdMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @InjectMocks
    private ProjectRepository projectRepository;

    @BeforeEach
    void setUp() {
        projectRepository = new ProjectRepository(namedParameterJdbcTemplate);
    }

    @Test
    @DisplayName("should return list of valid project ids given a list of projects ids")
    void shouldReturnListOfValidProjectIdsGivenAListOfProjectIds() {
        List<String> projectIds = Arrays.asList("project1", "project2", "project3");
        Map<String, Object> params = new HashMap<>();
        params.put("projectIds", projectIds);

        when(
                namedParameterJdbcTemplate.queryForObject(
                eq(
                        String.format(
                            "SELECT id FROM project WHERE id IN (:projectIds) AND isDeleted = false fetch first %s rows only",
                            projectIds.size()
                        )
                ),
                eq(params),
                any(ProjectIdMapper.class)
            )
        ).thenReturn(projectIds);

        List<String> projectExists = projectRepository.validateProjectId(projectIds);
        assertEquals(projectExists.size(), projectIds.size());
    }

}