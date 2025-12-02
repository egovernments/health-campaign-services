"""__init__.py: This is used in service.init to configure DB """


__author__ = "BG"
__copyright__ = "Copyright 2022, Prescience Decision Solutions"

from flask_sqlalchemy import SQLAlchemy
db = SQLAlchemy()

# from src.models import *
from src.models.product_info import ProductInfo
from src.models.tag import Tag
from src.models.connection_info import ConnectionInfo
from src.models.config import Configuration
from src.models.model_info import LLMModelInfo
from src.models.platform_config import PlatformConfiguration
from src.models.user_info import UserInfo
from src.models.user_favorite import UserFavorite
from src.models.chat_info import ChatInfo
from src.models.chat_history import ChatHistory
from src.models.example import Example
from src.models.macro import Macro
from src.models.question import Question