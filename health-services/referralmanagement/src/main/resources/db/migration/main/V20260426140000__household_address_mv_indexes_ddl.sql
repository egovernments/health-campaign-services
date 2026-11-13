  CREATE MATERIALIZED VIEW IF NOT EXISTS household_address_mv AS
   SELECT h.id,
      h.tenantid,
      h.clientreferenceid,
      h.numberofmembers,
      h.addressid,
      h.additionaldetails,
      h.createdby,
      h.lastmodifiedby,
      h.createdtime,
      h.lastmodifiedtime,
      h.rowversion,
      h.isdeleted,
      h.clientcreatedtime,
      h.clientlastmodifiedtime,
      h.clientcreatedby,
      h.clientlastmodifiedby,
      h.householdtype,
      a.id AS aid,
      a.tenantid AS atenantid,
      a.clientreferenceid AS aclientreferenceid,
      a.doorno,
      a.latitude,
      a.longitude,
      a.locationaccuracy,
      a.type,
      a.addressline1,
      a.addressline2,
      a.landmark,
      a.city,
      a.pincode,
      a.buildingname,
      a.street,
      a.localitycode,
      row_number() OVER (PARTITION BY a.localitycode ORDER BY h.id) AS rank
     FROM household h
     JOIN address a ON ((h.addressid)::text = (a.id)::text)
    WHERE h.isdeleted = false;

  CREATE INDEX IF NOT EXISTS idx_household_address_mv_localitycode
      ON household_address_mv USING btree (localitycode);

  CREATE INDEX IF NOT EXISTS idx_household_address_mv_clientreferenceid
      ON household_address_mv USING btree (clientreferenceid);

  CREATE UNIQUE INDEX IF NOT EXISTS idx_household_address_mv_id
      ON household_address_mv USING btree (id);
