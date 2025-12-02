"""platform_config.py: Platform configuration model"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from src.models import db
from src.classes.base import Base
from src.utils.constants import TableNames


class PlatformConfiguration(Base, db.Model):
    __tablename__ =  TableNames.PLATFORM_CONFIG.value

    id = db.Column('id', db.Integer, primary_key=True)
    name = db.Column('name', db.String(256), unique=True, nullable=False)
    display_name = db.Column('display_name', db.String(256), nullable=False)
    help_msg = db.Column('help_msg', db.String(512), nullable=True)
    type = db.Column('type', db.String(128), nullable=False)
    value = db.Column('value', db.Text, nullable=True)
    readonly = db.Column('readonly', db.Boolean, nullable=False, default=False)
    created_by = db.Column('created_by', db.String(256), nullable=False)
    is_active = db.Column('is_active', db.Boolean, nullable=False, default=True)
    created_at = db.Column('created_at', db.DateTime(timezone=True), nullable=False)
    modified_by = db.Column('modified_by', db.String(256), nullable=True)
    updated_at = db.Column('updated_at', db.DateTime(timezone=True), nullable=True)
    deleted_at = db.Column('deleted_at', db.DateTime(timezone=True), nullable=True)

    def __init__(self, name, display_name, type, value, readonly, created_by, is_active=True, created_at=None, updated_at=None, deleted_at=None):
        super().__init__(created_by, is_active, created_at, updated_at, deleted_at)
        self.name = name
        self.display_name = display_name
        self.type = type
        self.value = value
        self.readonly = readonly

#db.create_all()