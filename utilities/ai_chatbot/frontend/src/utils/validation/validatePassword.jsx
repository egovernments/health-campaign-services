export const validatePassword = [
  {
    message: "Please enter a new password.",
  },
  {
    validator: (_, value) => {
      if (!value) return Promise.resolve();

      if (value.length < 6) {
        return Promise.reject(
          new Error("Password must be at least 6 characters.")
        );
      }

      if (!/[A-Z]/.test(value)) {
        return Promise.reject(
          new Error("Password must include at least one uppercase letter.")
        );
      }

      if (!/[a-z]/.test(value)) {
        return Promise.reject(
          new Error("Password must include at least one lowercase letter.")
        );
      }

      if (!/\d/.test(value)) {
        return Promise.reject(
          new Error("Password must include at least one number.")
        );
      }

      if (!/[!@#$%^&*(),.?":{}|<>]/.test(value)) {
        return Promise.reject(
          new Error("Password must include at least one special character.")
        );
      }

      return Promise.resolve();
    },
  },
];
