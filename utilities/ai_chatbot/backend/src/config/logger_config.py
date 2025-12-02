"""logger_config.py: logger package"""

__author__ = "Umesh"
__copyright__ = "Copyright 2023, Prescience Decision Solutions"

import logging
from logging.handlers import RotatingFileHandler

# logging.basicConfig(filename='app.log', level=logging.DEBUG)  # added the logging file for backend
# logging.basicConfig(level=logging.DEBUG, format='%(asctime)s - %(levelname)s - %(message)s',
#                     datefmt='%Y-%m-%d %H:%M:%S')
# logger = logging.getLogger(__name__)

# to enable file logger
log_format = '[%(asctime)s] [%(levelname)s] [%(filename)s : %(funcName)s : %(lineno)d]  %(message)s'
log_level = logging.DEBUG

logger = logging.getLogger('app_logger')
logger.setLevel(log_level)

# Create file handler and set level to debug
#file_handler = logging.FileHandler('/app/data/chatbot.log')
file_handler = RotatingFileHandler('/app/data/chatbot.log', maxBytes=5*1024*1024, backupCount=3)
file_handler.setLevel(log_level)
file_handler.setFormatter(logging.Formatter(log_format))

logger.addHandler(file_handler)