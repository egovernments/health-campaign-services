"""create hcm_report_metadata table

Revision ID: 001
Revises: None
Create Date: 2025-12-01 12:00:00

This migration creates the hcm_report_metadata table to track
report uploads to filestore with campaign metadata.
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '001'
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Create hcm_report_metadata table and indexes"""

    # Create the main table
    op.create_table(
        'hcm_report_metadata',

        # Primary key
        sa.Column('id', sa.Integer(), primary_key=True, autoincrement=True),

        # DAG information
        sa.Column('dag_run_id', sa.String(255), nullable=False, comment='Airflow DAG run ID'),
        sa.Column('dag_name', sa.String(100), nullable=False, comment='Name of the DAG that generated the report'),

        # Campaign information
        sa.Column('campaign_name', sa.String(100), nullable=False, comment='Campaign identifier'),
        sa.Column('report_name', sa.String(100), nullable=False, comment='Type of report generated'),
        sa.Column('trigger_frequency', sa.String(50), nullable=False, comment='Report frequency: Daily, Weekly, Monthly'),
        sa.Column('trigger_date', sa.DateTime(), nullable=False, comment='Date when report was triggered'),

        # Filestore information
        sa.Column('filestore_id', sa.String(255), nullable=False, comment='UUID from filestore service'),

        # Unique constraint on filestore_id
        sa.UniqueConstraint('filestore_id', name='uk_hcm_report_filestore_id')
    )

    # Create indexes for common query patterns
    op.create_index(
        'idx_hcm_report_campaign',
        'hcm_report_metadata',
        ['campaign_number']
    )

    op.create_index(
        'idx_hcm_report_name',
        'hcm_report_metadata',
        ['report_name']
    )

    op.create_index(
        'idx_hcm_report_frequency',
        'hcm_report_metadata',
        ['trigger_frequency']
    )

    op.create_index(
        'idx_hcm_report_trigger_date',
        'hcm_report_metadata',
        ['trigger_date']
    )

    # Composite index for common UI query pattern
    op.create_index(
        'idx_hcm_report_campaign_report_freq',
        'hcm_report_metadata',
        ['campaign_number', 'report_name', 'trigger_frequency']
    )


def downgrade() -> None:
    """Drop hcm_report_metadata table and indexes"""

    # Drop indexes first
    op.drop_index('idx_hcm_report_campaign_report_freq', table_name='hcm_report_metadata')
    op.drop_index('idx_hcm_report_trigger_date', table_name='hcm_report_metadata')
    op.drop_index('idx_hcm_report_frequency', table_name='hcm_report_metadata')
    op.drop_index('idx_hcm_report_name', table_name='hcm_report_metadata')
    op.drop_index('idx_hcm_report_campaign', table_name='hcm_report_metadata')

    # Drop table
    op.drop_table('hcm_report_metadata')
