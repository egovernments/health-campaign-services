"""user_info.py: User info model"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from src.models import db
from src.classes.base import Base
from src.utils.constants import TableNames
from src.models.association_tables import user_tag
from error_handler.custom_exception import InvalidParamException


class UserInfo(Base, db.Model):
    __tablename__ =  TableNames.USER_INFO.value

    id = db.Column('id', db.Integer, primary_key=True)
    guid = db.Column('guid', db.String(36), unique=True, nullable=False)
    user_name = db.Column('user_name', db.String(256), unique=True, nullable=False)
    is_active = db.Column('is_active', db.Boolean, nullable=False, default=True)
    created_by = db.Column('created_by', db.String(256), nullable=False)
    created_at = db.Column('created_at', db.DateTime(timezone=True), nullable=False)
    modified_by = db.Column('modified_by', db.String(256), nullable=True)
    updated_at = db.Column('updated_at', db.DateTime(timezone=True), nullable=True)
    deleted_at = db.Column('deleted_at', db.DateTime(timezone=True), nullable=True)
    
    tags = db.relationship('Tag', secondary=user_tag, back_populates='userinfos', uselist=True, 
                           lazy="joined")
    favorites = db.relationship('UserFavorite', back_populates='userinfo', uselist=True, 
                           lazy="joined")
    
    def __init__(self, guid, user_name, tags, is_active, created_by, created_at):
        self.created_by = created_by
        self.created_at = created_at
        self.guid = guid
        self.user_name = user_name
        self.tags = tags
        self.is_active = is_active


    def validate(self):
        if(self.guid is None or self.guid == ''):
            raise InvalidParamException('guid')
        
        if(self.user_name is None or self.user_name == ''):
            raise InvalidParamException('user_name')
    
#db.create_all()