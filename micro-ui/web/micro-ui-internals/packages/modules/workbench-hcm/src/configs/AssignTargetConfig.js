export const AssignTargetConfig = [
  {
    body:[
      {
    label: "WBH_BENEFICIARY_TYPE_LABEL",
    type: "text",
    isMandatory: false,
    populators: {
      name: "beneficiaryType",
      validation: { pattern: {} },
    },
  },
  {
    label: "WBH_TOTAL_NO_LABEL",
    type: "number",
    isMandatory: false,
    populators: {
      name: "totalNo",
      validation: { pattern: "^[1-9]+[0-9]*$" },
    },
  },
  {
    label: "WBH_TARGET_NO_LABEL",
    type: "number",
    isMandatory: false,
    populators: {
      name: "targetNo",
      validation: { pattern: {} },
    },
  },
]
  }
];
