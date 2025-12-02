export function validateFiveChar(rule, value) {
    value = value.trim()
    if (value && value.length < 5) {
        return Promise.reject("Should be at least 5 characters long.");
    }
    return Promise.resolve();
}