"""model_info_repo.py: Model info repository functions"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from error_handler.custom_exception import ResourceNotFoundException
from src.models import db
from src.models.model_info import LLMModelInfo
from datetime import datetime
from src.config.logger_config import logger


class ModelInfoRepo() :

    def get_count(self):
        try:
            logger.debug(f"Entering get_count.")
            return LLMModelInfo.query.count()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug(f"Exiting get_count.")


    def find_all(self):
        try:
            logger.debug(f"Entering find_all.")
            return LLMModelInfo.query.order_by(LLMModelInfo.id).all()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all.")


    def find_by_id(self, id):
        try:
            logger.debug(f"Entering find_by_id {id}.")
            item = LLMModelInfo.query.get(id)
            if not item:
                raise ResourceNotFoundException(
                    f'Model configuration {id} not found.', 404)
            return item
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_by_id.")


    def find_by_name(self, name):
        try:
            logger.debug(f"Entering find_by_name. {name}")
            item = LLMModelInfo.query.filter_by(name=name).first()
            if not item:
                raise ResourceNotFoundException(
                    f'Model configuration {name} not found.', 404)

            return item
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_by_name.")


    def find_default(self):
        try:
            logger.debug(f"Entering find_default.")
            item = LLMModelInfo.query.filter_by(is_default=True).first()
            if not item:
                raise ResourceNotFoundException(
                    f'Default model configuration not found.', 404)

            return item
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_default.")


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


    def modify(self, id, config):
        try:
            logger.debug(f"Entering modify {id}.")
            item = self.find_by_id(id)
            item.name = config.name
            item.description = config.description
            item.type = config.type
            item.data = config.data
            item.status = config.status
            item.is_default = config.is_default
            item.is_active = config.is_active
            item.modified_by = config.modified_by
            item.updated_at = datetime.now()
            db.session.commit()
            return self.find_by_id(item.id)
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting modify.")


    def modify_status(self, id, status, user_id=None):
        try:
            logger.debug(f"Entering modify_status {id} {status} {user_id}.")
            item = self.find_by_id(id)
            item.is_active = status
            if(user_id is not None):
                item.modified_by = user_id

            item.updated_at = datetime.now()
            db.session.commit()
            return self.find_by_id(item.id)
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting modify_status.")
    

    def set_default(self, id, is_default, user_id=None) :
        try:
            logger.debug(f"Entering set_default {id} {user_id}.")
            item = self.find_by_id(id)
            item.is_default = is_default
            if(user_id is not None):
                item.modified_by = user_id

            item.updated_at = datetime.now()
            db.session.commit()
            return self.find_by_id(id)
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting set_default.")


    def modify_default_for_all(self, id, user_id):
        try:
            logger.debug(f"Entering modify_default_for_all {id}.")

            LLMModelInfo.query.update({LLMModelInfo.is_default : False, LLMModelInfo.modified_by : user_id})
            LLMModelInfo.query.filter_by(id=id).update({LLMModelInfo.is_default : True, LLMModelInfo.modified_by : user_id})
            db.session.commit()
            return self.find_by_id(id)
            # old approach to set flag
            # items = self.find_all()
            # if(items is not None):
            #     #changing default to False for all existing tenant locations
            #     for item in items :
            #         self.set_default(item.id, False, user_id)
                
            #changing default to True for the given one
            # return self.set_default(id, True, user_id)
        except Exception as ex:
            logger.error(ex.args)
            return False
        finally:
            logger.debug("Exiting modify_default_flag.")


    def delete_by_id(self, id):
        try:
            logger.debug(f"Entering delete_by_id {id}.")
            item = LLMModelInfo.query.get(id)
            if not item:
                raise ResourceNotFoundException(
                    f'Model configuration {id} not found.', 404)

            db.session.delete(item)
            db.session.commit()
            return
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting delete_by_id.")
