"""dst_data_analysis_report - the DST campaign reporting code, in two layers.

    common/     Airflow-aware orchestration: config resolution, deployment
                groups, slot matching, run history, alerts, the stage chain.
    pipeline/   the report itself: ES extract, Excels, Word, Drive, Slack.
                Imports NO Airflow, so it still runs standalone.
    platform/   DevOps artifacts for mdms mode: the dst_report_metadata table
                and the egov-persister config that fills it.

The dependency runs one way - common imports pipeline, never the reverse.

Listed in .airflowignore: this is imported BY the DAGs, it is not a DAG, and
the scheduler should not parse it.
"""
