package org.digit.health.sync.web.models;

import org.digit.health.sync.helper.SyncUpDataListTestBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SyncUpDataListTest {

    @Test
    void shouldReturnTheSumOfAllTheItemsInTheSyncUpDataList() {
        SyncUpDataList syncUpDataList = SyncUpDataListTestBuilder
                .builder().withTwoHouseholdRegistrationAndDelivery().build();
        assertEquals(4, syncUpDataList.getTotalCount());
    }

}