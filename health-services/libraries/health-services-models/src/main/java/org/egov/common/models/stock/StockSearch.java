package org.egov.common.models.stock;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineSearchModel;
import org.egov.common.models.core.Exclude;
import org.egov.common.models.core.OrGroup;
import org.springframework.validation.annotation.Validated;

/**
 * StockSearch
 */
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockSearch extends EgovOfflineSearchModel {

    @JsonProperty("facilityId")
    @Size(min=2, max=64)
    private String facilityId = null;

    @JsonProperty("productVariantId")
    private List<String> productVariantId = null;

    @JsonProperty("referenceId")
    private String referenceId = null;

    @JsonProperty("wayBillNumber")
    @JsonAlias({"waybillNumber"})
    private List<String> wayBillNumber = null;

    @JsonProperty("referenceIdType")
    @Size(min=2, max=64)
    private String referenceIdType = null;

    @JsonProperty("transactionType")
    @Valid
    private TransactionType transactionType = null;

    @JsonProperty("transactionReason")
    @Valid
    private TransactionReason transactionReason = null;

    @JsonProperty("transactingPartyId")
    @Size(min=2, max=64)
    private String transactingPartyId = null;

    @JsonProperty("transactingPartyType")
    private String transactingPartyType = null;

    /**
     * senderId and receiverId allow searching stock records by the actual
     * sender/receiver columns in the database.
     *
     * For a facility (warehouse) that both dispatches and receives stock,
     * passing both senderId and receiverId with the same facility ID
     * triggers an OR condition — returning all records where the facility
     * is either the sender or the receiver in a single query.
     *
     * For a CDD (Community Drug Distributor / last-mile staff), who only
     * receives or returns stock and is never a sender, only receiverId
     * needs to be provided with the staff's user UUID.
     */
    @JsonProperty("senderId")
    @OrGroup("senderOrReceiver")
    @Size(min=2, max=64)
    private String senderId = null;

    @JsonProperty("receiverId")
    @OrGroup("senderOrReceiver")
    @Size(min=2, max=64)
    private String receiverId = null;

    @JsonProperty("lastSyncedTime")
    @Exclude
    private Long lastSyncedTime = null;

    @JsonProperty("campaignNumber")
    @Size(min = 2, max = 64)
    private String campaignNumber = null;
}

