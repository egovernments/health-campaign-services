export function validateUsername(rule, value) {
    const regex = /^[a-z]{2,}$/; // Only allow lowercase letters and minimum 2 characters, no symbols
    if (value && !regex.test(value)) {
      return Promise.reject(
        "Should be at least 2 characters long and only contain lowercase letters."
      );
    }
    return Promise.resolve();
  }
  