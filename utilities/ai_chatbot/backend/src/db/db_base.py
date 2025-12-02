"""db_base.py: DB base class"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from abc import abstractmethod

class DBBase:
    
    # Create a new conn
    @abstractmethod
    def get_db_connection(self, conn):
        pass
    
    # Test connection
    @abstractmethod
    def test_connection(self, conn):
        pass
    
    @abstractmethod
    def validate_query(self, conn, query):
        pass

    @abstractmethod
    def get_all_table_names(self, data, conn):
        pass

    @abstractmethod
    def get_tables_schema_and_relationships(self, conn, table_schema, table_names:list):
        pass

    @abstractmethod
    def get_metadata(self, data, conn):
        pass
    
    @abstractmethod
    def exec_query_and_read_data_as_json(self, data, conn_data, query):
        pass