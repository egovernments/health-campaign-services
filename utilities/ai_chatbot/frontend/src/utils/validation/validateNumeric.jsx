export function validateNumeric(rule, value) {
  const regex = /^[0-9]*$/;
  if (value && !regex.test(value)) {
    return Promise.reject("Only numbers are allowed");
  }
  if (!value || (Number(value) >= 1 && Number(value) <= 65535)) {
    return Promise.resolve();
  }
  return Promise.reject(new Error('Port must be a number between 1 and 65535!'));
}

export function validateNineDigitNumber(_, value) {
  const pattern = /^\d{9}$/;
  if (value && !pattern.test(value)) {
    return Promise.reject("Please enter a 9-digit number");
  }
  return Promise.resolve();
}

export function validateEightDigitNumber(_, value) {
  const pattern = /^\d{8}$/;
  const codes = value.split(",");
  for (let i = 0; i < codes.length; i++) {
    const code = codes[i].trim();
    if (i !== 0 && code === "") {
      return Promise.reject("Expecting a value after comma");
    }
    if (value && !pattern.test(code)) {
      return Promise.reject("Please enter a 8-digit number.");
    }
  }
  return Promise.resolve();
}

export function validateFiveorEightDigitNumber(_, value) {
  const pattern = /^\d{5,8}$/;
  const codes = value.split(",");
  for (let i = 0; i < codes.length; i++) {
    const code = codes[i].trim();
    if (i !== 0 && code === "") {
      return Promise.reject("Expecting a value after comma");
    }
    if (value && !pattern.test(code)) {
      return Promise.reject("DOT Number should be 5 to 8 digit long");
    }
  }
  return Promise.resolve();
}
