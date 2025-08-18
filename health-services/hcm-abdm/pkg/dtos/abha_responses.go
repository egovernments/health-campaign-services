package dtos

// ---------- ABHA RESPONSE DTOs ----------

type AbhaNumberResponse struct {
	ExternalID   string `json:"external_id"`
	AbhaNumber   string `json:"abha_number"`
	HealthID     string `json:"health_id"`
	Name         string `json:"name"`
	FirstName    string `json:"first_name"`
	MiddleName   string `json:"middle_name"`
	LastName     string `json:"last_name"`
	Gender       string `json:"gender"`
	DateOfBirth  string `json:"date_of_birth"`
	Address      string `json:"address"`
	District     string `json:"district"`
	State        string `json:"state"`
	PinCode      string `json:"pincode"`
	Mobile       string `json:"mobile"`
	Email        string `json:"email"`
	ProfilePhoto string `json:"profile_photo"`
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	New          bool   `json:"new"`
}

type AbhaTxnResponse struct {
	TransactionID string              `json:"transaction_id"`
	Detail        string              `json:"detail,omitempty"`
	AbhaNumber    *AbhaNumberResponse `json:"abha_number,omitempty"`
	Created       bool                `json:"created,omitempty"`
	AuthMethods   []string            `json:"auth_methods,omitempty"`
	AbhaAddresses []string            `json:"abha_addresses,omitempty"`
	PreferredAbha string              `json:"preferred_abha_address,omitempty"`
	HealthID      string              `json:"health_id,omitempty"`
	Success       bool                `json:"success,omitempty"`
}

type EnrolWithOTPResponse struct {
	TxnId       string       `json:"txnId"`
	IsNew       bool         `json:"isNew"`
	Tokens      TokenDetails `json:"tokens"`
	ABHAProfile AbhaProfile  `json:"ABHAProfile"` // tag must match the case exactly
}

type TokenDetails struct {
	Token            string `json:"token"`
	ExpiresIn        int64  `json:"expiresIn"`
	RefreshToken     string `json:"refreshToken"`
	RefreshExpiresIn int64  `json:"refreshExpiresIn"`
}

type AbhaProfile struct {
	ABHANumber   string   `json:"ABHANumber"`
	PhrAddress   []string `json:"phrAddress"`
	FirstName    string   `json:"firstName"`
	MiddleName   string   `json:"middleName"`
	LastName     string   `json:"lastName"`
	Gender       string   `json:"gender"`
	Dob          string   `json:"dob"`
	Mobile       string   `json:"mobile"`
	Email        string   `json:"email"`
	Address      string   `json:"address"`
	StateCode    string   `json:"stateCode"`
	DistrictCode string   `json:"districtCode"`
	PinCode      string   `json:"pinCode"`
	Photo        string   `json:"photo"`
}
