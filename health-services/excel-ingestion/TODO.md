# Excel Ingestion Service - TODO

## Pending Tasks

### Status Handling
- [ ] After resource is generated it should return `"status": "generated"`
- [ ] If generation fails in between then return `"status": "failed"`

## Notes
- Status should be included in the response to indicate generation result
- Proper error handling needed for failed generation scenarios