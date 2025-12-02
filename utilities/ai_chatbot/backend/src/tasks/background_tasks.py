"""background_tasks.py: background tasks code file"""

__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

from src.repos.model_info_repo import ModelInfoRepo
from src.repos.example_repo import ExampleRepo
from src.llm.llm_factory import get_llm_wrapper
from src.config.logger_config import logger

def embedding_task(app, id, examples):
    try:
        logger.debug(f"Starting embedding task for config id = {id}, examples = {len(examples)}.")
        with app.app_context():
            # get data profile
            
            if(len(examples) <= 0):
                return
            
            model_info = ModelInfoRepo().find_default()
            if(model_info is None):
                return
            
            llm_wrapper = get_llm_wrapper(model_info.type, model_info.get_settings())
            
            # get embedding for all examples one by one and save in DB
            for ex in examples:
                embed = llm_wrapper.generate_embedding(ex.key)
                ExampleRepo().modify_embedding(ex.id, embed)
                logger.debug(f"Embedding saved for example with id {ex.id}.")

        logger.debug(f"Embedding task completed for config id = {id}.")
    except Exception as ex:
        logger.error(f"Error in getting embedding. {ex.args}")
    finally:
        logger.debug("Ending embedding task.")