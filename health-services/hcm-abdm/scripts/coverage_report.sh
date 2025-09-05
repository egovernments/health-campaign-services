#!/bin/sh

# POSIX shell script to generate coverage report for ABHA handlers
# This script runs tests, generates coverage data, and produces a detailed report

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    printf "${GREEN}[INFO]${NC} %s\n" "$1"
}

print_warning() {
    printf "${YELLOW}[WARN]${NC} %s\n" "$1"
}

print_error() {
    printf "${RED}[ERROR]${NC} %s\n" "$1"
}

print_header() {
    printf "\n${BLUE}=== %s ===${NC}\n" "$1"
}

# Check if we're in the right directory
if [ ! -f "go.mod" ]; then
    print_error "go.mod not found. Please run this script from the project root."
    exit 1
fi

print_header "Starting Coverage Analysis for ABHA Handlers"

# Clean up any previous coverage files
print_status "Cleaning up previous coverage files..."
rm -f coverage.out coverage_func.txt coverage.html

# Run tests with coverage
print_status "Running tests with coverage..."
if ! go test ./... -count=1 -coverprofile=coverage.out -covermode=atomic; then
    print_error "Tests failed. Coverage analysis aborted."
    exit 1
fi

# Check if coverage file was generated
if [ ! -f "coverage.out" ]; then
    print_error "Coverage file not generated. Tests may have failed."
    exit 1
fi

# Generate coverage function report
print_status "Generating coverage function report..."
go tool cover -func=coverage.out > coverage_func.txt

# Generate HTML coverage report
print_status "Generating HTML coverage report..."
go tool cover -html=coverage.out -o coverage.html

print_status "Coverage files generated:"
print_status "  - coverage.out (raw coverage data)"
print_status "  - coverage_func.txt (function-level coverage)"
print_status "  - coverage.html (HTML report)"

# Parse coverage data and generate markdown report
print_status "Parsing coverage data for targeted handlers..."

# Function to extract coverage for specific functions
extract_coverage() {
    local file_pattern="$1"
    local function_pattern="$2"
    
    if [ -f "coverage_func.txt" ]; then
        grep "$file_pattern" coverage_func.txt | grep "$function_pattern" || echo ""
    else
        echo ""
    fi
}

# Function to calculate coverage percentage
calculate_percentage() {
    local covered="$1"
    local total="$2"
    
    if [ "$total" -eq 0 ]; then
        echo "0.0"
    else
        awk "BEGIN {printf \"%.1f\", ($covered/$total)*100}"
    fi
}

# Generate or update REPORT.md
print_status "Generating REPORT.md..."

cat > REPORT.md << 'EOF'
# ABHA Handlers Test Coverage Report

This report provides detailed coverage analysis for the five targeted ABHA handlers and their directly invoked functions.

## Section A: Planned Coverage

### Targeted Handlers and Functions

#### 1. SendAadhaarOTP Handler
**Primary Function:** `internal/handlers/abha_handler.go:SendAadhaarOTP`

**Directly Invoked Functions:**
- `ABHAService.FetchPublicKey()` - Fetches ABDM public key for encryption
- `ABHAService.EncryptData()` - Encrypts Aadhaar number for secure transmission
- `ABHAService.SendOTPRequest()` - Sends OTP request to ABDM API
- `ABHAService.RecordAadhaarTxnOnOtp()` - Records transaction in database
- `utils.FetchABDMToken()` - Retrieves authentication token
- `json.Unmarshal()` - Parses response for transaction ID extraction
- Request binding and validation logic
- Error handling and response formatting

#### 2. VerifyAndEnrolByAadhaarWithOTP Handler
**Primary Function:** `internal/handlers/abha_handler.go:VerifyAndEnrolByAadhaarWithOTP`

**Directly Invoked Functions:**
- `ABHAService.FetchPublicKey()` - Fetches public key for OTP encryption
- `ABHAService.EncryptData()` - Encrypts OTP value
- `ABHAService.SendEnrolRequestWithOTP()` - Processes enrollment with encrypted OTP
- `utils.FetchABDMToken()` - Authentication token retrieval
- `time.Now().Format()` - Timestamp formatting for OTP request
- Request binding and validation logic
- Error handling and response formatting

#### 3. GetABHACardUnified Handler
**Primary Function:** `internal/handlers/abha_handler.go:GetABHACardUnified`

**Directly Invoked Functions:**
- `ABHAService.FetchAbhaCardByType()` - Retrieves ABHA card in specified format
- Request binding and validation logic
- Content-type determination and header setting
- Error handling and binary response formatting

#### 4. ProfileLoginRequestOTP Handler
**Primary Function:** `internal/handlers/abha_handler.go:ProfileLoginRequestOTP`

**Directly Invoked Functions:**
- `ABHAService.FetchPublicKey()` - Public key retrieval for encryption
- `ABHAService.EncryptData()` - Encrypts login credentials
- `ABHAService.ProfileLoginRequestOTP()` - Sends profile login OTP request
- `ABHAService.ResolveAadhaarFromABHA()` - Resolves Aadhaar from ABHA number
- `utils.ValidateABHANumber()` - ABHA number format validation
- `utils.CanonicalizeABHA()` - ABHA number canonicalization
- `utils.FetchABDMToken()` - Authentication token retrieval
- Scope determination logic
- Login hint processing (aadhaar/mobile/abha-number)
- Error handling and response formatting

#### 5. ProfileLoginVerifyOTP Handler
**Primary Function:** `internal/handlers/abha_handler.go:ProfileLoginVerifyOTP`

**Directly Invoked Functions:**
- `ABHAService.FetchPublicKey()` - Public key retrieval
- `ABHAService.EncryptData()` - OTP encryption
- `ABHAService.ProfileLoginVerifyOTP()` - OTP verification and profile retrieval
- `utils.FetchABDMToken()` - Authentication token retrieval
- Default scope determination logic
- Request binding and validation logic
- Error handling and response formatting

## Section B: Measured Coverage

EOF

# Extract actual coverage data from coverage_func.txt and append to REPORT.md
if [ -f "coverage_func.txt" ]; then
    echo "### Handler Coverage Results" >> REPORT.md
    echo "" >> REPORT.md
    
    # Create table header
    echo "| Handler | Function | Lines of Logic Under Test | Lines Covered | Coverage % |" >> REPORT.md
    echo "|---------|----------|---------------------------|---------------|------------|" >> REPORT.md
    
    # Process each handler
    handlers="SendAadhaarOTP VerifyAndEnrolByAadhaarWithOTP GetABHACardUnified ProfileLoginRequestOTP ProfileLoginVerifyOTP"
    
    for handler in $handlers; do
        # Extract coverage line for this handler
        coverage_line=$(grep "internal/handlers.*$handler" coverage_func.txt 2>/dev/null || echo "")
        
        if [ -n "$coverage_line" ]; then
            # Parse the coverage line (format: filename:function coverage_pct)
            # Example: internal/handlers/abha_handler.go:SendAadhaarOTP    85.7%
            func_name=$(echo "$coverage_line" | awk '{print $1}' | sed 's/.*://')
            coverage_pct=$(echo "$coverage_line" | awk '{print $2}')
            
            # For this example, we'll use placeholder values for lines
            # In a real implementation, you might parse more detailed coverage data
            lines_total="Unknown"
            lines_covered="Unknown"
            
            echo "| $handler | $func_name | $lines_total | $lines_covered | $coverage_pct |" >> REPORT.md
        else
            echo "| $handler | Not found in coverage | N/A | N/A | 0.0% |" >> REPORT.md
        fi
    done
    
    echo "" >> REPORT.md
    
    # Add overall statistics
    echo "### Overall Statistics" >> REPORT.md
    echo "" >> REPORT.md
    
    total_coverage=$(tail -n 1 coverage_func.txt | awk '{print $3}' || echo "0.0%")
    echo "**Total Project Coverage:** $total_coverage" >> REPORT.md
    echo "" >> REPORT.md
    
    # Extract handler-specific coverage
    handler_coverage=$(grep "internal/handlers" coverage_func.txt | awk '{total+=$2; count++} END {if(count>0) printf "%.1f%%", (total/count); else print "0.0%"}' || echo "0.0%")
    echo "**Handler Package Coverage:** $handler_coverage" >> REPORT.md
    echo "" >> REPORT.md
    
else
    echo "### Coverage Data Not Available" >> REPORT.md
    echo "" >> REPORT.md
    echo "Coverage function report (coverage_func.txt) was not generated successfully." >> REPORT.md
    echo "Please ensure tests run successfully and coverage.out is created." >> REPORT.md
    echo "" >> REPORT.md
fi

# Add Section C
cat >> REPORT.md << 'EOF'
## Section C: Untested Branches to Tackle Next

### Critical Paths Requiring Additional Coverage

#### High Priority (Security & Data Integrity)
1. **Error Handling Edge Cases**
   - Network timeout scenarios in external API calls
   - Malformed responses from ABDM services
   - Database connection failures during transaction recording
   - Invalid encryption key scenarios

2. **Input Validation Boundary Cases**
   - Malformed Aadhaar numbers with special characters
   - ABHA numbers with invalid checksums
   - OTP values with non-numeric characters
   - Empty or null request bodies

3. **Authentication & Authorization**
   - Expired or invalid ABDM tokens
   - Rate limiting scenarios
   - Concurrent request handling

#### Medium Priority (Business Logic)
1. **State Management**
   - Transaction ID collision handling
   - Duplicate OTP requests for same Aadhaar
   - Session timeout scenarios

2. **Data Format Variations**
   - Different ABHA card formats (SVG, PNG, JPEG)
   - Various mobile number formats
   - International vs domestic phone numbers

#### Low Priority (Performance & Monitoring)
1. **Logging and Monitoring**
   - Audit trail completeness
   - Performance metrics collection
   - Error rate monitoring

2. **Configuration Edge Cases**
   - Missing environment variables
   - Invalid service endpoints
   - Timeout configuration variations

### Recommendations for Next Testing Phase

1. **Add Integration Tests**
   - Test actual ABDM API integration with sandbox
   - Database transaction rollback scenarios
   - End-to-end user journey testing

2. **Performance Testing**
   - Load testing for concurrent OTP requests
   - Memory usage profiling
   - Response time optimization

3. **Security Testing**
   - Input sanitization validation
   - Encryption/decryption error handling
   - Token expiry edge cases

### Test Data Scenarios to Implement

```go
// Example additional test cases needed
var additionalTestScenarios = []TestCase{
    {
        Name: "Aadhaar number with hyphens and spaces",
        Input: "1234-5678-9012-3456",
        Expected: "normalized and encrypted properly",
    },
    {
        Name: "Concurrent OTP requests for same Aadhaar",
        Scenario: "Race condition testing",
        Expected: "Proper transaction isolation",
    },
    {
        Name: "Network timeout during ABDM call",
        MockBehavior: "Simulate timeout after 30 seconds",
        Expected: "Graceful error handling",
    },
    // Add more scenarios...
}
```

---

**Report Generated:** $(date)
**Coverage Tool:** Go 1.23.0 built-in coverage
**Test Framework:** testify v1.10.0
EOF

print_header "Coverage Analysis Complete"
print_status "Generated files:"
printf "  ${GREEN}✓${NC} coverage.out - Raw coverage data\n"
printf "  ${GREEN}✓${NC} coverage_func.txt - Function-level coverage details\n"
printf "  ${GREEN}✓${NC} coverage.html - Interactive HTML coverage report\n"
printf "  ${GREEN}✓${NC} REPORT.md - Detailed markdown coverage report\n"

# Display quick summary
if [ -f "coverage_func.txt" ]; then
    print_header "Quick Coverage Summary"
    total_coverage=$(tail -n 1 coverage_func.txt)
    printf "  ${BLUE}Total Coverage:${NC} %s\n" "$total_coverage"
    
    handler_count=$(grep -c "internal/handlers.*func" coverage_func.txt 2>/dev/null || echo "0")
    printf "  ${BLUE}Handler Functions Analyzed:${NC} %s\n" "$handler_count"
fi

print_status "Open coverage.html in your browser for detailed visual coverage analysis"
print_status "Review REPORT.md for comprehensive coverage breakdown"

exit 0