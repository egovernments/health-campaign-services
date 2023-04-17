package org.egov.individual.service;

import org.egov.common.models.individual.Address;
import org.egov.common.models.individual.AddressType;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.user.BloodGroup;
import org.egov.common.models.user.Gender;
import org.egov.common.models.user.UserRequest;
import org.egov.common.models.user.UserType;
import org.egov.individual.config.IndividualProperties;

import java.util.Random;
import java.util.UUID;

public class IndividualMapper {

    private static Random random = new Random();

    public static UserRequest toUserRequest(Individual individual, IndividualProperties properties) {

        Address permanentAddress = individual.getAddress().stream()
                .filter(address -> address.getType().equals(AddressType.PERMANENT))
                .findFirst()
                .orElse(Address.builder().build());

        Address correspondenceAddress = individual.getAddress().stream()
                .filter(address -> address.getType().equals(AddressType.CORRESPONDENCE))
                .findFirst()
                .orElse(Address.builder().build());
        return UserRequest.builder()
                .tenantId(individual.getTenantId())
                .userName(UUID.randomUUID().toString())
                .name(String.join(" ", individual.getName().getGivenName(),
                        individual.getName().getFamilyName()))
                .gender(Gender.valueOf(individual.getGender().name()).name())
                .mobileNumber(generateDummyMobileNumber(individual.getMobileNumber()))
                .emailId(individual.getEmail())
                .aadhaarNumber(individual.getIdentifiers().stream()
                        .filter(identifier -> identifier.getIdentifierType()
                                .equals("AADHAAR"))
                        .findFirst().orElse(Identifier
                                .builder()
                                .build()).getIdentifierId())
                .type(UserType.valueOf(properties.getUserServiceUserType()))
                .accountLocked(properties.isUserServiceAccountLocked())
                .active(true)
                .dob(individual.getDateOfBirth())
                .altContactNumber(individual.getAltContactNumber())
                .fatherOrHusbandName(individual.getFatherName() != null
                        ? individual.getFatherName()
                        : individual.getHusbandName())
                .bloodGroup(BloodGroup.valueOf(individual.getBloodGroup().name()).name())
                .pan(individual.getIdentifiers().stream()
                        .filter(identifier -> identifier.getIdentifierType()
                                .equals("PAN"))
                        .findFirst().orElse(Identifier
                                .builder()
                                .build()).getIdentifierId())
                .permanentAddress(permanentAddress.getAddressLine1())
                .permanentCity(permanentAddress.getCity())
                .permanentPinCode(permanentAddress.getPincode())
                .correspondenceAddress(correspondenceAddress.getAddressLine1())
                .correspondenceCity(correspondenceAddress.getCity())
                .correspondencePinCode(correspondenceAddress.getPincode())
                .photo(individual.getPhoto())
                .build();
    }

    /**
     * Generates a dummy 10 digit mobile number not starting with 0, if the input mobile number is null.
     * If the input mobile number is not null, it returns the same input number.
     *
     * @param mobileNumber the input mobile number to check
     * @return a dummy 10 digit mobile number if the input is null, or the input number if it's not null
     */
    private static String generateDummyMobileNumber(String mobileNumber) {
        if (mobileNumber == null) {
            int number = random.nextInt(900000000) + 100000000; // generate 9 digit number
            return "1" + number; // prepend 1 to avoid starting with 0
        } else {
            return mobileNumber;
        }
    }
}
