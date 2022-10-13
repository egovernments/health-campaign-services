package org.digit.health.sync.helper;

import org.digit.health.sync.web.models.CampaignData;
import org.digit.health.sync.web.models.Delivery;
import org.digit.health.sync.web.models.HouseholdRegistration;
import org.digit.health.sync.web.models.SyncUpData;
import org.digit.health.sync.web.models.SyncUpDataList;

import java.util.ArrayList;
import java.util.List;

public class SyncUpDataListTestBuilder {

    private SyncUpDataList.SyncUpDataListBuilder builder;

    public static SyncUpDataListTestBuilder builder() {
        return new SyncUpDataListTestBuilder();
    }

    public SyncUpDataListTestBuilder() {
        this.builder = SyncUpDataList.builder();
    }

    public SyncUpDataList build() {
        return builder.build();
    }

    public SyncUpDataListTestBuilder withOneHouseholdRegistrationAndDelivery() {
        List<CampaignData> householdRegistrationList = new ArrayList<>();
        householdRegistrationList.add(getHouseholdRegistration());
        SyncUpData householdSyncUpData = SyncUpData.builder()
                .items(householdRegistrationList)
                .build();
        List<CampaignData> deliveryList = new ArrayList<>();
        deliveryList.add(getDelivery());
        SyncUpData deliverySyncUpData = SyncUpData.builder()
                .items(deliveryList)
                .build();
        List<SyncUpData> syncUpData = new ArrayList<>();
        syncUpData.add(householdSyncUpData);
        syncUpData.add(deliverySyncUpData);
        builder.syncUpData(syncUpData);
        return this;
    }

    public SyncUpDataListTestBuilder withOneDelivery() {
        List<CampaignData> deliveryList = new ArrayList<>();
        deliveryList.add(getDelivery());
        SyncUpData deliverySyncUpData = SyncUpData.builder()
                .items(deliveryList)
                .build();
        List<SyncUpData> syncUpData = new ArrayList<>();
        syncUpData.add(deliverySyncUpData);
        builder.syncUpData(syncUpData);
        return this;
    }

    public SyncUpDataListTestBuilder withOneHouseholdRegistration() {
        List<CampaignData> householdRegistrationList = new ArrayList<>();
        householdRegistrationList.add(getHouseholdRegistration());
        SyncUpData householdSyncUpData = SyncUpData.builder()
                .items(householdRegistrationList)
                .build();
        List<SyncUpData> syncUpData = new ArrayList<>();
        syncUpData.add(householdSyncUpData);
        builder.syncUpData(syncUpData);
        return this;
    }

    public SyncUpDataListTestBuilder withTwoHouseholdRegistrationAndDelivery() {
        List<CampaignData> householdRegistrationList = new ArrayList<>();
        householdRegistrationList.add(getHouseholdRegistration());
        householdRegistrationList.add(getHouseholdRegistration("some-different-id"));
        SyncUpData householdSyncUpData = SyncUpData.builder()
                .items(householdRegistrationList)
                .build();
        List<CampaignData> deliveryList = new ArrayList<>();
        deliveryList.add(getDelivery());
        deliveryList.add(getDelivery("some-different-id"));
        SyncUpData deliverySyncUpData = SyncUpData.builder()
                .items(deliveryList)
                .build();
        List<SyncUpData> syncUpData = new ArrayList<>();
        syncUpData.add(householdSyncUpData);
        syncUpData.add(deliverySyncUpData);
        builder.syncUpData(syncUpData);
        return this;
    }

    public static HouseholdRegistration getHouseholdRegistration() {
       return HouseholdRegistrationTestBuilder.builder()
                .withDummyClientReferenceId().build();
    }

    public static HouseholdRegistration getHouseholdRegistration(String id) {
        return HouseholdRegistrationTestBuilder.builder()
                .withDummyClientReferenceId(id).build();
    }

    public static Delivery getDelivery() {
        return DeliveryTestBuilder.builder()
                .withDummyClientReferenceId()
                .withRegistrationClientReferenceId().build();
    }

    public static Delivery getDelivery(String id) {
        return DeliveryTestBuilder.builder()
                .withDummyClientReferenceId()
                .withRegistrationClientReferenceId(id).build();
    }
}
