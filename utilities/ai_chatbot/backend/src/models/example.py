"""example.py: Example model"""
 
__author__ = "Praful"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

from src.models import db
from src.classes.base import Base
from src.utils.constants import TableNames
from sqlalchemy.dialects.postgresql import ARRAY, DOUBLE_PRECISION
from sqlalchemy.orm import deferred
 
class Example(Base, db.Model):
    __tablename__ =  TableNames.EXAMPLE.value
 
    id = db.Column('id', db.Integer, primary_key=True)
    type = db.Column('type', db.Text, nullable=True)
    key = db.Column('key', db.Text, nullable=False)
    value = db.Column('value', db.Text, nullable=True)
    embedding = deferred(db.Column('embedding', ARRAY(DOUBLE_PRECISION), nullable=True))
    config_id = db.Column('config_id_fk', db.Integer, db.ForeignKey(f'{TableNames.CONFIG.value}.id', ondelete='CASCADE'), nullable=False)
    config = db.relationship("Configuration", back_populates="examples")
    
    def __init__(self, key, value, type):
        self.key = key
        self.value = value
        self.type = type