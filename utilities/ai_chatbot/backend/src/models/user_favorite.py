"""user_favorite.py: User Favorite model"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from src.models import db
from src.classes.base import Base
from src.utils.constants import TableNames, FavoriteTypes
from sqlalchemy import UniqueConstraint
from error_handler.custom_exception import InvalidParamException

class UserFavorite(Base, db.Model):
    __tablename__ =  TableNames.USER_FAVORITE.value

    id = db.Column('id', db.Integer, primary_key=True)
    name = db.Column('name', db.Text, unique=False, nullable=False)
    description = db.Column('description', db.Text, nullable=False)
    data = db.Column('data', db.Text, nullable=True)
    type = db.Column('type', db.String(256), nullable=False)
    user_id = db.Column('user_id_fk', db.Integer, db.ForeignKey(f'{TableNames.USER_INFO.value}.id', ondelete='CASCADE'), nullable=False)
    created_by = db.Column('created_by', db.String(256), nullable=False)
    created_at = db.Column('created_at', db.DateTime(timezone=True), nullable=False)
    modified_by = db.Column('modified_by', db.String(256), nullable=True)
    updated_at = db.Column('updated_at', db.DateTime(timezone=True), nullable=True)
        
    userinfo = db.relationship('UserInfo', back_populates='favorites')
    
    __table_args__ = (
        UniqueConstraint('name', 'user_id_fk', name='user_fav_name_unique_key'),
    )
    
    def __init__(self, name, description, data, type, user_id, created_by, created_at=None):
        super().__init__(created_by, True, created_at, None, None)
        self.name = name
        self.description = description
        self.data = data
        self.type = type
        self.user_id = user_id
        

    def validate(self):
        if(self.name is None or self.name == ''):
            raise InvalidParamException('favorite name')
        
        if(self.type is None or self.type == '' or (self.type not in [FavoriteTypes.Query.value])):
            raise InvalidParamException('favorite type')
        
        if(self.user_id is None or self.user_id == ''):
            raise InvalidParamException('user id')
        
#db.create_all()