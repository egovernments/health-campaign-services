"""llm_factory.py: LLM factory method"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from src.llm.azure_openai_wrapper import AzureOpenAIWrapper
from src.llm.openai_wrapper import OpenAIWrapper
from src.llm.gemini_wrapper import GeminiWrapper
from src.utils.constants import SupportedLLM
from error_handler.custom_exception import UnsupportedPlatformException

# Note
# Modify below to add a new LLM support
# also create a LLM wrapper corresponding to the LLM

def get_llm_wrapper(model_type, settings):
    if(model_type == SupportedLLM.AzureOpenAI.value):
        return AzureOpenAIWrapper(settings)
    elif(model_type == SupportedLLM.OpenAI.value):
        return OpenAIWrapper(settings)
    elif(model_type == SupportedLLM.Gemini.value):
        return GeminiWrapper(settings)
    
    raise UnsupportedPlatformException('Unsupported LLM type.')
    