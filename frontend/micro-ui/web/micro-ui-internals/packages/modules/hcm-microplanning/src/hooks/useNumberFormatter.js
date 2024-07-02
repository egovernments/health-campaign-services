import { useTranslation } from "react-i18next";

export const useNumberFormatter = (FormatMapping) => {
  const { i18n } = useTranslation();

  const formatNumber = (value, options) => {
    try {
      const currentLanguage = i18n.language;
      const fallbackLanguage = i18n.options.fallbackLng[0]; // Get the first language in the fallback list
      const locale = FormatMapping?.[currentLanguage] || FormatMapping?.[fallbackLanguage] || currentLanguage || "";
      return new Intl.NumberFormat(locale, options).format(value);
    } catch (error) {
      console.error("Error formatting number:", error);
      return value;
    }
  };

  return { formatNumber };
};

export default useNumberFormatter;
