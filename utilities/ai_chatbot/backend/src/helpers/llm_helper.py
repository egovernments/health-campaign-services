"""llm_helper.py: LLM helper file"""

__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

from src.llm.llm_factory import get_llm_wrapper
from src.repos.model_info_repo import ModelInfoRepo
from error_handler.custom_exception import InvalidModelConfigurationException, ResourceDisabledException
from src.config.logger_config import logger

def get_llm():
    try:
        logger.debug("Entering get_llm.")
        
        logger.debug(f"Using default LLM model configuration.")
        model_info = ModelInfoRepo().find_default()
        if(model_info is None):
            raise InvalidModelConfigurationException
        
        if(model_info.is_active == False):
            raise ResourceDisabledException('LLM model configuration')

        llm_wrapper = get_llm_wrapper(model_info.type, model_info.get_settings())
        if(llm_wrapper is None):
            raise Exception('Cannot get LLM model information.')
        
        # validating LLM wrapper
        llm_wrapper.validate()
        return llm_wrapper, model_info.id
    except Exception as ex:
        logger.error(f"Error in getting LLM. {ex.args}")
        raise ex
    finally:
        logger.debug("Exiting get_llm.")


def get_embedding(msg:str):
    try:
        llm_wrapper, model_id = get_llm()
        embedding = llm_wrapper.generate_embedding(msg)
        return embedding
    except Exception as ex:
        logger.error(f"Error in getting embedding. {ex.args}")
        return None