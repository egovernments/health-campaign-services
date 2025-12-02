"""example_repo.py: Example repository functions"""

__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

from src.models.example import Example
from error_handler.custom_exception import ResourceNotFoundException
from src.utils.constants import ExampleType
from src.config.logger_config import logger
from src.models import db


class ExampleRepo() :
    
    def find_by_id(self, id):
        try:
            logger.debug(f"Entering find_by_id {id}.")
            item = Example.query.get(id)
            if not item:
                raise ResourceNotFoundException(
                    f'Example {id} not found.', 404)
            return item
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_by_id.")


    def find_all_by_config_id(self, config_id, type=ExampleType.SEMANTIC_EXAMPLE.value):
        try:
            logger.debug(f"Entering find_all_by_config_id {config_id} {type}.")
            return Example.query.filter_by(config_id=config_id, type=type).order_by(Example.id.asc()).all()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all_by_config_id.")


    def save(self, item):
        try:
            logger.debug("Entering save.")
            db.session.add(item)
            db.session.commit()
            return self.find_by_id(item.id)
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting save.")


    def modify_embedding(self, id, embedding=None):
        try:
            logger.debug(f"Entering modify_embedding {id}.")
            item = self.find_by_id(id)
            item.embedding = embedding
            db.session.commit()
            return self.find_by_id(item.id)
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting modify_embedding.")