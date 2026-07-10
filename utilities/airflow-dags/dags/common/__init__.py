"""Shared helpers for the property-tax Airflow pipelines.

This package is imported by DAG modules (e.g. property_tax_raw_to_silver) and by
task modules such as payment_backupdate. It defines NO DAGs itself — the sibling
.airflowignore keeps the Airflow DAG processor from scanning this folder.
"""
