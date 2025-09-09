// package errorsx

// import (
// 	"encoding/json"
// 	"errors"
// 	"fmt"
// 	"net/http"

// 	"github.com/gin-gonic/gin"
// )

// type ErrorType string

// const (
// 	Recoverable    ErrorType = "RECOVERABLE"
// 	NonRecoverable ErrorType = "NON_RECOVERABLE"
// )

// type Error struct {
// 	// Keep the JSON shape same as Java
// 	Exception         string      `json:"exception,omitempty"` // cause stringified
// 	ErrorCode         string      `json:"errorCode,omitempty"`
// 	ErrorMessage      string      `json:"errorMessage,omitempty"`
// 	Type              ErrorType   `json:"type,omitempty"`
// 	AdditionalDetails interface{} `json:"additionalDetails,omitempty"`

// 	// not exported in JSON
// 	status int   `json:"-"`
// 	cause  error `json:"-"`
// }

// // --- Constructors ------------------------------------------------------------

// func New(code, msg string, t ErrorType, status int, details interface{}, cause error) *Error {
// 	e := &Error{
// 		ErrorCode:         code,
// 		ErrorMessage:      msg,
// 		Type:              t,
// 		AdditionalDetails: details,
// 		status:            status,
// 		cause:             cause,
// 	}
// 	if cause != nil {
// 		e.Exception = cause.Error()
// 	}
// 	return e
// }

// func RecoverableErr(code, msg string, status int, details interface{}, cause error) *Error {
// 	return New(code, msg, Recoverable, status, details, cause)
// }

// func NonRecoverableErr(code, msg string, status int, details interface{}, cause error) *Error {
// 	return New(code, msg, NonRecoverable, status, details, cause)
// }

// // Common helpers (opinioned mappings)
// func BadRequest(code, msg string, details interface{}, cause error) *Error {
// 	return RecoverableErr(code, msg, http.StatusBadRequest, details, cause)
// }
// func Unauthorized(code, msg string, details interface{}, cause error) *Error {
// 	return RecoverableErr(code, msg, http.StatusUnauthorized, details, cause)
// }
// func Forbidden(code, msg string, details interface{}, cause error) *Error {
// 	return RecoverableErr(code, msg, http.StatusForbidden, details, cause)
// }
// func NotFound(code, msg string, details interface{}, cause error) *Error {
// 	return RecoverableErr(code, msg, http.StatusNotFound, details, cause)
// }
// func UpstreamFailed(code, msg string, status int, details interface{}, cause error) *Error {
// 	// Treat upstream failures as recoverable by default; you can flip if needed
// 	return RecoverableErr(code, msg, status, details, cause)
// }
// func Internal(code, msg string, details interface{}, cause error) *Error {
// 	return NonRecoverableErr(code, msg, http.StatusInternalServerError, details, cause)
// }

// // --- Error interface / helpers ----------------------------------------------

// func (e *Error) Error() string {
// 	if e == nil {
// 		return "<nil>"
// 	}
// 	if e.cause != nil {
// 		return fmt.Sprintf("%s: %s (cause: %v)", e.ErrorCode, e.ErrorMessage, e.cause)
// 	}
// 	return fmt.Sprintf("%s: %s", e.ErrorCode, e.ErrorMessage)
// }

// func (e *Error) Unwrap() error { return e.cause }

// func (e *Error) WithDetails(details interface{}) *Error {
// 	e.AdditionalDetails = details
// 	return e
// }

// func (e *Error) WithStatus(status int) *Error {
// 	e.status = status
// 	return e
// }

// // --- HTTP response writer ----------------------------------------------------
// func Write(c *gin.Context, err error) {
// 	if err == nil {
// 		c.Status(http.StatusNoContent)
// 		return
// 	}
// 	var ae *Error
// 	if !errors.As(err, &ae) {
// 		ae = Internal(CodeInternal, "unexpected error", nil, err)
// 	}
// 	status := ae.status
// 	if status == 0 {
// 		status = http.StatusInternalServerError
// 	}
// 	// Ensure JSON content-type and write exactly once
// 	c.Header("Content-Type", "application/json")
// 	c.JSON(status, toEgov(ae))
// }

// // Optional: centralized success writer to keep one exit path in handlers
// func OK(c *gin.Context, body interface{}) {
// 	switch b := body.(type) {
// 	case []byte:
// 		c.Data(http.StatusOK, "application/json", b)
// 	default:
// 		c.JSON(http.StatusOK, b)
// 	}
// }

// // If your upstream returns a body and status, translate to *Error neatly
// func FromUpstream(status int, body []byte, codePrefix string) *Error {
// 	// Try to parse upstream error JSON if any, otherwise bubble up as text
// 	var parsed map[string]interface{}
// 	_ = json.Unmarshal(body, &parsed)
// 	msg := http.StatusText(status)
// 	if len(parsed) > 0 {
// 		return UpstreamFailed(codePrefix+"_UPSTREAM", msg, status, parsed, fmt.Errorf("upstream status %d", status))
// 	}
// 	return UpstreamFailed(codePrefix+"_UPSTREAM", msg, status, string(body), fmt.Errorf("upstream status %d", status))
// }

package errorsx

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"

	"github.com/gin-gonic/gin"
)

type ErrorType string

const (
	Recoverable    ErrorType = "RECOVERABLE"
	NonRecoverable ErrorType = "NON_RECOVERABLE"
)

type Error struct {
	Exception         string      `json:"exception,omitempty"` // cause stringified
	ErrorCode         string      `json:"errorCode,omitempty"`
	ErrorMessage      string      `json:"errorMessage,omitempty"`
	Type              ErrorType   `json:"type,omitempty"`
	AdditionalDetails interface{} `json:"additionalDetails,omitempty"`

	status int   `json:"-"`
	cause  error `json:"-"`
}

// --- Constructors ------------------------------------------------------------

func New(code, msg string, t ErrorType, status int, details interface{}, cause error) *Error {
	e := &Error{
		ErrorCode:         code,
		ErrorMessage:      msg,
		Type:              t,
		AdditionalDetails: details,
		status:            status,
		cause:             cause,
	}
	if cause != nil {
		e.Exception = cause.Error()
	}
	return e
}

func RecoverableErr(code, msg string, status int, details interface{}, cause error) *Error {
	return New(code, msg, Recoverable, status, details, cause)
}

func NonRecoverableErr(code, msg string, status int, details interface{}, cause error) *Error {
	return New(code, msg, NonRecoverable, status, details, cause)
}

// Opinionated helpers
func BadRequest(code, msg string, details interface{}, cause error) *Error {
	return RecoverableErr(code, msg, http.StatusBadRequest, details, cause)
}
func Unauthorized(code, msg string, details interface{}, cause error) *Error {
	return RecoverableErr(code, msg, http.StatusUnauthorized, details, cause)
}
func Forbidden(code, msg string, details interface{}, cause error) *Error {
	return RecoverableErr(code, msg, http.StatusForbidden, details, cause)
}
func NotFound(code, msg string, details interface{}, cause error) *Error {
	return RecoverableErr(code, msg, http.StatusNotFound, details, cause)
}
func UpstreamFailed(code, msg string, status int, details interface{}, cause error) *Error {
	return RecoverableErr(code, msg, status, details, cause)
}
func Internal(code, msg string, details interface{}, cause error) *Error {
	return NonRecoverableErr(code, msg, http.StatusInternalServerError, details, cause)
}

// --- Error interface / helpers ----------------------------------------------

func (e *Error) Error() string {
	if e == nil {
		return "<nil>"
	}
	if e.cause != nil {
		return fmt.Sprintf("%s: %s (cause: %v)", e.ErrorCode, e.ErrorMessage, e.cause)
	}
	return fmt.Sprintf("%s: %s", e.ErrorCode, e.ErrorMessage)
}

func (e *Error) Unwrap() error { return e.cause }

func (e *Error) WithDetails(details interface{}) *Error {
	e.AdditionalDetails = details
	return e
}

func (e *Error) WithStatus(status int) *Error {
	e.status = status
	return e
}

// --- Egov envelope -----------------------------------------------------------

type egovErrorItem struct {
	ID          *string     `json:"id"`
	ParentID    *string     `json:"parentId"`
	Code        string      `json:"code"`
	Message     string      `json:"message"`
	Description *string     `json:"description"`
	Params      interface{} `json:"params"`
}

type egovErrorEnvelope struct {
	Errors []egovErrorItem `json:"Errors"`
}

func toEgov(ae *Error) egovErrorEnvelope {
	var descPtr *string
	if ae.Exception != "" {
		s := ae.Exception
		descPtr = &s
	}
	return egovErrorEnvelope{
		Errors: []egovErrorItem{
			{
				ID:          nil,
				ParentID:    nil,
				Code:        ae.ErrorCode,
				Message:     ae.ErrorMessage,
				Description: descPtr,
				Params:      ae.AdditionalDetails,
			},
		},
	}
}

// --- HTTP writers ------------------------------------------------------------

func Write(c *gin.Context, err error) {
	if err == nil {
		c.Status(http.StatusNoContent)
		return
	}
	var ae *Error
	if !errors.As(err, &ae) {
		ae = Internal(CodeInternal, "unexpected error", nil, err)
	}
	status := ae.status
	if status == 0 {
		status = http.StatusInternalServerError
	}
	c.Header("Content-Type", "application/json")
	c.JSON(status, toEgov(ae))
}

func OK(c *gin.Context, body interface{}) {
	switch b := body.(type) {
	case []byte:
		c.Data(http.StatusOK, "application/json", b)
	default:
		c.JSON(http.StatusOK, b)
	}
}

// --- Upstream translator -----------------------------------------------------

// FromUpstream maps arbitrary upstream error bodies into our Error.
// Recognizes common shapes like:
// 1) {"error":{"code":"ABDM-1114","message":"..."}}
// 2) {"code":"...", "message":"..."}
// 3) {"errors":[{"code":"...","message":"..."}]}
// 4) {"errorCode":"...","errorMessage":"..."}
// Falls back to codePrefix+"_UPSTREAM" and http.StatusText(status) if none found.
func FromUpstream(status int, body []byte, codePrefix string) *Error {
	type topErr struct {
		Error struct {
			Code    string `json:"code"`
			Message string `json:"message"`
		} `json:"error"`
		Code         string `json:"code"`
		Message      string `json:"message"`
		ErrorCode    string `json:"errorCode"`
		ErrorMessage string `json:"errorMessage"`
		Errors       []struct {
			Code    string `json:"code"`
			Message string `json:"message"`
		} `json:"errors"`
	}
	var te topErr
	var parsed map[string]interface{}
	_ = json.Unmarshal(body, &te)
	_ = json.Unmarshal(body, &parsed)

	code := ""
	msg := http.StatusText(status)

	switch {
	case te.Error.Code != "" || te.Error.Message != "":
		if te.Error.Code != "" {
			code = te.Error.Code
		}
		if te.Error.Message != "" {
			msg = te.Error.Message
		}
	case te.Code != "" || te.Message != "":
		if te.Code != "" {
			code = te.Code
		}
		if te.Message != "" {
			msg = te.Message
		}
	case te.ErrorCode != "" || te.ErrorMessage != "":
		if te.ErrorCode != "" {
			code = te.ErrorCode
		}
		if te.ErrorMessage != "" {
			msg = te.ErrorMessage
		}
	case len(te.Errors) > 0:
		if te.Errors[0].Code != "" {
			code = te.Errors[0].Code
		}
		if te.Errors[0].Message != "" {
			msg = te.Errors[0].Message
		}
	}

	if code == "" {
		code = codePrefix + "_UPSTREAM"
	}
	// Keep all parsed JSON as params for observability; if not JSON, keep raw string.
	var params interface{} = nil
	if len(parsed) > 0 {
		params = parsed
	} else if len(body) > 0 {
		params = string(body)
	}

	return UpstreamFailed(code, msg, status, params, fmt.Errorf("upstream status %d", status))
}
