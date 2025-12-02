"""macro.py: Macro model"""

__author__ = "Praful Nigam"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

from src.models import db
from src.classes.base import Base
from src.utils.constants import TableNames

class Macro(Base, db.Model):
    __tablename__ =  TableNames.MACRO.value

    id = db.Column('id', db.Integer, primary_key=True)
    name = db.Column('name', db.String(256), nullable=False)
    description = db.Column('description', db.Text, nullable=False)
    
    def __init__(self, name, description):
        self.name = name
        self.description = description