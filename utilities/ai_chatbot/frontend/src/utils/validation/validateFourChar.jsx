export function validateFourChar(rule, value) {
    value = value.trim()
    if (value && value.length < 4) {
        return Promise.reject("Should be at least 4 characters long.");
    }
    return Promise.resolve();
}