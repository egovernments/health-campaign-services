"""llm_base.py: LLM model base class"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"


import abc
from abc import abstractmethod
from src.classes.token_info import TokenInfo
from src.utils.constants import SupportedLLM
from error_handler.custom_exception import LLMSettingsException
from src.config.logger_config import logger

# Note
# Modify below to add a new LLM support

LLM_TO_SETTINGS_MAPPING = {
    SupportedLLM.OpenAI.value : {
        "api_key": "",
        "model_name": "",
        "per_input_token_cost" : 0.0,
        "per_output_token_cost" : 0.0
    },

    SupportedLLM.AzureOpenAI.value : {
        "azure_openai_endpoint": "",
        "azure_openai_api_key": "",
        "azure_deployment_model":"",
        "azure_openai_api_version": "",
        "per_input_token_cost" : 0.0,
        "per_output_token_cost" : 0.0
    },

    SupportedLLM.Gemini.value : {
        "api_key" : "",
        "model_name" : "",
        "per_input_token_cost" : 0.0,
        "per_output_token_cost" : 0.0
    }
}


# base class for all LLM settings
class LLMSettings():
    def __init__(self, per_input_token_cost, per_output_token_cost):
        self.per_input_token_cost = float(per_input_token_cost)
        self.per_output_token_cost = float(per_output_token_cost)

# Note
# Modify below to add a new LLM support
# add new LLM settings below

class OpenAISettings(LLMSettings):
    def __init__(self, api_key, model_name, per_input_token_cost, per_output_token_cost):
        super().__init__(per_input_token_cost, per_output_token_cost) 
        self.api_key = api_key
        self.model_name = model_name

    def validate(self):
        if self.api_key is None or self.api_key == '' or self.model_name is None or self.model_name == '':
            # If API key or endpoint is missing, raise an error
            logger.error("OpenAI API key or model name is missing.")
            raise LLMSettingsException("OpenAI API key or model name is not set.")


class AzureOpenAISettings(LLMSettings):
    def __init__(self, azure_openai_api_key, azure_openai_endpoint, azure_deployment_model, azure_openai_api_version, 
                 per_input_token_cost, per_output_token_cost):
        super().__init__(per_input_token_cost, per_output_token_cost) 
        self.azure_openai_api_key = azure_openai_api_key
        self.azure_openai_endpoint = azure_openai_endpoint
        self.azure_deployment_model = azure_deployment_model
        self.azure_openai_api_version = azure_openai_api_version
        

    def validate(self):
        if self.azure_openai_api_key is None or self.azure_openai_api_key == '' or self.azure_openai_endpoint is None or self.azure_openai_endpoint == '':
            # If API key or endpoint is missing, raise an error
            logger.error("Azure OpenAI API key or endpoint is missing.")
            raise LLMSettingsException("Azure OpenAI API key or endpoint is not set.")


class GeminiSettings(LLMSettings):
    def __init__(self, api_key, model_name, per_input_token_cost, per_output_token_cost):
        super().__init__(per_input_token_cost, per_output_token_cost) 
        self.api_key = api_key
        self.model_name = model_name
        

    def validate(self):
        if self.api_key is None or self.api_key == '':
            # If API key or endpoint is missing, raise an error
            logger.error("Gemini LLM key is missing.")
            raise LLMSettingsException("Gemini LLM key is not set.")
        
        if self.model_name is None or self.model_name == '':
            # If API key or endpoint is missing, raise an error
            logger.error("Gemini LLM model name is missing.")
            raise LLMSettingsException("Gemini LLM model name is not set.")


# Note
# To add any new LLM support, we need to implement following interface in respective wrapper
class LLMProvider(abc.ABC):

    @abstractmethod
    def validate(self):
        """
        Validates the configuration settings for the LLM.
        """
        pass

    @abstractmethod
    def generate(self, system_prompt:str, user_prompt: str=None) -> tuple[str, TokenInfo]:
        """
        Sends the input prompt to the LLM and returns the generated response.
        """
        pass
    
    @abstractmethod
    def process_llm_output(self, input_data: str, data_type: str) -> str:
        """
        Processes and formats the LLM output for application use.
        """
        pass

    @abstractmethod
    def calculate_cost(self, input_tokens: int=0, output_tokens: int=0) -> float:
        """
        (Optional) Calculates the cost of an LLM call, if applicable.
        """
        pass
    
    @abstractmethod
    def generate_embedding(self, text:str):
        """
        Creates vector embedding for the given text.
        """
        pass