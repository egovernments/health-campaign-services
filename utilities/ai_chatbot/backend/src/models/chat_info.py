"""chat_info.py: Chat info model"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from src.models import db
from src.classes.base import Base
from src.utils.constants import TableNames
from sqlalchemy import func
from sqlalchemy.dialects.postgresql import UUID, JSONB, ARRAY, DOUBLE_PRECISION
from sqlalchemy.orm import deferred

class ChatInfo(Base, db.Model):
    __tablename__ =  TableNames.CHAT_INFO.value

    id = db.Column('id', db.Integer, primary_key=True)
    user_id = db.Column('user_id_fk', db.Integer, db.ForeignKey(f'{TableNames.USER_INFO.value}.id', ondelete='CASCADE'), nullable=False)
    config_id = db.Column('config_id_fk', db.Integer, db.ForeignKey(f'{TableNames.CONFIG.value}.id', ondelete='CASCADE'), nullable=False)
    model_config_id = db.Column('model_config_id_fk', db.Integer, db.ForeignKey(f'{TableNames.MODEL_METADATA.value}.id', ondelete='SET NULL'), nullable=True)
    session_id = db.Column('session_id', UUID(as_uuid=True), unique=False, nullable=False)
    question = db.Column('question', db.Text, nullable=False)
    response = db.Column('response', db.Text, nullable=True)
    message = db.Column('message', JSONB, default='{}', nullable=False)
    is_correct = db.Column('is_correct', db.Boolean, nullable=True)
    is_cached = db.Column('is_cached', db.Boolean, default=False, nullable=False)
    embedding = deferred(db.Column('embedding', ARRAY(DOUBLE_PRECISION), nullable=True))
    token_details = db.Column('token_details', db.Text, nullable=True)
    started_at = db.Column('started_at', db.DateTime(timezone=True), nullable=False)
    completed_at = db.Column('completed_at', db.DateTime(timezone=True), nullable=False)
    created_at = db.Column('created_at', db.DateTime(timezone=True), server_default=func.now(), nullable=False)
    modified_by = db.Column('modified_by', db.String(256), nullable=True)
    updated_at = db.Column('updated_at', db.DateTime(timezone=True), nullable=True)
    
    
    def __init__(self, user_id, config_id, model_config_id, session_id, question, response, message, 
                 token_details, started_at, completed_at, created_at):
        self.user_id = user_id
        self.config_id = config_id
        self.model_config_id = model_config_id
        self.session_id = session_id
        self.question = question
        self.response = response
        self.message = message
        self.token_details = token_details
        self.started_at = started_at
        self.completed_at = completed_at
        self.created_at = created_at
        

#db.create_all()