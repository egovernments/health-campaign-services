"""tag.py: Tag model"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from src.models import db
from src.classes.base import Base
from src.utils.constants import TableNames
from src.models.association_tables import configuration_tag, user_tag
from error_handler.custom_exception import InvalidParamException


class Tag(Base, db.Model):
    __tablename__ =  TableNames.TAG.value

    id = db.Column('id', db.Integer, primary_key=True)
    name = db.Column('name', db.String(256), unique=True, nullable=False)
    description = db.Column('description', db.Text, nullable=False)
    created_by = db.Column('created_by', db.String(256), nullable=False)
    is_active = db.Column('is_active', db.Boolean, nullable=False, default=True)
    created_at = db.Column('created_at', db.DateTime(timezone=True), nullable=False)
    modified_by = db.Column('modified_by', db.String(256), nullable=True)
    updated_at = db.Column('updated_at', db.DateTime(timezone=True), nullable=True)
    deleted_at = db.Column('deleted_at', db.DateTime(timezone=True), nullable=True)
    
    configurations = db.relationship('Configuration', secondary=configuration_tag, back_populates='tags')
    userinfos = db.relationship("UserInfo", secondary=user_tag, back_populates='tags')
    
    def __init__(self, name, description, created_by, is_active=True, created_at=None):
        super().__init__(created_by, is_active, created_at, None, None)
        self.name = name
        self.description = description
        

    def validate(self):
        if(self.name is None or self.name == ''):
            raise InvalidParamException('tag name')
        
    
#db.create_all()