package org.egov.transformer.models.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import digit.models.coremodels.user.enums.BloodGroup;
import digit.models.coremodels.user.enums.Gender;
import digit.models.coremodels.user.enums.GuardianRelation;
import digit.models.coremodels.user.enums.UserType;
import digit.models.coremodels.user.OtpValidationRequest;
import org.apache.commons.lang3.time.DateUtils;
import digit.models.coremodels.user.Role;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.Email;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

public class User {
    private Long id;
    private String uuid;
    private @Pattern(
            regexp = "^[a-zA-Z. ]*$"
    ) @Size(
            max = 50
    ) String tenantId;
    private String username;
    private String title;
    private String password;
    private String salutation;
    private @Pattern(
            regexp = "^[^\\\\$\\\"<>?\\\\\\\\~`!@#$%^()+={}\\\\[\\\\]*,:;“”‘’]*$"
    ) String guardian;
    private GuardianRelation guardianRelation;
    private @Pattern(
            regexp = "^[^\\\\$\\\"<>?\\\\\\\\~`!@#$%^()+={}\\\\[\\\\]*,:;“”‘’]*$"
    ) @Size(
            max = 50
    ) String name;
    private Gender gender;
    private String mobileNumber;
    private @Email String emailId;
    private String altContactNumber;
    private String pan;
    private String aadhaarNumber;
    private Address permanentAddress;
    private Address correspondenceAddress;
    private Set<Address> addresses;
    private Boolean active;
    private Set<Role> roles;
    private Date dob;
    private Date passwordExpiryDate;
    private String locale = "en_IN";
    private UserType type;
    private BloodGroup bloodGroup;
    private String identificationMark;
    private String signature;
    private String photo;
    private Boolean accountLocked;
    private Long accountLockedDate;
    private Date lastModifiedDate;
    private Date createdDate;
    private String otpReference;
    private Long createdBy;
    private Long lastModifiedBy;
    private Long loggedInUserId;
    private boolean otpValidationMandatory;
    private boolean mobileValidationMandatory = true;
    private String alternateMobileNumber;

    public User addAddressItem(Address addressItem) {
        if (this.addresses == null) {
            this.addresses = new HashSet();
        }

        this.addresses.add(addressItem);
        return this;
    }

    public User addRolesItem(Role roleItem) {
        if (this.roles == null) {
            this.roles = new HashSet();
        }

        this.roles.add(roleItem);
        return this;
    }

    public void validateNewUser() {
        this.validateNewUser(true);
    }

    public void validateNewUser(boolean createUserValidateName) {
        if (this.isUsernameAbsent() || createUserValidateName && this.isNameAbsent() || this.isMobileNumberAbsent() || this.isActiveIndicatorAbsent() || this.isTypeAbsent() || this.isPermanentAddressInvalid() || this.isCorrespondenceAddressInvalid() || this.isRolesAbsent() || this.isOtpReferenceAbsent() || this.isTenantIdAbsent()) {
            throw new InvalidUserCreateException(this);
        }
    }

    public void validateUserModification() {
        if (this.isPermanentAddressInvalid() || this.isCorrespondenceAddressInvalid() || this.isTenantIdAbsent()) {
            throw new InvalidUserUpdateException(this);
        }
    }

    @JsonIgnore
    public boolean isCorrespondenceAddressInvalid() {
        return this.correspondenceAddress != null && this.correspondenceAddress.isInvalid();
    }

    @JsonIgnore
    public boolean isPermanentAddressInvalid() {
        return this.permanentAddress != null && this.permanentAddress.isInvalid();
    }

    @JsonIgnore
    public boolean isOtpReferenceAbsent() {
        return this.otpValidationMandatory && ObjectUtils.isEmpty(this.otpReference);
    }

    @JsonIgnore
    public boolean isTypeAbsent() {
        return ObjectUtils.isEmpty(this.type);
    }

    @JsonIgnore
    public boolean isActiveIndicatorAbsent() {
        return ObjectUtils.isEmpty(this.active);
    }

    @JsonIgnore
    public boolean isMobileNumberAbsent() {
        return this.mobileValidationMandatory && ObjectUtils.isEmpty(this.mobileNumber);
    }

    @JsonIgnore
    public boolean isNameAbsent() {
        return ObjectUtils.isEmpty(this.name);
    }

    @JsonIgnore
    public boolean isUsernameAbsent() {
        return ObjectUtils.isEmpty(this.username);
    }

    @JsonIgnore
    public boolean isTenantIdAbsent() {
        return ObjectUtils.isEmpty(this.tenantId);
    }

    @JsonIgnore
    public boolean isPasswordAbsent() {
        return ObjectUtils.isEmpty(this.password);
    }

    @JsonIgnore
    public boolean isRolesAbsent() {
        return CollectionUtils.isEmpty(this.roles) || this.roles.stream().anyMatch((r) -> {
            return ObjectUtils.isEmpty(r.getCode());
        });
    }

    @JsonIgnore
    public boolean isIdAbsent() {
        return this.id == null;
    }

    public void nullifySensitiveFields() {
        this.username = null;
        this.type = null;
        this.mobileNumber = null;
        this.password = null;
        this.passwordExpiryDate = null;
        this.roles = null;
        this.accountLocked = null;
        this.accountLockedDate = null;
    }

    @JsonIgnore
    public boolean isLoggedInUserDifferentFromUpdatedUser() {
        return !this.id.equals(this.loggedInUserId);
    }

    public void setRoleToCitizen() {
        this.type = UserType.CITIZEN;
        this.roles = Collections.singleton(Role.getCitizenRole());
    }

    public void updatePassword(String newPassword) {
        this.password = newPassword;
    }

    @JsonIgnore
    public OtpValidationRequest getOtpValidationRequest() {
        return OtpValidationRequest.builder().mobileNumber(this.mobileNumber).tenantId(this.tenantId).otpReference(this.otpReference).build();
    }

    @JsonIgnore
    public List<Address> getPermanentAndCorrespondenceAddresses() {
        ArrayList<Address> addresses = new ArrayList();
        if (this.correspondenceAddress != null && this.correspondenceAddress.isNotEmpty()) {
            addresses.add(this.correspondenceAddress);
        }

        if (this.permanentAddress != null && this.permanentAddress.isNotEmpty()) {
            addresses.add(this.permanentAddress);
        }

        return addresses;
    }

    public void setDefaultPasswordExpiry(int expiryInDays) {
        if (this.passwordExpiryDate == null) {
            this.passwordExpiryDate = DateUtils.addDays(new Date(), expiryInDays);
        }

    }

    public void setActive(boolean isActive) {
        this.active = isActive;
    }

    public static UserBuilder builder() {
        return new UserBuilder();
    }

    public UserBuilder toBuilder() {
        return (new UserBuilder()).id(this.id).uuid(this.uuid).tenantId(this.tenantId).username(this.username).title(this.title).password(this.password).salutation(this.salutation).guardian(this.guardian).guardianRelation(this.guardianRelation).name(this.name).gender(this.gender).mobileNumber(this.mobileNumber).emailId(this.emailId).altContactNumber(this.altContactNumber).pan(this.pan).aadhaarNumber(this.aadhaarNumber).permanentAddress(this.permanentAddress).correspondenceAddress(this.correspondenceAddress).addresses(this.addresses).active(this.active).roles(this.roles).dob(this.dob).passwordExpiryDate(this.passwordExpiryDate).locale(this.locale).type(this.type).bloodGroup(this.bloodGroup).identificationMark(this.identificationMark).signature(this.signature).photo(this.photo).accountLocked(this.accountLocked).accountLockedDate(this.accountLockedDate).lastModifiedDate(this.lastModifiedDate).createdDate(this.createdDate).otpReference(this.otpReference).createdBy(this.createdBy).lastModifiedBy(this.lastModifiedBy).loggedInUserId(this.loggedInUserId).otpValidationMandatory(this.otpValidationMandatory).mobileValidationMandatory(this.mobileValidationMandatory).alternateMobileNumber(this.alternateMobileNumber);
    }

    public User(Long id, String uuid, String tenantId, String username, String title, String password, String salutation, String guardian, GuardianRelation guardianRelation, String name, Gender gender, String mobileNumber, String emailId, String altContactNumber, String pan, String aadhaarNumber, Address permanentAddress, Address correspondenceAddress, Set<Address> addresses, Boolean active, Set<Role> roles, Date dob, Date passwordExpiryDate, String locale, UserType type, BloodGroup bloodGroup, String identificationMark, String signature, String photo, Boolean accountLocked, Long accountLockedDate, Date lastModifiedDate, Date createdDate, String otpReference, Long createdBy, Long lastModifiedBy, Long loggedInUserId, boolean otpValidationMandatory, boolean mobileValidationMandatory, String alternateMobileNumber) {
        this.id = id;
        this.uuid = uuid;
        this.tenantId = tenantId;
        this.username = username;
        this.title = title;
        this.password = password;
        this.salutation = salutation;
        this.guardian = guardian;
        this.guardianRelation = guardianRelation;
        this.name = name;
        this.gender = gender;
        this.mobileNumber = mobileNumber;
        this.emailId = emailId;
        this.altContactNumber = altContactNumber;
        this.pan = pan;
        this.aadhaarNumber = aadhaarNumber;
        this.permanentAddress = permanentAddress;
        this.correspondenceAddress = correspondenceAddress;
        this.addresses = addresses;
        this.active = active;
        this.roles = roles;
        this.dob = dob;
        this.passwordExpiryDate = passwordExpiryDate;
        this.locale = locale;
        this.type = type;
        this.bloodGroup = bloodGroup;
        this.identificationMark = identificationMark;
        this.signature = signature;
        this.photo = photo;
        this.accountLocked = accountLocked;
        this.accountLockedDate = accountLockedDate;
        this.lastModifiedDate = lastModifiedDate;
        this.createdDate = createdDate;
        this.otpReference = otpReference;
        this.createdBy = createdBy;
        this.lastModifiedBy = lastModifiedBy;
        this.loggedInUserId = loggedInUserId;
        this.otpValidationMandatory = otpValidationMandatory;
        this.mobileValidationMandatory = mobileValidationMandatory;
        this.alternateMobileNumber = alternateMobileNumber;
    }

    public Long getId() {
        return this.id;
    }

    public String getUuid() {
        return this.uuid;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public String getUsername() {
        return this.username;
    }

    public String getTitle() {
        return this.title;
    }

    public String getPassword() {
        return this.password;
    }

    public String getSalutation() {
        return this.salutation;
    }

    public String getGuardian() {
        return this.guardian;
    }

    public GuardianRelation getGuardianRelation() {
        return this.guardianRelation;
    }

    public String getName() {
        return this.name;
    }

    public Gender getGender() {
        return this.gender;
    }

    public String getMobileNumber() {
        return this.mobileNumber;
    }

    public String getEmailId() {
        return this.emailId;
    }

    public String getAltContactNumber() {
        return this.altContactNumber;
    }

    public String getPan() {
        return this.pan;
    }

    public String getAadhaarNumber() {
        return this.aadhaarNumber;
    }

    public Address getPermanentAddress() {
        return this.permanentAddress;
    }

    public Address getCorrespondenceAddress() {
        return this.correspondenceAddress;
    }

    public Set<Address> getAddresses() {
        return this.addresses;
    }

    public Boolean getActive() {
        return this.active;
    }

    public Set<Role> getRoles() {
        return this.roles;
    }

    public Date getDob() {
        return this.dob;
    }

    public Date getPasswordExpiryDate() {
        return this.passwordExpiryDate;
    }

    public String getLocale() {
        return this.locale;
    }

    public UserType getType() {
        return this.type;
    }

    public BloodGroup getBloodGroup() {
        return this.bloodGroup;
    }

    public String getIdentificationMark() {
        return this.identificationMark;
    }

    public String getSignature() {
        return this.signature;
    }

    public String getPhoto() {
        return this.photo;
    }

    public Boolean getAccountLocked() {
        return this.accountLocked;
    }

    public Long getAccountLockedDate() {
        return this.accountLockedDate;
    }

    public Date getLastModifiedDate() {
        return this.lastModifiedDate;
    }

    public Date getCreatedDate() {
        return this.createdDate;
    }

    public String getOtpReference() {
        return this.otpReference;
    }

    public Long getCreatedBy() {
        return this.createdBy;
    }

    public Long getLastModifiedBy() {
        return this.lastModifiedBy;
    }

    public Long getLoggedInUserId() {
        return this.loggedInUserId;
    }

    public boolean isOtpValidationMandatory() {
        return this.otpValidationMandatory;
    }

    public boolean isMobileValidationMandatory() {
        return this.mobileValidationMandatory;
    }

    public String getAlternateMobileNumber() {
        return this.alternateMobileNumber;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setSalutation(String salutation) {
        this.salutation = salutation;
    }

    public void setGuardian(String guardian) {
        this.guardian = guardian;
    }

    public void setGuardianRelation(GuardianRelation guardianRelation) {
        this.guardianRelation = guardianRelation;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public void setEmailId(String emailId) {
        this.emailId = emailId;
    }

    public void setAltContactNumber(String altContactNumber) {
        this.altContactNumber = altContactNumber;
    }

    public void setPan(String pan) {
        this.pan = pan;
    }

    public void setAadhaarNumber(String aadhaarNumber) {
        this.aadhaarNumber = aadhaarNumber;
    }

    public void setPermanentAddress(Address permanentAddress) {
        this.permanentAddress = permanentAddress;
    }

    public void setCorrespondenceAddress(Address correspondenceAddress) {
        this.correspondenceAddress = correspondenceAddress;
    }

    public void setAddresses(Set<Address> addresses) {
        this.addresses = addresses;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public void setDob(Date dob) {
        this.dob = dob;
    }

    public void setPasswordExpiryDate(Date passwordExpiryDate) {
        this.passwordExpiryDate = passwordExpiryDate;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public void setType(UserType type) {
        this.type = type;
    }

    public void setBloodGroup(BloodGroup bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public void setIdentificationMark(String identificationMark) {
        this.identificationMark = identificationMark;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public void setAccountLocked(Boolean accountLocked) {
        this.accountLocked = accountLocked;
    }

    public void setAccountLockedDate(Long accountLockedDate) {
        this.accountLockedDate = accountLockedDate;
    }

    public void setLastModifiedDate(Date lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public void setOtpReference(String otpReference) {
        this.otpReference = otpReference;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public void setLastModifiedBy(Long lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public void setLoggedInUserId(Long loggedInUserId) {
        this.loggedInUserId = loggedInUserId;
    }

    public void setOtpValidationMandatory(boolean otpValidationMandatory) {
        this.otpValidationMandatory = otpValidationMandatory;
    }

    public void setMobileValidationMandatory(boolean mobileValidationMandatory) {
        this.mobileValidationMandatory = mobileValidationMandatory;
    }

    public void setAlternateMobileNumber(String alternateMobileNumber) {
        this.alternateMobileNumber = alternateMobileNumber;
    }

    public String toString() {
        return "User(id=" + this.getId() + ", uuid=" + this.getUuid() + ", tenantId=" + this.getTenantId() + ", username=" + this.getUsername() + ", title=" + this.getTitle() + ", password=" + this.getPassword() + ", salutation=" + this.getSalutation() + ", guardian=" + this.getGuardian() + ", guardianRelation=" + this.getGuardianRelation() + ", name=" + this.getName() + ", gender=" + this.getGender() + ", mobileNumber=" + this.getMobileNumber() + ", emailId=" + this.getEmailId() + ", altContactNumber=" + this.getAltContactNumber() + ", pan=" + this.getPan() + ", aadhaarNumber=" + this.getAadhaarNumber() + ", permanentAddress=" + this.getPermanentAddress() + ", correspondenceAddress=" + this.getCorrespondenceAddress() + ", addresses=" + this.getAddresses() + ", active=" + this.getActive() + ", roles=" + this.getRoles() + ", dob=" + this.getDob() + ", passwordExpiryDate=" + this.getPasswordExpiryDate() + ", locale=" + this.getLocale() + ", type=" + this.getType() + ", bloodGroup=" + this.getBloodGroup() + ", identificationMark=" + this.getIdentificationMark() + ", signature=" + this.getSignature() + ", photo=" + this.getPhoto() + ", accountLocked=" + this.getAccountLocked() + ", accountLockedDate=" + this.getAccountLockedDate() + ", lastModifiedDate=" + this.getLastModifiedDate() + ", createdDate=" + this.getCreatedDate() + ", otpReference=" + this.getOtpReference() + ", createdBy=" + this.getCreatedBy() + ", lastModifiedBy=" + this.getLastModifiedBy() + ", loggedInUserId=" + this.getLoggedInUserId() + ", otpValidationMandatory=" + this.isOtpValidationMandatory() + ", mobileValidationMandatory=" + this.isMobileValidationMandatory() + ", alternateMobileNumber=" + this.getAlternateMobileNumber() + ")";
    }

    public static class UserBuilder {
        private Long id;
        private String uuid;
        private String tenantId;
        private String username;
        private String title;
        private String password;
        private String salutation;
        private String guardian;
        private GuardianRelation guardianRelation;
        private String name;
        private Gender gender;
        private String mobileNumber;
        private String emailId;
        private String altContactNumber;
        private String pan;
        private String aadhaarNumber;
        private Address permanentAddress;
        private Address correspondenceAddress;
        private Set<Address> addresses;
        private Boolean active;
        private Set<Role> roles;
        private Date dob;
        private Date passwordExpiryDate;
        private String locale;
        private UserType type;
        private BloodGroup bloodGroup;
        private String identificationMark;
        private String signature;
        private String photo;
        private Boolean accountLocked;
        private Long accountLockedDate;
        private Date lastModifiedDate;
        private Date createdDate;
        private String otpReference;
        private Long createdBy;
        private Long lastModifiedBy;
        private Long loggedInUserId;
        private boolean otpValidationMandatory;
        private boolean mobileValidationMandatory;
        private String alternateMobileNumber;

        UserBuilder() {
        }

        public UserBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public UserBuilder uuid(String uuid) {
            this.uuid = uuid;
            return this;
        }

        public UserBuilder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public UserBuilder username(String username) {
            this.username = username;
            return this;
        }

        public UserBuilder title(String title) {
            this.title = title;
            return this;
        }

        public UserBuilder password(String password) {
            this.password = password;
            return this;
        }

        public UserBuilder salutation(String salutation) {
            this.salutation = salutation;
            return this;
        }

        public UserBuilder guardian(String guardian) {
            this.guardian = guardian;
            return this;
        }

        public UserBuilder guardianRelation(GuardianRelation guardianRelation) {
            this.guardianRelation = guardianRelation;
            return this;
        }

        public UserBuilder name(String name) {
            this.name = name;
            return this;
        }

        public UserBuilder gender(Gender gender) {
            this.gender = gender;
            return this;
        }

        public UserBuilder mobileNumber(String mobileNumber) {
            this.mobileNumber = mobileNumber;
            return this;
        }

        public UserBuilder emailId(String emailId) {
            this.emailId = emailId;
            return this;
        }

        public UserBuilder altContactNumber(String altContactNumber) {
            this.altContactNumber = altContactNumber;
            return this;
        }

        public UserBuilder pan(String pan) {
            this.pan = pan;
            return this;
        }

        public UserBuilder aadhaarNumber(String aadhaarNumber) {
            this.aadhaarNumber = aadhaarNumber;
            return this;
        }

        public UserBuilder permanentAddress(Address permanentAddress) {
            this.permanentAddress = permanentAddress;
            return this;
        }

        public UserBuilder correspondenceAddress(Address correspondenceAddress) {
            this.correspondenceAddress = correspondenceAddress;
            return this;
        }

        public UserBuilder addresses(Set<Address> addresses) {
            this.addresses = addresses;
            return this;
        }

        public UserBuilder active(Boolean active) {
            this.active = active;
            return this;
        }

        public UserBuilder roles(Set<Role> roles) {
            this.roles = roles;
            return this;
        }

        public UserBuilder dob(Date dob) {
            this.dob = dob;
            return this;
        }

        public UserBuilder passwordExpiryDate(Date passwordExpiryDate) {
            this.passwordExpiryDate = passwordExpiryDate;
            return this;
        }

        public UserBuilder locale(String locale) {
            this.locale = locale;
            return this;
        }

        public UserBuilder type(UserType type) {
            this.type = type;
            return this;
        }

        public UserBuilder bloodGroup(BloodGroup bloodGroup) {
            this.bloodGroup = bloodGroup;
            return this;
        }

        public UserBuilder identificationMark(String identificationMark) {
            this.identificationMark = identificationMark;
            return this;
        }

        public UserBuilder signature(String signature) {
            this.signature = signature;
            return this;
        }

        public UserBuilder photo(String photo) {
            this.photo = photo;
            return this;
        }

        public UserBuilder accountLocked(Boolean accountLocked) {
            this.accountLocked = accountLocked;
            return this;
        }

        public UserBuilder accountLockedDate(Long accountLockedDate) {
            this.accountLockedDate = accountLockedDate;
            return this;
        }

        public UserBuilder lastModifiedDate(Date lastModifiedDate) {
            this.lastModifiedDate = lastModifiedDate;
            return this;
        }

        public UserBuilder createdDate(Date createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public UserBuilder otpReference(String otpReference) {
            this.otpReference = otpReference;
            return this;
        }

        public UserBuilder createdBy(Long createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public UserBuilder lastModifiedBy(Long lastModifiedBy) {
            this.lastModifiedBy = lastModifiedBy;
            return this;
        }

        public UserBuilder loggedInUserId(Long loggedInUserId) {
            this.loggedInUserId = loggedInUserId;
            return this;
        }

        public UserBuilder otpValidationMandatory(boolean otpValidationMandatory) {
            this.otpValidationMandatory = otpValidationMandatory;
            return this;
        }

        public UserBuilder mobileValidationMandatory(boolean mobileValidationMandatory) {
            this.mobileValidationMandatory = mobileValidationMandatory;
            return this;
        }

        public UserBuilder alternateMobileNumber(String alternateMobileNumber) {
            this.alternateMobileNumber = alternateMobileNumber;
            return this;
        }

        public User build() {
            return new User(this.id, this.uuid, this.tenantId, this.username, this.title, this.password, this.salutation, this.guardian, this.guardianRelation, this.name, this.gender, this.mobileNumber, this.emailId, this.altContactNumber, this.pan, this.aadhaarNumber, this.permanentAddress, this.correspondenceAddress, this.addresses, this.active, this.roles, this.dob, this.passwordExpiryDate, this.locale, this.type, this.bloodGroup, this.identificationMark, this.signature, this.photo, this.accountLocked, this.accountLockedDate, this.lastModifiedDate, this.createdDate, this.otpReference, this.createdBy, this.lastModifiedBy, this.loggedInUserId, this.otpValidationMandatory, this.mobileValidationMandatory, this.alternateMobileNumber);
        }

        public String toString() {
            return "User.UserBuilder(id=" + this.id + ", uuid=" + this.uuid + ", tenantId=" + this.tenantId + ", username=" + this.username + ", title=" + this.title + ", password=" + this.password + ", salutation=" + this.salutation + ", guardian=" + this.guardian + ", guardianRelation=" + this.guardianRelation + ", name=" + this.name + ", gender=" + this.gender + ", mobileNumber=" + this.mobileNumber + ", emailId=" + this.emailId + ", altContactNumber=" + this.altContactNumber + ", pan=" + this.pan + ", aadhaarNumber=" + this.aadhaarNumber + ", permanentAddress=" + this.permanentAddress + ", correspondenceAddress=" + this.correspondenceAddress + ", addresses=" + this.addresses + ", active=" + this.active + ", roles=" + this.roles + ", dob=" + this.dob + ", passwordExpiryDate=" + this.passwordExpiryDate + ", locale=" + this.locale + ", type=" + this.type + ", bloodGroup=" + this.bloodGroup + ", identificationMark=" + this.identificationMark + ", signature=" + this.signature + ", photo=" + this.photo + ", accountLocked=" + this.accountLocked + ", accountLockedDate=" + this.accountLockedDate + ", lastModifiedDate=" + this.lastModifiedDate + ", createdDate=" + this.createdDate + ", otpReference=" + this.otpReference + ", createdBy=" + this.createdBy + ", lastModifiedBy=" + this.lastModifiedBy + ", loggedInUserId=" + this.loggedInUserId + ", otpValidationMandatory=" + this.otpValidationMandatory + ", mobileValidationMandatory=" + this.mobileValidationMandatory + ", alternateMobileNumber=" + this.alternateMobileNumber + ")";
        }
    }
}
