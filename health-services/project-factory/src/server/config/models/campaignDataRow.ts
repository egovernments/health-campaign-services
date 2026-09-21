/**
 * One shape for every campaign_data read: the DB returns lowercased columns, so each reader used to
 * re-map them by hand and re-guess the field types. `data` stays free-form — it is the sheet row as
 * uploaded, and its keys differ per resource type.
 */
export interface CampaignDataRow {
  campaignNumber: string;
  type: string;
  data: Record<string, any>;
  uniqueIdentifier: string;
  status: string;
  uniqueIdAfterProcess: string | null;
  // Written by the attendance sync when the register is deleted / the person de-enrolled in the
  // attendance service. denrollmentDate is BIGINT, which node-postgres returns as a string, so every
  // mapper coerces it here rather than leaving each download path to remember.
  isDeleted: boolean;
  denrollmentDate: number | null;
}

/** What data/campaign/_search returns: the same contract plus the row's identity and audit fields. */
export interface CampaignDataApiRow extends CampaignDataRow {
  id: string;
  createdBy: string;
  createdTime: number;
  lastModifiedBy: string;
  lastModifiedTime: number;
}
