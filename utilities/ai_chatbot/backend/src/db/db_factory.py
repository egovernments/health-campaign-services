"""db_factory.py: DB factory method"""

__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

from src.utils.constants import SupportedDBType
from src.db.elasticsearch_db import ESDBWrapper

def get_db_factory(db_type):
    if(db_type == SupportedDBType.Elasticsearch.name):
        return ESDBWrapper()
        
    return None
    