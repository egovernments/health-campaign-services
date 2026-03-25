package org.egov.fhirtransformer.mapping.fhirBuilder;

import org.egov.common.models.stock.*;
import org.egov.fhirtransformer.common.Constants;
import org.hl7.fhir.r5.model.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Date;

/**
 * Mapper utility for converting DIGIT Stock domain models
 * to FHIR resources and back.
 */
public class DIGITHCMStockMapper {

    /**
     * Creates a FHIR {@link SupplyDelivery} resource from a DIGIT {@link Stock}.
     * @param stock stock transaction data; must not be {@code null}
     * @return populated {@link SupplyDelivery} resource
     */
    public static SupplyDelivery buildSupplyDeliveryFromStock(Stock stock) {

        String facilityId = null;
        SupplyDelivery supplyDelivery = new SupplyDelivery();
        supplyDelivery.setId(stock.getId());
        Identifier identifier = new Identifier()
                .setSystem(Constants.IDENTIFIER_SYSTEM_WAYBILL)
                .setValue(stock.getWayBillNumber());
        supplyDelivery.addIdentifier(identifier);
        Long dateOfEntry = stock.getDateOfEntry();
        DateTimeType dateOfEntryDt = (dateOfEntry != null) ? new DateTimeType(new Date(dateOfEntry)) : null;
        supplyDelivery.setOccurrence(dateOfEntryDt);

        SupplyDelivery.SupplyDeliverySuppliedItemComponent suppliedItemComponent =
                new SupplyDelivery.SupplyDeliverySuppliedItemComponent();

        Quantity stockquantity = new Quantity()
                .setValue(stock.getQuantity());
        suppliedItemComponent.setQuantity(stockquantity);
        suppliedItemComponent.setItem(
                new Reference()
                        .setIdentifier(new Identifier()
                                .setSystem(Constants.PRODUCT_VARIANT_IDENTIFIER_SYSTEM)
                                .setValue(stock.getProductVariantId()))
        );

        //Set extension for Supply Delivery Condition
        suppliedItemComponent.addExtension(new Extension().setUrl(Constants.SD_CONDITION_URL)
                .setValue(new CodeableConcept().addCoding(
                        new Coding()
                                .setSystem(Constants.TRANSACTION_REASON_SYSTEM)
                                .setCode(String.valueOf(stock.getTransactionReason())))));
        supplyDelivery.addSuppliedItem(suppliedItemComponent);

        // Set extension for Supply Delivery Stage
        Extension stageExt = new Extension().setUrl(Constants.SD_STAGE_URL)
                .setValue(new CodeableConcept().addCoding(
                        new Coding()
                        .setSystem(Constants.TRANSACTION_TYPE_SYSTEM)
                        .setCode(String.valueOf(stock.getTransactionType()))));
        supplyDelivery.addExtension(stageExt);

        // Set extension for Event Location
        if (stock.getTransactionType().equals(TransactionType.RECEIVED)) {
            facilityId = stock.getReceiverId(); //change it to facilityID once added
        } else if (stock.getTransactionType().equals(TransactionType.DISPATCHED)) {
            facilityId = stock.getSenderId();
        }
        Extension eventLocationExt = new Extension().setUrl(Constants.EVENT_LOCATION_URL)
                .setValue(new Reference()
                        .setIdentifier( new Identifier()
                                .setSystem(Constants.FACILITY_ID_SYSTEM)
                                .setValue(facilityId)));
        supplyDelivery.addExtension(eventLocationExt);

        // Set extension for Supply Delivery Sender Location
        Extension senderLocationExt = new Extension()
                .setUrl(Constants.SD_SENDER_LOCATION_URL)
                .setValue(new Reference().setIdentifier(
                        new Identifier()
                                .setSystem(Constants.FACILITY_ID_SYSTEM)
                                .setValue(stock.getSenderId())));

        supplyDelivery.addExtension(senderLocationExt);

        supplyDelivery.setDestination(new Reference()
                        .setIdentifier( new Identifier()
                                .setSystem(Constants.FACILITY_ID_SYSTEM)
                                .setValue("F-2024-06-26-004987")));

        return supplyDelivery;
    }

    /**
     * Creates a FHIR {@link InventoryReport} resource from a {@link StockReconciliation}.
     * @param stockReconciliation stock reconciliation data; must not be {@code null}
     * @return populated {@link InventoryReport} resource
     */
    public static InventoryReport buildInventoryReportFromStockReconciliation(StockReconciliation stockReconciliation) {

        InventoryReport inventoryReport = new InventoryReport();

        inventoryReport.setId(stockReconciliation.getId());
        inventoryReport.setStatus(InventoryReport.InventoryReportStatus.ACTIVE);
        inventoryReport.setCountType(InventoryReport.InventoryCountType.SNAPSHOT);

        Long reportedDateEpoch = stockReconciliation.getDateOfReconciliation();
        Date reportedDate = new Date(reportedDateEpoch);
        inventoryReport.setReportedDateTimeElement(new DateTimeType(reportedDate));

        InventoryReport.InventoryReportInventoryListingComponent listing = new InventoryReport.InventoryReportInventoryListingComponent();
        listing.setCountingDateTime(reportedDate);

        Reference locationRef = new Reference()
                .setType(Constants.LOCATION)
                .setIdentifier(new Identifier()
                        .setSystem(Constants.FACILITY_ID_SYSTEM)
                        .setValue(stockReconciliation.getFacilityId()));

        listing.setLocation(locationRef);

        InventoryReport.InventoryReportInventoryListingItemComponent item= new InventoryReport.InventoryReportInventoryListingItemComponent();

        Quantity qty = new Quantity().setValue(stockReconciliation.getCalculatedCount());
        item.setQuantity(qty);

        Reference itemRef = new Reference().setType(Constants.INVENTORY_ITEM)
                .setIdentifier(new Identifier()
                        .setSystem(Constants.IDENTIFIER_SYSTEM_PV)
                        .setValue(stockReconciliation.getProductVariantId()));
        CodeableReference CodeableReferenceItemRef = new CodeableReference(itemRef);
        item.setItem(CodeableReferenceItemRef);

        listing.addItem(item);
        inventoryReport.addInventoryListing(listing);

        return inventoryReport;
    }

    /**
     * Converts a FHIR {@link SupplyDelivery} resource into a DIGIT {@link Stock}.
     *
     * @param supplyDelivery FHIR SupplyDelivery to convert; must not be {@code null}
     * @param tenantID
     * @return populated {@link Stock} object
     */
    public Stock buildStockFromSupplyDelivery(SupplyDelivery supplyDelivery, String tenantID) {
        // Implementation for reverse mapping if needed
        Stock stock = new Stock();
        stock.setTenantId(tenantID);

        //Defaulting the values for mandatory fields
        stock.setSenderType(SenderReceiverType.WAREHOUSE);
        stock.setReferenceIdType(ReferenceIdType.OTHER);
        stock.setReceiverType(SenderReceiverType.WAREHOUSE);

        stock.setId(supplyDelivery.getIdElement().getIdPart());
        for (Identifier identifier : supplyDelivery.getIdentifier()) {
            if (Constants.IDENTIFIER_SYSTEM_WAYBILL.equals(identifier.getSystem())) {
                stock.setWayBillNumber(identifier.getValue());
            }
        }

        if (supplyDelivery.hasOccurrenceDateTimeType()) {
            stock.setDateOfEntry(supplyDelivery.getOccurrenceDateTimeType().getValue().getTime());
        }

        for (SupplyDelivery.SupplyDeliverySuppliedItemComponent suppliedItem : supplyDelivery.getSuppliedItem()) {
            if (suppliedItem.hasItemReference() && suppliedItem.getItemReference().hasIdentifier()) {
                Identifier itemId = suppliedItem.getItemReference().getIdentifier();
                if (Constants.PRODUCT_VARIANT_IDENTIFIER_SYSTEM.equals(itemId.getSystem())) {
                    stock.setProductVariantId(itemId.getValue());
                }
            }

            // quantity → Stock.quantity
            Quantity qty = suppliedItem.getQuantity();
            if (qty != null && qty.hasValue()) {
                stock.setQuantity(qty.getValue().intValue());
            }

            // suppliedItem.extension where url is supplydelivery-condition → Stock.transactionReason
            for (Extension ext : suppliedItem.getExtension()) {
                if (Constants.SD_CONDITION_URL.equals(ext.getUrl())) {
                    CodeableConcept cc = (CodeableConcept) ext.getValue();
                    if (cc != null && cc.hasCoding()) {
                        if (Constants.TRANSACTION_REASON_SYSTEM.equals(cc.getCodingFirstRep().getSystem())) {
                            // transaction reason is a enum
                            stock.setTransactionReason(TransactionReason.fromValue(cc.getCodingFirstRep().getCode()));
                        }
                    }
                }
            }

            if (supplyDelivery.hasDestination() && supplyDelivery.getDestination().hasIdentifier()) {
                Identifier destId = supplyDelivery.getDestination().getIdentifier();
                if (Constants.FACILITY_ID_SYSTEM.equals(destId.getSystem())) {
                    stock.setReceiverId(destId.getValue());
                }
            }

            for (Extension ext : supplyDelivery.getExtension()) {
                switch (ext.getUrl()) {
                    case Constants.SD_STAGE_URL:
                        CodeableConcept ccStage = (CodeableConcept) ext.getValue();
                        if (ccStage != null && ccStage.hasCoding()) {
                            if (Constants.TRANSACTION_TYPE_SYSTEM.equals(ccStage.getCodingFirstRep().getSystem())) {
                                stock.setTransactionType(TransactionType.fromValue(ccStage.getCodingFirstRep().getCode()));
                            }
                        }
                        break;

                    case Constants.EVENT_LOCATION_URL:
                        Reference eventLoc = (Reference) ext.getValue();
                        if (eventLoc != null && eventLoc.hasIdentifier()) {
                            Identifier id = eventLoc.getIdentifier();
                            if (Constants.FACILITY_ID_SYSTEM.equals(id.getSystem())) {
                                stock.setReferenceId(id.getValue()); //change it facilityID once added
                            }
                        }
                        break;

                    case Constants.SD_SENDER_LOCATION_URL:
                        Reference senderLoc = (Reference) ext.getValue();
                        if (senderLoc != null && senderLoc.hasIdentifier()) {
                            Identifier id = senderLoc.getIdentifier();
                            if (Constants.FACILITY_ID_SYSTEM.equals(id.getSystem())) {
                                stock.setSenderId(id.getValue());
                            }
                        }
                        break;
                }
            }
        }
        return stock;
    }

    /**
     * Converts a FHIR {@link InventoryReport} resource into a {@link StockReconciliation}.
     *
     * @param inventoryReport FHIR InventoryReport to convert; must not be {@code null}
     * @param tenantID
     * @return populated {@link StockReconciliation} object
     */

    public StockReconciliation buildStockReconFromInventoryReport(InventoryReport inventoryReport, String tenantID) {

        StockReconciliation stockRecon = new StockReconciliation();
        //Defaulting the values for mandatory fields
        stockRecon.setTenantId(tenantID);

        stockRecon.setReferenceId(UUID.randomUUID().toString());
        stockRecon.setReferenceIdType(ReferenceIdType.OTHER.toString());

        String reportedDateTime = inventoryReport.getReportedDateTimeElement().getValueAsString();
        OffsetDateTime odt = OffsetDateTime.parse(reportedDateTime);
        stockRecon.setDateOfReconciliation((long) odt.toInstant().toEpochMilli());

        // Extract facility ID
        if (inventoryReport.getInventoryListingFirstRep() != null
                && inventoryReport.getInventoryListingFirstRep().getLocation() != null
                && inventoryReport.getInventoryListingFirstRep().getLocation().getIdentifier() != null) {
            stockRecon.setFacilityId(
                    inventoryReport.getInventoryListingFirstRep()
                            .getLocation()
                            .getIdentifier()
                            .getValue()
            );
        }

        // Extract Product Variant ID

        if (inventoryReport.getInventoryListingFirstRep() != null
                && inventoryReport.getInventoryListingFirstRep().getItemFirstRep() != null
                && inventoryReport.getInventoryListingFirstRep().getItemFirstRep().getItem() != null
                && inventoryReport.getInventoryListingFirstRep().getItemFirstRep().getItem().getReference() != null
                && inventoryReport.getInventoryListingFirstRep().getItemFirstRep().getItem().getReference().getIdentifier() != null) {

            stockRecon.setProductVariantId(inventoryReport.getInventoryListingFirstRep()
                    .getItemFirstRep()
                    .getItem()
                    .getReference()
                    .getIdentifier()
                    .getValue());
        }


        // this needs change
        stockRecon.setCalculatedCount(inventoryReport.getInventoryListingFirstRep().getItemFirstRep().getQuantity().getValue().intValue());
        stockRecon.setPhysicalCount(inventoryReport.getInventoryListingFirstRep().getItemFirstRep().getQuantity().getValue().intValue());
        return stockRecon;
    }
}
