"""question.py: Question model"""
 
__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"
 
from src.models import db
from src.classes.base import Base
from src.utils.constants import TableNames

 
class Question(Base, db.Model):
    __tablename__ =  TableNames.QUESTION.value
 
    id = db.Column('id', db.Integer, primary_key=True)
    detail = db.Column('detail', db.Text, nullable=False)
    config_id = db.Column('config_id_fk', db.Integer, db.ForeignKey(f'{TableNames.CONFIG.value}.id', ondelete='CASCADE'), nullable=False)
    config = db.relationship("Configuration", back_populates="questions")
    
    def __init__(self, detail):
        self.detail = detail