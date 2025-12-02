"""model_info.py: Model info model"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

import json
from src.models import db
from src.classes.base import Base
from src.utils.constants import TableNames
from src.utils.constants import SupportedLLM
from src.utils.util import base64_to_string
from error_handler.custom_exception import InvalidParamException, UnsupportedModelException

class LLMModelInfo(Base, db.Model):
    __tablename__ =  TableNames.MODEL_METADATA.value

    id = db.Column('id', db.Integer, primary_key=True)
    name = db.Column('name', db.String(256), unique=True, nullable=False)
    description = db.Column('description', db.Text, nullable=True)
    type = db.Column('type', db.String(256), nullable=False)
    data = db.Column('data', db.Text, nullable=False)
    status = db.Column('status', db.String(128), nullable=False)
    is_default = db.Column('is_default', db.Boolean, nullable=False, default=True)
    created_by = db.Column('created_by', db.String(256), nullable=False)
    is_active = db.Column('is_active', db.Boolean, nullable=False, default=True)
    created_at = db.Column('created_at', db.DateTime(timezone=True), nullable=False)
    modified_by = db.Column('modified_by', db.String(256), nullable=True)
    updated_at = db.Column('updated_at', db.DateTime(timezone=True), nullable=True)
    deleted_at = db.Column('deleted_at', db.DateTime(timezone=True), nullable=True)

    def __init__(self, name, type, description, data, status, is_default, created_by, is_active=True, created_at=None, updated_at=None, deleted_at=None):

        super().__init__(created_by, is_active, created_at, updated_at, deleted_at)
        self.name = name
        self.type = type
        self.description = description
        self.data = data
        self.status = status
        self.is_default = is_default
        

    def validate(self):
        if(self.name is None or self.name == ''):
            raise InvalidParamException('name')
        
        if(self.data is None or self.data == ''):
            raise InvalidParamException('configuration data')
        
        if (self.type not in [e.value for e in SupportedLLM]):
            raise UnsupportedModelException
        
        settings = base64_to_string(self.data)
        settings = json.loads(settings)
        # checking only mandatory params
        if(self.type == SupportedLLM.AzureOpenAI.value):
            azure_openai_endpoint = settings['azure_openai_endpoint']
            azure_openai_api_key = settings['azure_openai_api_key']
            azure_deployment_model = settings['azure_deployment_model']
            azure_openai_api_version = settings['azure_openai_api_version']
        elif(self.type == SupportedLLM.Gemini.value):
            api_key = settings['api_key']
            model_name = settings['model_name']
        elif(self.type == SupportedLLM.OpenAI.value):
            api_key = settings['api_key']
            model_name = settings['model_name']
        else:
            raise UnsupportedModelException


    def get_settings(self):
        if(self.data is None or self.data == ''):
            raise InvalidParamException('configuration data')
        
        settings = base64_to_string(self.data)
        return json.loads(settings)

#db.create_all()