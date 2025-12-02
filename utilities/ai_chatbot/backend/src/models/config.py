"""config.py: Configuration model"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from src.models import db
from src.classes.base import Base
from src.utils.constants import TableNames
from src.models.association_tables import configuration_tag
from error_handler.custom_exception import InvalidParamException

class Configuration(Base, db.Model):
    __tablename__ =  TableNames.CONFIG.value

    id = db.Column('id', db.Integer, primary_key=True)
    name = db.Column('name', db.String(256), unique=True, nullable=False)
    description = db.Column('description', db.Text, unique=False, nullable=True)
    data = db.Column('data', db.Text, nullable=False)
    created_by = db.Column('created_by', db.String(256), nullable=False)
    is_active = db.Column('is_active', db.Boolean, nullable=False, default=True)
    created_at = db.Column('created_at', db.DateTime(timezone=True), nullable=False)
    modified_by = db.Column('modified_by', db.String(256), nullable=True)
    updated_at = db.Column('updated_at', db.DateTime(timezone=True), nullable=True)
    deleted_at = db.Column('deleted_at', db.DateTime(timezone=True), nullable=True)

    tags = db.relationship('Tag', secondary=configuration_tag, back_populates='configurations', uselist=True,
                           lazy="joined")
    examples = db.relationship('Example', back_populates='config', uselist=True, 
                               cascade="all, delete", passive_deletes=True, lazy="joined")
    questions = db.relationship('Question', back_populates='config', uselist=True, 
                               cascade="all, delete", passive_deletes=True, lazy="joined")
    
    def __init__(self, name, description, data, tags, created_by, is_active=True, created_at=None, updated_at=None, deleted_at=None):
        super().__init__(created_by, is_active, created_at, updated_at, deleted_at)
        self.name = name
        self.description = description
        self.data = data
        self.tags = tags
        

    def validate(self):
        if(self.name is None or self.name == ''):
            raise InvalidParamException('configuration name')
        
        if(self.data is None or self.data == ''):
            raise InvalidParamException('configuration data')
        
    
#db.create_all()