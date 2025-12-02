"""job_scheduler.py: Job Scheduler"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

import os
from flask_apscheduler import APScheduler
from apscheduler.jobstores.sqlalchemy import SQLAlchemyJobStore
from apscheduler.executors.pool import ThreadPoolExecutor
from src.utils.constants import PlatformConfiguration
from src.helpers.platform_config_helper import get_config_value
from src.repos.chat_history_repo import ChatHistoryRepo
from src.repos.chat_info_repo import ChatInfoRepo
from src.config.logger_config import logger

db_url = os.environ['DATABASE_URL']
JOB_STORE = 'app_db'

jobstores = {
    'app_db': SQLAlchemyJobStore(db_url)
}
executors = {
    'default': ThreadPoolExecutor(20)
}
job_defaults = {
    'coalesce': False,
    'max_instances': 3
}

class Config:
    SCHEDULER_API_ENABLED = False
    SCHEDULER_JOBSTORES=jobstores
    SCHEDULER_EXECUTORS=executors
    SCHEDULER_JOB_DEFAULTS=job_defaults
    #SCHEDULER_TIMEZONE=UTC

#global instance of scheduler
scheduler = APScheduler()

def init_scheduler(app):
    try:
        logger.info("Job Scheduler init - Started.")
        #logger.debug(f"db_url = {db_url}")
        #app.config['SCHEDULER_JOBSTORES'] = jobstores
        app.config.from_object(Config())
        scheduler.init_app(app)
        scheduler.start()
    except Exception as ex:
        logger.error(f"Error in init scheduler. {ex.args}")
    finally:
        logger.info("Job Scheduler init - Completed.")


def config_jobs(app):
    try:
        value = get_config_value(PlatformConfiguration.ChatHistoryCleanupJobSchedule)
        logger.debug(f'ChatHistoryCleanupJobSchedule value = {value}')
        job_id = 'Chat history cleanup schedule job'
        if(value is not None and value > 0):
            scheduler.add_job(jobstore=JOB_STORE, id=job_id, func=chat_history_check_trigger, trigger='interval', 
                seconds=value*60*60, replace_existing=True, max_instances=1)
        else:
            # checking if job is already there
            job = scheduler.get_job(job_id, jobstore=JOB_STORE)
            if(job is not None):
                scheduler.remove_job(job_id, jobstore=JOB_STORE)
    except Exception as ex:
        logger.error(f"Error in configuring scheduled job. {ex.args}")


def chat_history_check_trigger():
    try:
        logger.debug("Entering chat_history_check_trigger.")
        
        #getting all history items which needs to be deleted
        with scheduler.app.app_context():
            days_count = get_config_value(PlatformConfiguration.ChatHistoryRetentionPeriod)
            if(days_count is None or days_count < 0):
                return
            
            # row_count = ChatHistoryRepo().delete_history_by_retention_days(days_count)
            # logger.debug(f"Deleted {row_count} chat history.")
            
            row_count = ChatInfoRepo().delete_chats_by_retention_days(days_count)
            logger.debug(f"Deleted {row_count} chat info.")
    except Exception as ex:
        logger.error(ex.args)
    finally:
        logger.debug("Exiting chat_history_check_trigger.")
