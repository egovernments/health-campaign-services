"""base_auth_provider.py: Base auth provider class """


__author__ = "Umesh"
__copyright__ = "Copyright 2023, Prescience Decision Solutions"

from abc import abstractmethod
from enum import Enum

class UserRoles(Enum):
    USER='Chatbot User'
    ADMIN='Chatbot Admin'


# base class for auth
class AuthProvider():

    @abstractmethod
    def get_token(self, user_name, password):
        pass

    @abstractmethod
    def refresh_token(self, token:str):
        pass
    
    @abstractmethod
    def check_token(self, token:str):
        pass