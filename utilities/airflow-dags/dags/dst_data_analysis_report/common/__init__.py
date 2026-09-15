"""common - the Airflow-aware half of dst_data_analysis_report.

Config resolution, deployment groups, slot matching, run history, alerts and
the stage chain. This is the layer that knows about Airflow; `pipeline` does
not, and the dependency runs one way (common -> pipeline, never the reverse) so
the report can still run standalone.
"""
