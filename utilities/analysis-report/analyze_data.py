"""
Punjab Data Analysis Module
Analyzes extracted CSV data and generates comprehensive reports
"""

import os
import pandas as pd
import numpy as np
import glob
import logging
import json
import gc
import pytz
from datetime import datetime
from config_loader import ConfigLoader

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def format_size(size_bytes):
    """Format file size in human-readable format"""
    for unit in ['B', 'KB', 'MB', 'GB']:
        if size_bytes < 1024.0:
            return f"{size_bytes:.2f} {unit}"
        size_bytes /= 1024.0
    return f"{size_bytes:.2f} TB"

def get_file_size(file_path):
    """Get file size and return formatted string"""
    try:
        size = os.path.getsize(file_path)
        return size, format_size(size)
    except Exception as e:
        logger.warning(f"Could not get size for {file_path}: {e}")
        return 0, "unknown"

class PunjabDataAnalyzer:
    def __init__(self):
        """Initialize analyzer using ConfigLoader for consistent configuration"""
        # Load configuration from YAML file
        config_path = os.getenv('CONFIG_PATH', '/app/secrets/db_config.yaml')
        environment = os.getenv('ENVIRONMENT', 'development')

        self.config_loader = ConfigLoader(config_path=config_path, environment=environment)

        # Get workflow-specific settings from environment
        self.tenant_id = os.getenv('TENANT_ID', 'pb.adampur')
        self.execution_date = os.getenv('EXECUTION_DATE', datetime.now().strftime('%Y-%m-%d'))

        # Get directories from configuration
        data_dirs = self.config_loader.get_data_dirs()
        self.data_dir = os.getenv('DATA_DIR', data_dirs['data_dir'])
        self.output_dir = os.getenv('OUTPUT_DIR', data_dirs['output_dir'])

        # Get settings (tenant-specific if available)
        self.debug_mode = self.config_loader.get_setting('debug_mode', default=False, tenant_id=self.tenant_id)

        # Create output directory
        os.makedirs(self.output_dir, exist_ok=True)

        self.tenant_name = self.tenant_id.split('.')[-1]
        self.tenant_data_dir = os.path.join(self.data_dir, self.tenant_name)

        logger.info(f"🔧 Initialized analyzer for tenant: {self.tenant_id}")
        logger.info(f"🌍 Environment: {environment}")
        logger.info(f"📁 Data directory: {self.tenant_data_dir}")
        logger.info(f"📊 Output directory: {self.output_dir}")
        logger.info(f"🐛 Debug mode: {self.debug_mode}")

    def load_small_tables(self):
        """Load the three main tables"""
        logger.info("Loading main tables...")

        try:
            # Load property data - filter only ACTIVE properties
            property_file = os.path.join(self.tenant_data_dir, 'eg_pt_property.csv')
            self.df_property = pd.read_csv(property_file,
                usecols=['id', 'propertyid', 'tenantid', 'createdtime', 'additionaldetails',
                        'ownershipcategory', 'status', 'usagecategory', 'propertytype'])
            self.df_property = self.df_property[self.df_property['status'] == 'ACTIVE'].copy()
            logger.info(f"Loaded {len(self.df_property)} active property records")

            # Load owner data - filter only ACTIVE owners
            owner_file = os.path.join(self.tenant_data_dir, 'eg_pt_owner.csv')
            self.df_owner = pd.read_csv(owner_file,
                usecols=['propertyid', 'ownertype', 'status'])
            self.df_owner = self.df_owner[self.df_owner['status'] == 'ACTIVE'].copy()
            logger.info(f"Loaded {len(self.df_owner)} active owner records")

            # Load unit data - load all columns for area calculations
            unit_file = os.path.join(self.tenant_data_dir, 'eg_pt_unit.csv')
            self.df_unit = pd.read_csv(unit_file)
            logger.info(f"Loaded {len(self.df_unit)} unit records")

        except Exception as e:
            logger.error(f"Failed to load main tables: {e}")
            raise

    def load_large_tables(self):
        """Load and combine chunked tables"""
        logger.info("Loading and combining chunked tables...")

        # Load demand data
        demand_dir = os.path.join(self.tenant_data_dir, 'egbs_demand_v1')
        demand_files = glob.glob(os.path.join(demand_dir, 'output_*.csv'))

        if not demand_files:
            logger.warning(f"No demand files found in {demand_dir}. Creating empty demand dataframe.")
            self.df_demand = pd.DataFrame(columns=['id', 'taxperiodfrom', 'taxperiodto', 'consumercode', 'status', 'businessservice'])
            self.df_demanddetail = pd.DataFrame(columns=['demandid', 'taxamount', 'collectionamount', 'taxheadcode'])
            return

        demand_dfs = []
        needed_cols = ['id', 'taxperiodfrom', 'taxperiodto', 'consumercode', 'status', 'businessservice']
        for file in sorted(demand_files):
            logger.info(f"Loading demand file: {file}")
            df_chunk = pd.read_csv(file, usecols=needed_cols)
            demand_dfs.append(df_chunk)

        self.df_demand = pd.concat(demand_dfs, ignore_index=True)
        # Filter only ACTIVE PT demands
        self.df_demand = self.df_demand[self.df_demand['status'] == 'ACTIVE'].copy()
        self.df_demand = self.df_demand[self.df_demand['businessservice'] == 'PT'].copy()
        logger.info(f"Combined {len(demand_files)} demand chunks: {len(self.df_demand)} total active PT records")
        del demand_dfs; gc.collect()

        # Load demand detail data
        detail_dir = os.path.join(self.tenant_data_dir, 'egbs_demanddetail_v1')
        detail_files = glob.glob(os.path.join(detail_dir, 'output_*.csv'))

        if not detail_files:
            logger.warning(f"No demand detail files found in {detail_dir}. Creating empty demand detail dataframe.")
            self.df_demanddetail = pd.DataFrame(columns=['demandid', 'taxamount', 'collectionamount', 'taxheadcode'])
            return

        detail_dfs = []
        needed_cols = ['demandid', 'taxamount', 'collectionamount', 'taxheadcode']
        for file in sorted(detail_files):
            logger.info(f"Loading demand detail file: {file}")
            df_chunk = pd.read_csv(file, usecols=needed_cols)
            detail_dfs.append(df_chunk)

        self.df_demanddetail = pd.concat(detail_dfs, ignore_index=True)
        logger.info(f"Combined {len(detail_files)} detail chunks: {len(self.df_demanddetail)} total records")
        del detail_dfs; gc.collect()

    def prepare_data(self):
        """Clean and prepare data for analysis"""
        logger.info("Preparing data for analysis...")

        # Check if we have demand data
        if self.df_demand.empty or self.df_demanddetail.empty:
            logger.warning("No demand data available. Creating empty joined demand dataframe.")
            self.df_joined_demand = pd.DataFrame(columns=[
                'id', 'taxperiodfrom', 'taxperiodto', 'consumercode', 'status', 'businessservice',
                'demandid', 'taxamount', 'collectionamount', 'taxheadcode', 'fy'
            ])
        else:
            # Merge demand and demand details first
            self.df_joined_demand = pd.merge(
                self.df_demand,
                self.df_demanddetail,
                left_on='id',
                right_on='demandid',
                how='left',
                suffixes=('_demand', '_detail')
            )
            logger.info(f"Merged demand and details: {len(self.df_joined_demand)} records")

            # Convert tax period dates and calculate financial year
            ist = pytz.timezone('Asia/Kolkata')
            self.df_joined_demand['taxperiodfrom'] = pd.to_datetime(
                self.df_joined_demand['taxperiodfrom'], unit='ms', utc=True
            ).dt.tz_convert(ist)
            self.df_joined_demand['taxperiodto'] = pd.to_datetime(
                self.df_joined_demand['taxperiodto'], unit='ms', utc=True
            ).dt.tz_convert(ist)

            # Calculate financial year
            def get_fy(date):
                if pd.isna(date):
                    return None
                if date.month >= 4:
                    fy_start = date.year
                    fy_end = date.year + 1
                else:
                    fy_start = date.year - 1
                    fy_end = date.year
                return f"{fy_start}-{str(fy_end)[-2:]}"

            self.df_joined_demand['fy'] = self.df_joined_demand['taxperiodfrom'].apply(get_fy)

            # Handle missing values in key columns
            numeric_columns = ['taxamount', 'collectionamount']
            for col in numeric_columns:
                if col in self.df_joined_demand.columns:
                    self.df_joined_demand[col] = pd.to_numeric(self.df_joined_demand[col], errors='coerce').fillna(0)

        logger.info("Data preparation completed")

    def perform_analysis(self):
        """Main analysis logic following the exact reference script pattern"""
        logger.info("Starting comprehensive analysis...")

        try:
            # Check if we have any demand data
            if self.df_joined_demand.empty:
                logger.warning("No demand data available. Creating basic property report without financial data.")
                return self._create_basic_property_report()

            # Step 1: Calculate earliest and latest financial years for each consumer (exact as reference)
            result = self.df_joined_demand.groupby('consumercode')['fy'].agg(['min', 'max']).reset_index()
            result.rename(columns={'min': 'earliest_fy', 'max': 'latest_fy'}, inplace=True)

            # Step 2: Calculate latest FY tax amount (exact as reference)
            joined = self.df_joined_demand.merge(
                result[['consumercode', 'latest_fy']],
                on='consumercode',
                how='left'
            )

            # Filter only latest FY
            latest_demand = joined[joined['fy'] == joined['latest_fy']]

            # Pivot taxheadcode values into separate columns
            pivoted = latest_demand.pivot_table(
                index='consumercode',
                columns='taxheadcode',
                values='taxamount',
                aggfunc='sum',
                fill_value=0
            ).reset_index()

            # Apply formula exactly as reference
            pivoted['latest_fy_taxamount'] = (
                pivoted.get('PT_TAX', 0) +
                pivoted.get('PT_CANCER_CESS', 0) +
                pivoted.get('PT_FIRE_CESS', 0) +
                pivoted.get('PT_ROUNDOFF', 0) -
                (pivoted.get('PT_OWNER_EXEMPTION', 0).abs() + pivoted.get('PT_UNIT_USAGE_EXEMPTION', 0).abs())
            )

            # Merge back into result
            result = result.merge(
                pivoted[['consumercode', 'latest_fy_taxamount']],
                on='consumercode',
                how='left'
            )

            # Step 3: Calculate current year (2025-26) tax amount (exact as reference)
            target_fy = "2025-26"
            current_fy_demand = self.df_joined_demand[self.df_joined_demand['fy'] == target_fy]

            # Pivot taxheadcode values into separate columns
            pivoted_current = current_fy_demand.pivot_table(
                index='consumercode',
                columns='taxheadcode',
                values='taxamount',
                aggfunc='sum',
                fill_value=0
            ).reset_index()

            # Apply formula
            pivoted_current['current_fy_taxamount'] = (
                pivoted_current.get('PT_TAX', 0) +
                pivoted_current.get('PT_CANCER_CESS', 0) +
                pivoted_current.get('PT_FIRE_CESS', 0) +
                pivoted_current.get('PT_ROUNDOFF', 0) -
                (pivoted_current.get('PT_OWNER_EXEMPTION', 0).abs() + pivoted_current.get('PT_UNIT_USAGE_EXEMPTION', 0).abs())
            )

            # Keep only required cols
            pivoted_current = pivoted_current[['consumercode', 'current_fy_taxamount']]

            # Ensure all consumercodes are present (exact as reference)
            all_consumercodes = pd.DataFrame(self.df_joined_demand['consumercode'].unique(), columns=['consumercode'])
            final = all_consumercodes.merge(pivoted_current, on='consumercode', how='left')
            final['current_fy_taxamount'] = final['current_fy_taxamount'].fillna(0)

            # Merge into result
            result = result.merge(final, on='consumercode', how='left')
            result['current_fy_taxamount'] = result['current_fy_taxamount'].fillna(0)

            # Step 4: Calculate arrear years demand (exact as reference)
            arrear_demand = self.df_joined_demand[self.df_joined_demand['fy'] < target_fy]

            agg = arrear_demand.groupby('consumercode').agg(
                arrear_taxamount_sum=('taxamount', 'sum'),
                arrear_collectionamount_sum=('collectionamount', 'sum')
            ).reset_index()

            agg['arrear_years_demand_generated'] = (
                agg['arrear_taxamount_sum'] - agg['arrear_collectionamount_sum']
            )

            result = result.merge(
                agg[['consumercode', 'arrear_years_demand_generated']],
                on='consumercode', how='left'
            )
            result['arrear_years_demand_generated'] = result['arrear_years_demand_generated'].fillna(0)

            # Step 5: Calculate penalty and interest (exact as reference)
            relevant_codes = ['PT_TIME_PENALTY', 'PT_TIME_INTEREST']
            filtered = self.df_joined_demand[self.df_joined_demand['taxheadcode'].isin(relevant_codes)]

            grouped = (
                filtered.groupby(['consumercode', 'taxheadcode'])['taxamount']
                .sum()
                .unstack(fill_value=0)  # Puts taxheadcodes as columns, fills missing with 0
                .reset_index()
            )

            # Ensure both columns exist
            for col in relevant_codes:
                if col not in grouped.columns:
                    grouped[col] = 0

            grouped = grouped[['consumercode', 'PT_TIME_PENALTY', 'PT_TIME_INTEREST']]
            grouped = grouped.fillna(0)

            result = result.merge(grouped, on='consumercode', how='left')
            result[['PT_TIME_PENALTY', 'PT_TIME_INTEREST']] = result[['PT_TIME_PENALTY', 'PT_TIME_INTEREST']].fillna(0)

            # Step 6: Merge properties and units first (exact as reference)
            merged = self.df_property.merge(self.df_unit, left_on='id', right_on='propertyid', suffixes=('_property', '_unit'))

            def classify_ownership(occupancies):
                unique_types = set(occupancies)
                if 'RENTED' in unique_types:
                    if len(unique_types) > 1:
                        return 'Mixed'
                    else:
                        return 'Tenant'
                if 'SELFOCCUPIED' in unique_types:
                    # If only SELFOCCUPIED or SELFOCCUPIED + UNOCCUPIED
                    return 'Owner'
                if 'UNOCCUPIED' in unique_types:
                    return 'Owner'
                # fallback
                return None

            # Find occupancytypes per property id (exact as reference)
            ownership = (
                merged.groupby('propertyid_property')['occupancytype']
                .apply(classify_ownership)
                .reset_index()
                .rename(columns={'occupancytype': 'Owned_Rented'})
            )

            property_df = self.df_property.merge(ownership, left_on='propertyid', right_on='propertyid_property', how='left')

            # Step 7: Calculate area summary (exact as reference)
            def clean_numeric(series):
                # Replace 'NULL' strings and NaNs with 0, then convert to float
                return pd.to_numeric(series.replace('NULL', 0), errors='coerce').fillna(0)

            merged['builtuparea'] = clean_numeric(merged['builtuparea'])
            merged['plintharea'] = clean_numeric(merged['plintharea'])

            area_summary = (
                merged.groupby('propertyid_property', as_index=False)
                .agg(
                    total_builtup_area=('builtuparea', 'sum'),
                    total_plinth_area=('plintharea', 'sum')
                )
            )

            property_df = property_df.merge(area_summary, left_on='propertyid', right_on='propertyid_property', how='left')
            property_df['total_builtup_area'] = property_df['total_builtup_area'].fillna(0)
            property_df['total_plinth_area'] = property_df['total_plinth_area'].fillna(0)

            # Step 8: Merge with financial result (exact as reference)
            property_result_merged = property_df.merge(
                result,
                left_on='propertyid',
                right_on='consumercode',
                how='left'
            )

            # Step 9: Add exemption status (exact as reference)
            self.df_owner['is_exempted'] = self.df_owner['ownertype'].isin(['WIDOW', 'FREEDOMFIGHTER'])
            exempted_status = self.df_owner.groupby('propertyid')['is_exempted'].any().reset_index()
            exempted_status['Is Property Exempted [Yes/ No]'] = exempted_status['is_exempted'].apply(lambda x: 'Yes' if x else 'No')
            exempted_status = exempted_status.drop(columns=['is_exempted'])

            # Add exemption column to the merged result (exact as reference)
            property_result_merged = property_result_merged.merge(
                exempted_status[['propertyid', 'Is Property Exempted [Yes/ No]']],
                left_on='id',  # property_df.id == eg_pt_owner.propertyid
                right_on='propertyid',
                how='left'
            )

            property_result_merged['Is Property Exempted [Yes/ No]'] = property_result_merged['Is Property Exempted [Yes/ No]'].fillna('No')

            # Drop duplicate merge key (exact as reference)
            if 'propertyid' in property_result_merged.columns:
                property_result_merged.drop(columns=['propertyid'], inplace=True)

            # If 'propertyid_x' exists, use it as the correct property ID (exact as reference)
            if 'propertyid_x' in property_result_merged.columns:
                property_result_merged['propertyid'] = property_result_merged['propertyid_x']

            # Step 10: Rename columns for the final report (exact as reference)
            report = property_result_merged.rename(columns={
                'tenantid': 'ULB',
                'propertyid': 'Property ID',
                'usagecategory': 'Usage',
                'createdtime': 'Date of Creation of the Property in the System',
                'additionaldetails': 'Date of Construction of the Property',
                'ownershipcategory': 'Ownership Type',
                'Is Property Exempted [Yes/ No]': 'Is Property Exempted [Yes/ No]',
                'Owned_Rented': 'Owned_Rented (Owner/ Rented/ Mixed)',
                'earliest_fy': 'Earliest Financial Year for which Demand was Generated',
                'latest_fy': 'Latest Financial Year for which Demand was Generated',
                'latest_fy_taxamount': 'Latest Demand Generated [in Rs.]',
                'current_fy_taxamount': 'Current Years Demand Generated [in Rs.]',
                'PT_TIME_PENALTY': 'Penalty',
                'PT_TIME_INTEREST': 'Interest',
                'arrear_years_demand_generated': 'Arrear Years Demand Generated [in Rs.]',
                'propertytype': 'Property Type[Building/ Vacant]',
                'total_builtup_area': 'Total Builtup Area [Sum of all units/ floors]',
                'total_plinth_area': 'Total Plinth Area [Sum of all units/ floors]'
            }).copy()

            # Step 11: Format ULB and date fields (exact as reference)
            def epoch_to_custom_date(epoch_ms):
                return datetime.fromtimestamp(epoch_ms / 1000).strftime('%d-%b-%Y') if pd.notna(epoch_ms) else None

            def get_year_construction(val):
                if pd.isna(val): return None
                try: return json.loads(val).get('yearConstruction')
                except: return None

            report['ULB'] = report['ULB'].str.split('.').str[1].str.capitalize()
            report['Date of Creation of the Property in the System'] = report['Date of Creation of the Property in the System'].apply(epoch_to_custom_date)
            report['Date of Construction of the Property'] = report['Date of Construction of the Property'].apply(get_year_construction)

            # Step 12: Select final columns in required order (exact as reference)
            final_report = report[
                [
                    'ULB',
                    'Property ID',
                    'Usage',
                    'Date of Creation of the Property in the System',
                    'Date of Construction of the Property',
                    'Ownership Type',
                    'Is Property Exempted [Yes/ No]',
                    'Owned_Rented (Owner/ Rented/ Mixed)',
                    'Earliest Financial Year for which Demand was Generated',
                    'Latest Financial Year for which Demand was Generated',
                    'Latest Demand Generated [in Rs.]',
                    'Current Years Demand Generated [in Rs.]',
                    'Penalty',
                    'Interest',
                    'Arrear Years Demand Generated [in Rs.]',
                    'Property Type[Building/ Vacant]',
                    'Total Builtup Area [Sum of all units/ floors]',
                    'Total Plinth Area [Sum of all units/ floors]'
                ]
            ].copy()

            logger.info(f"Analysis completed. Generated report with {len(final_report)} properties")
            return final_report

        except Exception as e:
            logger.error(f"Analysis failed: {e}")
            raise

    def _create_basic_property_report(self):
        """Create a basic property report without financial data"""
        logger.info("Creating basic property report without financial data...")

        # Step 1: Calculate owner/rented classification and areas
        def classify_ownership(occupancies):
            unique_types = set(occupancies)
            if 'RENTED' in unique_types:
                if len(unique_types) > 1:
                    return 'Mixed'
                else:
                    return 'Tenant'
            if 'SELFOCCUPIED' in unique_types:
                return 'Owner'
            if 'UNOCCUPIED' in unique_types:
                return 'Owner'
            return None

        # Merge property with units
        merged_prop_unit = pd.merge(
            self.df_property,
            self.df_unit,
            left_on='id',
            right_on='propertyid',
            suffixes=('_property', '_unit')
        )

        # Calculate ownership classification
        ownership = (
            merged_prop_unit.groupby('propertyid_property')['occupancytype']
            .apply(classify_ownership)
            .reset_index()
            .rename(columns={'occupancytype': 'Owned_Rented'})
        )

        # Clean numeric fields for area calculation
        def clean_numeric(series):
            return pd.to_numeric(series.replace('NULL', 0), errors='coerce').fillna(0)

        merged_prop_unit['builtuparea'] = clean_numeric(merged_prop_unit['builtuparea'])
        merged_prop_unit['plintharea'] = clean_numeric(merged_prop_unit['plintharea'])

        # Calculate area summary
        area_summary = (
            merged_prop_unit.groupby('propertyid_property', as_index=False)
            .agg(
                total_builtup_area=('builtuparea', 'sum'),
                total_plinth_area=('plintharea', 'sum')
            )
        )

        # Merge all property data
        property_complete = pd.merge(self.df_property, ownership, left_on='propertyid', right_on='propertyid_property', how='left')
        property_complete = pd.merge(property_complete, area_summary, left_on='propertyid', right_on='propertyid_property', how='left')
        property_complete['total_builtup_area'] = property_complete['total_builtup_area'].fillna(0)
        property_complete['total_plinth_area'] = property_complete['total_plinth_area'].fillna(0)

        # Step 2: Calculate exemption status
        self.df_owner['is_exempted'] = self.df_owner['ownertype'].isin(['WIDOW', 'FREEDOMFIGHTER'])
        exempted_status = self.df_owner.groupby('propertyid')['is_exempted'].any().reset_index()
        exempted_status['Is Property Exempted [Yes/ No]'] = exempted_status['is_exempted'].apply(lambda x: 'Yes' if x else 'No')

        # Step 3: Merge with exemption status
        property_result_merged = pd.merge(
            property_complete,
            exempted_status[['propertyid', 'Is Property Exempted [Yes/ No]']],
            left_on='id',
            right_on='propertyid',
            how='left'
        )

        property_result_merged['Is Property Exempted [Yes/ No]'] = property_result_merged['Is Property Exempted [Yes/ No]'].fillna('No')

        # Add empty financial columns
        property_result_merged['earliest_fy'] = ''
        property_result_merged['latest_fy'] = ''
        property_result_merged['latest_fy_taxamount'] = 0
        property_result_merged['current_fy_taxamount'] = 0
        property_result_merged['PT_TIME_PENALTY'] = 0
        property_result_merged['PT_TIME_INTEREST'] = 0
        property_result_merged['arrear_years_demand_generated'] = 0

        # Step 4: Format and rename final report columns
        def epoch_to_custom_date(epoch_ms):
            if pd.isna(epoch_ms):
                return None
            try:
                return datetime.fromtimestamp(epoch_ms / 1000).strftime('%d-%b-%Y')
            except:
                return None

        def get_year_construction(val):
            if pd.isna(val):
                return None
            try:
                return json.loads(val).get('yearConstruction')
            except:
                return None

        # Format date and other fields
        property_result_merged['tenantid'] = property_result_merged['tenantid'].str.split('.').str[1].str.capitalize()
        property_result_merged['createdtime'] = property_result_merged['createdtime'].apply(epoch_to_custom_date)
        property_result_merged['additionaldetails'] = property_result_merged['additionaldetails'].apply(get_year_construction)

        # Final column mapping
        final_report = property_result_merged.rename(columns={
            'tenantid': 'ULB',
            'propertyid': 'Property ID',
            'usagecategory': 'Usage',
            'createdtime': 'Date of Creation of the Property in the System',
            'additionaldetails': 'Date of Construction of the Property',
            'ownershipcategory': 'Ownership Type',
            'Is Property Exempted [Yes/ No]': 'Is Property Exempted [Yes/ No]',
            'Owned_Rented': 'Owned_Rented (Owner/ Rented/ Mixed)',
            'earliest_fy': 'Earliest Financial Year for which Demand was Generated',
            'latest_fy': 'Latest Financial Year for which Demand was Generated',
            'latest_fy_taxamount': 'Latest Demand Generated [in Rs.]',
            'current_fy_taxamount': 'Current Years Demand Generated [in Rs.]',
            'PT_TIME_PENALTY': 'Penalty',
            'PT_TIME_INTEREST': 'Interest',
            'arrear_years_demand_generated': 'Arrear Years Demand Generated [in Rs.]',
            'propertytype': 'Property Type[Building/ Vacant]',
            'total_builtup_area': 'Total Builtup Area [Sum of all units/ floors]',
            'total_plinth_area': 'Total Plinth Area [Sum of all units/ floors]'
        })

        # Select final columns in required order
        final_columns = [
            'ULB',
            'Property ID',
            'Usage',
            'Date of Creation of the Property in the System',
            'Date of Construction of the Property',
            'Ownership Type',
            'Is Property Exempted [Yes/ No]',
            'Owned_Rented (Owner/ Rented/ Mixed)',
            'Earliest Financial Year for which Demand was Generated',
            'Latest Financial Year for which Demand was Generated',
            'Latest Demand Generated [in Rs.]',
            'Current Years Demand Generated [in Rs.]',
            'Penalty',
            'Interest',
            'Arrear Years Demand Generated [in Rs.]',
            'Property Type[Building/ Vacant]',
            'Total Builtup Area [Sum of all units/ floors]',
            'Total Plinth Area [Sum of all units/ floors]'
        ]

        df_final = final_report[[col for col in final_columns if col in final_report.columns]].copy()

        logger.info(f"Basic property report completed. Generated report with {len(df_final)} properties (no financial data)")
        return df_final

    def save_report(self, df_result):
        """Save analysis results to XLSX file"""
        if df_result.empty:
            logger.warning("No data to save")
            return

        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        filename = f"Punjab_Data_Analysis_{self.tenant_name}_{timestamp}.xlsx"
        output_path = os.path.join(self.output_dir, filename)

        df_result.to_excel(output_path, index=False, engine='openpyxl')

        # Log file size
        file_size_bytes, file_size_str = get_file_size(output_path)
        logger.info("")
        logger.info("=" * 70)
        logger.info(f"📊 ANALYSIS COMPLETE - Output file created")
        logger.info(f"📁 File: {filename}")
        logger.info(f"📦 Size: {file_size_str}")
        logger.info(f"📍 Location: {output_path}")
        logger.info(f"📈 Records: {len(df_result):,} properties")
        logger.info("=" * 70)

        if self.debug_mode:
            logger.info(f"Report preview:\n{df_result.head()}")
            logger.info(f"Report shape: {df_result.shape}")
            logger.info(f"Report columns: {list(df_result.columns)}")

    def run_analysis(self):
        """Main analysis workflow"""
        logger.info(f"Starting analysis for tenant: {self.tenant_id}")

        try:
            # Load all data
            self.load_small_tables()
            self.load_large_tables()

            # Prepare data
            self.prepare_data()

            # Perform analysis
            df_result = self.perform_analysis()

            # Save results
            self.save_report(df_result)

            logger.info("Analysis completed successfully")

        except Exception as e:
            logger.error(f"Analysis workflow failed: {e}")
            raise

if __name__ == "__main__":
    analyzer = PunjabDataAnalyzer()
    analyzer.run_analysis()