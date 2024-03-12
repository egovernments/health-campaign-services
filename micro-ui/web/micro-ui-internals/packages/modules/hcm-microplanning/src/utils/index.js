import _ from "lodash";


const formatDates = (value, type) => {
  if (type != "EPOC" && (!value || Number.isNaN(value))) {
    value = new Date();
  }
  switch (type) {
    case "date":
      return new Date(value)?.toISOString?.()?.split?.("T")?.[0];
    case "datetime":
      return new Date(value).toISOString();
    case "EPOC":
      return String(new Date(value)?.getTime());
  }
};

export default { formatDates};
