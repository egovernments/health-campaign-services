package domain

import "time"

type AbhaNumber struct {
	ID               int64     `json:"-"` // Internal DB ID, hidden from public response
	ExternalID       string    `db:"external_id"`
	Deleted          bool      `db:"deleted"`
	ABHANumber       string    `db:"abha_number"`
	HealthID         string    `db:"health_id"`
	Email            string    `db:"email"`
	FirstName        string    `db:"first_name"`
	MiddleName       string    `db:"middle_name"`
	LastName         string    `db:"last_name"`
	ProfilePhoto     string    `db:"profile_photo"`
	AccessToken      string    `db:"access_token"`
	RefreshToken     string    `db:"refresh_token"`
	Address          string    `db:"address"`
	DateOfBirth      string    `db:"date_of_birth"`
	District         string    `db:"district"`
	Gender           string    `db:"gender"`
	Name             string    `db:"name"`
	Pincode          string    `db:"pincode"`
	State            string    `db:"state"`
	Mobile           string    `db:"mobile"`
	New              bool      `db:"new"`
	CreatedBy        int64     `json:"created_by,omitempty"`
	CreatedDate      time.Time `json:"created_date,omitempty"`
	LastModifiedBy   int64     `json:"last_modified_by,omitempty"`
	LastModifiedDate time.Time `json:"last_modified_date,omitempty"`
}
