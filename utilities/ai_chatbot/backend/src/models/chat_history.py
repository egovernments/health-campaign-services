"""chat_history.py: Chat histoty model"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from src.models import db
from src.classes.base import Base
from src.utils.constants import TableNames
from sqlalchemy import func
from sqlalchemy.dialects.postgresql import UUID, JSONB

class ChatHistory(Base, db.Model):
    __tablename__ =  TableNames.CHAT_HISTORY.value

    id = db.Column('id', db.Integer, primary_key=True)
    session_id = db.Column('session_id', UUID(as_uuid=True), unique=False, nullable=False)
    message = db.Column('message', JSONB, nullable=False)
    created_at = db.Column('created_at', db.DateTime(timezone=True), server_default=func.now(), nullable=False)
    
    def __init__(self, session_id, message, created_at):
        self.session_id = session_id
        self.message = message
        self.created_at = created_at
        

#db.create_all()