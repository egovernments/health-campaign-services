interface PaymentValidationResult {
    valid: boolean;
    errors: string[];
}

const BANK_ACCOUNT_PATTERN = /^[0-9]{10}$/;
const BANK_CODE_PATTERN = /^$|^[0-9]{3}$|^[0-9]{9}$/;
const ALPHANUMERIC_PATTERN = /^[A-Za-z0-9]{1,35}$/;

export function validatePaymentFields(workerData: {
    paymentProvider?: string;
    payeeName?: string;
    beneficiaryCode?: string;
    bankAccount?: string;
    bankCode?: string;
    payeePhoneNumber?: string;
}): PaymentValidationResult {
    const errors: string[] = [];
    const provider = workerData.paymentProvider?.trim() || "";

    if (provider === "BANK") {
        if (!workerData.payeeName?.trim()) {
            errors.push("Payee Name is required for BANK payment provider.");
        }
        if (!workerData.beneficiaryCode?.trim()) {
            errors.push("Beneficiary Code is required for BANK payment provider.");
        } else if (!ALPHANUMERIC_PATTERN.test(workerData.beneficiaryCode.trim())) {
            errors.push("Beneficiary Code must be alphanumeric and max 35 characters.");
        }
        if (!workerData.bankAccount?.trim()) {
            errors.push("Bank Account is required for BANK payment provider.");
        } else if (!BANK_ACCOUNT_PATTERN.test(workerData.bankAccount.trim())) {
            errors.push("Bank Account must be exactly 10 digits.");
        }
        const bankCode = workerData.bankCode?.trim() || "";
        if (!BANK_CODE_PATTERN.test(bankCode)) {
            errors.push("Bank Code must be blank, 3 digits, or 9 digits.");
        }
    } else if (provider === "MTN") {
        if (!workerData.payeePhoneNumber?.trim()) {
            errors.push("Payee Phone Number is required for MTN payment provider.");
        }
        if (!workerData.payeeName?.trim()) {
            errors.push("Payee Name is required for MTN payment provider.");
        }
    }

    return { valid: errors.length === 0, errors };
}
