"""dashboard.py: Dashboard info class"""


__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

import json
from src.classes.base import Base

class DashboardInfo(Base):

    def __init__(self, id, name, embed_guid):
        self.id = id
        self.name = name
        self.embed_guid = embed_guid
    
    def __repr__(self):
        return f"Dashboard(id={self.id}, name='{self.name}, guid='{self.embed_guid}')"

    def __str__(self):
        return json.dumps({
            "id": self.id,
            "name": self.name,
            "embed_guid" : self.embed_guid
        })
    