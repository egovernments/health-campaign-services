"""search_result.py: Search result class"""


__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

import json

class SearchResult():

    def __init__(self, ds_id, user_query, sql_query, score=0.0):
        self.ds_id = ds_id
        self.user_query = user_query
        self.sql_query = sql_query
        self.score = score


    def __str__(self):
        return json.dumps({
            "ds_id": self.ds_id,
            "user_query": self.user_query,
            "sql_query": self.sql_query,
            "score" : self.score
        })
    