package org.egov.project.repository;

import org.egov.common.contract.request.User;
import org.egov.project.web.models.UserServiceResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserRepositoryTest {
    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private UserRepository repository;

    @Test
    public void testSearchUserIds() {
        List<String> userIds = Arrays.asList("user1", "user2");
        String tenantId = "default";
        UserServiceResponse response = getUserServiceResponse();

        when(restTemplate.postForObject(anyString(), any(), eq(UserServiceResponse.class))).thenReturn(response);

        List<User> validatedUserIds = repository.searchByUserIds(userIds, tenantId);

        List<User> userList = getUserList();
        Assertions.assertEquals(userList, validatedUserIds);
    }

    private List<User> getUserList() {
        return Arrays.asList(
                User.builder().uuid("user1").build(),
                User.builder().uuid("user2").build()
        );
    }

    private UserServiceResponse getUserServiceResponse() {
        UserServiceResponse response = new UserServiceResponse();
        response.setUsers(getUserList());
        return response;
    }
}
