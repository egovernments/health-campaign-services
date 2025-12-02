export function validateEmail(rule, value) {
    return new Promise((resolve, reject) => {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (value && !emailRegex.test(value)) {
            reject('Please enter a valid email address');
        } else {
            resolve();
        }
    });
};