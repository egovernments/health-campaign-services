export function validateTwoChar(rule, value) {
    if (!value || typeof value !== 'string') {
        return Promise.reject("This field is required.");
    }
    value = value.trim();
    if (value.length < 2) {
        return Promise.reject("Should be at least 2 characters long.");
    }
    return Promise.resolve();
}
