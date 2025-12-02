"""connection_info.py: Connection information model"""


__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

from src.models import db
from src.classes.base import Base
from src.utils.constants import TableNames
from error_handler.custom_exception import InvalidParamException


class ConnectionInfo(Base, db.Model):
    __tablename__ =  TableNames.CONNECTION.value

    id = db.Column('id', db.Integer, primary_key=True)
    name = db.Column('name', db.String(256), unique=True, nullable=False)
    description = db.Column('description', db.String(512), nullable=True)
    type = db.Column('type', db.String(256), nullable=False)
    data = db.Column('data', db.Text, nullable=False)
    tested_at = db.Column('tested_at', db.DateTime(timezone=True), nullable=True)
    test_status = db.Column('test_status', db.Text, nullable=True)
    created_by = db.Column('created_by', db.String(256), nullable=False)
    is_active = db.Column('is_active', db.Boolean, default=True, nullable=False)
    created_at = db.Column('created_at', db.DateTime(timezone=True), nullable=False)
    modified_by = db.Column('modified_by', db.String(256), nullable=True)
    updated_at = db.Column('updated_at', db.DateTime(timezone=True), nullable=True)
    deleted_at = db.Column('deleted_at', db.DateTime(timezone=True), nullable=True)
                      
    def __init__(self, name, description, type, data, tested_at, test_status, created_by, is_active, created_at=None, updated_at=None, deleted_at=None):
        super().__init__(created_by, is_active, created_at, updated_at, deleted_at)
        self.name = name
        self.description = description
        self.type = type
        self.data = data
        self.tested_at = tested_at
        self.test_status = test_status


    def validate(self) :
        if((self.name is None) or (self.name == '') or (self.type is None) or (self.type == '') or (self.data is None) or (self.data == '')) :
            raise InvalidParamException

        return True
        
#db.create_all()