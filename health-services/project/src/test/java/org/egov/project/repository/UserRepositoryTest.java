package org.egov.project.repository;

import org.egov.common.contract.request.User;
import org.egov.project.web.models.UserServiceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRepositoryTest {

    @InjectMocks
    private UserRepository userRepository;

    @Mock
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userRepository, "SEARCH_USER_URL", "user/_search");
        ReflectionTestUtils.setField(userRepository, "HOST", "http://localhost:8081/");

        restTemplate = new RestTemplate();
        userRepository = new UserRepository(restTemplate);
    }

    @Disabled
    @Test
    @DisplayName("should call user service and search by userIds")
    void shouldCallUserServiceAndSearchByUserIds() throws URISyntaxException {
        List<String> providedUserIds = Stream.of("userId1", "userId2").collect(Collectors.toList());
        String tenantId = "tenantId";
        List<User> userList = getUserList();

        when(restTemplate.postForObject(any(String.class), any(), eq(UserServiceResponse.class)))
                .thenReturn(UserServiceResponse.builder()
                        .users(userList)
                        .build());

        List<String> returnedUserIds  = userRepository.validatedUserIds(providedUserIds,tenantId);
        assertEquals(providedUserIds.size(), returnedUserIds.size());
    }

    private List<User> getUserList() {
        List<User> userList = new ArrayList<>();
        User user1 = new User();
        user1.setUuid("userId1");
        User user2 = new User();
        user2.setUuid("userId2");

        userList.add(user1);
        userList.add(user2);
        return userList;
    }


}