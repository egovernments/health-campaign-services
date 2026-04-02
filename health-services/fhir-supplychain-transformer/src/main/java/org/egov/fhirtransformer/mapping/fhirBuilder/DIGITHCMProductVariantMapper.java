package org.egov.fhirtransformer.mapping.fhirBuilder;

import org.egov.common.models.product.ProductVariant;
import org.egov.fhirtransformer.common.Constants;
import org.hl7.fhir.r5.model.*;
import java.util.Date;

/**
 * Creates a FHIR {@link InventoryItem} resource from a {@link ProductVariant}.
 */

public class DIGITHCMProductVariantMapper {

    /**
     * Builds a FHIR {@link InventoryItem} resource from the given {@link ProductVariant}.
     * This method transforms product variant domain data into a standardized FHIR InventoryItem
     * @param productVariant the source product variant containing inventory details
     * @return a populated {@link InventoryItem} FHIR resource
     */
    public static InventoryItem buildInventoryFromProductVariant(ProductVariant productVariant) {

        InventoryItem inventoryItem = new InventoryItem();
        Long lastModifiedMillis = productVariant.getAuditDetails().getLastModifiedTime();
        Date lastModified = (lastModifiedMillis != null) ? new Date(lastModifiedMillis) : null;

        Long expiryDateMillis = productVariant.getExpiryDate();
        Date expiryDate = (expiryDateMillis != null) ? new Date(expiryDateMillis) : null;

        inventoryItem.setId(productVariant.getId());
        inventoryItem.setStatus(InventoryItem.InventoryItemStatusCodes.ACTIVE);

        // Setting meta information for the Location resource DIGIT HCM Facility profile
        inventoryItem.setMeta(new Meta()
                .setLastUpdated(lastModified)
                .addProfile(Constants.PROFILE_DIGIT_HCM_PV));

        // Adding identifier for facility ID
        Identifier identifier = new Identifier()
                .setSystem(Constants.IDENTIFIER_SYSTEM_PV)
                .setValue(productVariant.getId());
        Identifier SKUidentifier = new Identifier()
                .setSystem(Constants.IDENTIFIER_SYSTEM_SKUPV)
                .setValue(productVariant.getSku());
        Identifier productidentifier = new Identifier()
                .setSystem(Constants.IDENTIFIER_SYSTEM_PRDCT)
                .setValue(productVariant.getProductId());

        inventoryItem.addIdentifier(identifier);
        inventoryItem.addIdentifier(SKUidentifier);
        inventoryItem.addIdentifier(productidentifier);

        // Adding Category
        inventoryItem.addCategory(new CodeableConcept().addCoding(
                new Coding()
                        .setSystem(Constants.CATEGORY_SYSTEM_PV)
                        .setCode(productVariant.getProduct().getType())
                        .setDisplay(productVariant.getProduct().getType())));

        // Adding baseUnit
        if (productVariant.getBaseUnit() != null) {
            inventoryItem.setBaseUnit(new CodeableConcept().addCoding(
                    new Coding()
                            .setSystem(Constants.UOM_SYSTEM)
                            .setCode(productVariant.getBaseUnit())
                            .setDisplay(productVariant.getBaseUnit())
            ));
        }

        // Adding NetContent
        if (productVariant.getNetContent() != null) {
            inventoryItem.setNetContent(new Quantity(productVariant.getNetContent().longValue()));
        }

        // Adding Name Type
        InventoryItem.InventoryItemNameComponent nameComponent = new InventoryItem.InventoryItemNameComponent()
                .setName(productVariant.getVariation())
                .setLanguage(Enumerations.CommonLanguages.ENUS)
                .setNameType(new Coding()
                                .setSystem(Constants.NAMETYPE_SYSTEM_PV)
                                .setCode(Constants.TRADENAME_PV));
        inventoryItem.addName(nameComponent);

        InventoryItem.InventoryItemNameComponent productnameComponent = new InventoryItem.InventoryItemNameComponent()
                .setName(productVariant.getProduct().getName())
                .setLanguage(Enumerations.CommonLanguages.ENUS)
                .setNameType(new Coding()
                        .setSystem(Constants.NAMETYPE_SYSTEM_PV)
                        .setCode(Constants.COMMONNAME_PV));
        inventoryItem.addName(productnameComponent);

        // Adding Manufacturer as Responsible Organization
        InventoryItem.InventoryItemResponsibleOrganizationComponent responsibleOrgComponent = new InventoryItem.InventoryItemResponsibleOrganizationComponent()
                .setOrganization(new Reference().setDisplay(productVariant.getProductId()))
                .setRole(new CodeableConcept().addCoding(
                        new Coding()
                        .setSystem(Constants.RESPORG_SYSTEM_PV)
                        .setCode(Constants.MANUFACTURER_PV)
                        .setDisplay(Constants.MANUFACTURER_PV)));

        inventoryItem.addResponsibleOrganization(responsibleOrgComponent);

        //Adding Instance Information
        InventoryItem.InventoryItemInstanceComponent instanceComponent = new InventoryItem.InventoryItemInstanceComponent()
                .addIdentifier(new Identifier()
                .setSystem(Constants.GTIN_PV)
                .setValue(productVariant.getGtin()))
                .setLotNumber(productVariant.getBatchNumber())
                .setExpiry(expiryDate);

        inventoryItem.setInstance(instanceComponent);
        return inventoryItem;
    }

    /**
     * Converts a FHIR {@link InventoryItem} resource into a DIGIT {@link ProductVariant}.
     *
     * @param inventoryItem FHIR InventoryItem to convert; must not be {@code null}
     * @param tenantID
     * @return populated {@link ProductVariant} object
     */
    public ProductVariant buildProductVariantFromInventoryItem(InventoryItem inventoryItem, String tenantID) {
        ProductVariant productVariant = new ProductVariant();
        //Defaulting the values for mandatory fields
        productVariant.setTenantId(tenantID);
        for (Identifier identifier : inventoryItem.getIdentifier()) {
            String system = identifier.getSystem();

            if (system.equals(Constants.IDENTIFIER_SYSTEM_PRDCT)) {
                productVariant.setProductId(identifier.getValue());

            } else if (system.equals(Constants.IDENTIFIER_SYSTEM_SKUPV)) {
                productVariant.setSku(identifier.getValue());
            }
            else if (system.equals(Constants.IDENTIFIER_SYSTEM_PV)) {
                productVariant.setId(identifier.getValue());
                productVariant.setVariation(inventoryItem.getNameFirstRep().getName());
            }
        }
        productVariant.setRowVersion(Constants.ROW_VERSION);

        return productVariant;

    }
}
