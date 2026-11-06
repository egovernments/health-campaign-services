# Outside Household

{% hint style="info" %}
Outside Household is available for **Polio** campaigns.
{% endhint %}

## Overview

Outside Household lets a campaign deliver outside people's homes. In a Polio campaign, frontline workers vaccinate children where people gather, such as schools and markets, and at transit points, such as bus stations and border crossings. These places are called **sites**.

Sites are added in two places: in the **microplan sheet** during campaign setup, and in the **HCM app** during the campaign. Sites move both ways between the two, as shown in How It Works.

Outside Household can be used on its own or together with House To House delivery.

## Terms

<table><thead><tr><th>Term</th><th>Meaning</th></tr></thead><tbody><tr><td>Site</td><td>A place outside households where the campaign delivers, such as a school or a bus station.</td></tr><tr><td>Site type</td><td>Outside Household Places or Transit Posts.</td></tr><tr><td>Category</td><td>The kind of place within a site type, for example School or Bus station.</td></tr><tr><td>Site status</td><td>Permanent: the site is offered again in later campaigns in the same area. Temporary: used in one campaign only.</td></tr><tr><td>Site usage</td><td>Active: used in this campaign. Inactive: not used in this campaign.</td></tr></tbody></table>

## How It Works

<table><thead><tr><th></th><th>Microplan sheet</th><th>HCM app</th></tr></thead><tbody><tr><td>Who adds sites</td><td>Campaign administrator</td><td>Frontline worker</td></tr><tr><td>When</td><td>During campaign setup, and in a campaign edit</td><td>During the campaign, also without network</td></tr><tr><td>Site types</td><td>Both types. The site status is chosen on each row.</td><td>Sites added in the Community Living Facilities flow are Outside Household Places (Permanent). Sites added in the Transit Post flow are Transit Posts (Temporary).</td></tr><tr><td>Where the site goes</td><td>After the campaign is created, Active sites reach the HCM app.</td><td>When the phone is online, the site is sent to the server and linked to the campaign.</td></tr><tr><td>Next campaign</td><td>Permanent sites are listed again in the next campaign's microplan sheet, as Inactive.</td><td>Same as the microplan sheet.</td></tr></tbody></table>

### 1. Choose Outside Household in the delivery conditions

In the delivery setup of a Polio campaign, choose House To House, Outside Household, or both. When the delivery conditions include **Outside Household**, the microplan sheet gets a fourth sheet named **Outside Household**. Without Outside Household, the microplan sheet has no fourth sheet.

### 2. Fill the Outside Household sheet

Each row of the sheet is one site.

<table><thead><tr><th>Column</th><th>What it does</th></tr></thead><tbody><tr><td>Boundary columns</td><td>Where the site is. Required. It must be at the lowest or second-lowest boundary level of the campaign.</td></tr><tr><td>Site Client Reference Id</td><td>A hidden column that identifies a site that already exists. It is filled on pre-filled rows and empty on new rows. Do not change or delete it.</td></tr><tr><td>Site Type</td><td>Outside Household Places or Transit Posts. Required.</td></tr><tr><td>Category</td><td>The kind of place. Required. The list shows only the categories of the chosen site type.</td></tr><tr><td>Site Name</td><td>The name of the place. Required, 2 to 200 characters.</td></tr><tr><td>Site Status</td><td>Permanent or Temporary. On a new row it is filled from the site type, and it can be changed.</td></tr><tr><td>Site Usage</td><td>Active or Inactive. Only Active sites are used in this campaign.</td></tr><tr><td>Frontline Worker Name</td><td>Optional. The worker at this site, for this campaign only.</td></tr><tr><td>Frontline Worker Phone Number</td><td>Optional. The worker's phone number, in the country's phone number format.</td></tr></tbody></table>

The default site types are:

<table><thead><tr><th>Site type</th><th>Categories</th><th>Default status</th></tr></thead><tbody><tr><td>Outside Household Places</td><td>School, Market, Health post, Community centre, Other</td><td>Permanent</td></tr><tr><td>Transit Posts</td><td>Bus station, Border crossing, Checkpoint, Ferry terminal, Other</td><td>Temporary</td></tr></tbody></table>

The sheet already lists these rows:

* in a campaign edit, the campaign's own sites;
* the permanent sites already saved at the campaign's lowest two boundary levels, from earlier campaigns or from the HCM app. They are listed as **Inactive**, with no frontline worker. Set a row to Active to use that site in this campaign.

Add one new row for each new site.

{% hint style="warning" %}
On a pre-filled row, only Site Usage and the two frontline worker columns can be changed. The other columns are locked. If a locked value is changed, the uploaded file is rejected.
{% endhint %}

### 3. Upload the sheet and create the campaign

The file is checked when it is uploaded. Errors are shown for each row, for example when the boundary is not at the lowest two levels, the category does not belong to the site type, a required value is missing, or a new row repeats a site (same site type, category, boundary and name). Fix the errors and upload the file again.

When the campaign is created:

* every row is saved as a site. A site that already exists is reused, not created again;
* every Active site is linked to the campaign, with its frontline worker;
* Inactive sites are saved, but not linked to the campaign.

### 4. During the campaign: the HCM app

* The app downloads the campaign's Active sites in the worker's area, with the frontline worker details. Inactive sites are not shown.
* Delivery at a site follows the Community Living Facilities and Transit Post flows of the app.
* A worker can add a new site, also without network, as shown in How It Works.

### 5. Edit a created campaign

In an edit, download the current microplan sheet and change the Outside Household sheet:

* add a row to add a new site;
* set a site to Active to link it to the campaign, or to Inactive to remove its link. The site itself is kept for later campaigns;
* change the frontline worker of an Active site.

If Outside Household is removed from the delivery conditions, the site links created from the microplan sheet are removed. The sites are kept. If it is added back, upload the Outside Household sheet again to link the Active sites.

### 6. Change Outside Household before the campaign is created

If Outside Household is added or removed after the microplan sheet was uploaded, a new microplan sheet is made. The users, targets and facilities already uploaded are copied into it. New site rows of the earlier file are not copied. Download the new sheet, check it, and upload it.

### 7. Clone a campaign

A clone of a Polio campaign on the same boundaries starts from the parent campaign's microplan sheet. If the clone adds Outside Household, the Outside Household sheet is added, with the permanent sites of the area. If the clone removes it, that sheet is left out. Sites in the parent's file are linked to the clone again, not created again.

## How a Facility Is Used as a Site

No new service or database table is added. Sites reuse the facility registry.

* **A site is saved as a facility.** The facility's usage holds the site type, its permanent flag holds the site status, and its boundary is the site's boundary. The category is saved with the facility.
* **A site is Active in a campaign when the facility is linked to the campaign's project**, in the same way any facility is linked to a campaign. The frontline worker's name and phone number are saved on this link, so they apply to that campaign only. An Inactive site has no link.
* **Sites stay out of normal facility lists**, such as warehouse, stock and referral screens, because their usage is a site type. Only a search that asks for a site type returns them.

## Configuration

<table><thead><tr><th>Master data</th><th>What to set</th></tr></thead><tbody><tr><td>HCM.OUTSIDE_HOUSEHOLD_TYPES</td><td>The site types, the categories of each type, and whether a new site of each type is Permanent by default.</td></tr><tr><td>HCM-PROJECT-TYPES.projectTypes</td><td>The Polio delivery methods: House To House and Outside Household. The HCM Console shows the choice only when these are set.</td></tr><tr><td>HCM-ADMIN-CONSOLE.schemas</td><td>The column rules of the Outside Household sheet (schema outside-household-microplan-ingestion).</td></tr><tr><td>HCM-ADMIN-CONSOLE.excelIngestionGenerate and excelIngestionProcess</td><td>Add the Outside Household sheet to the microplan sheet and to its upload checks.</td></tr><tr><td>ACCESSCONTROL-ROLEACTIONS</td><td>Allow the field roles to add and update sites, and their campaign links, from the HCM app.</td></tr><tr><td>Localisation, module hcm-admin-schemas</td><td>The sheet name, column names, labels and error messages, for each language.</td></tr></tbody></table>
