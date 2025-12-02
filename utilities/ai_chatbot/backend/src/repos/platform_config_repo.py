"""platform_confing_repo.py: Platform configuration repository functions"""

__author__ = "Umesh"
__copyright__ = "Copyright 2022, Prescience Decision Solutions"

from src.models import db
from src.models.platform_config import PlatformConfiguration
from datetime import datetime
from src.utils.util import pagination
from src.config.logger_config import logger
from error_handler.custom_exception import ResourceNotFoundException, ReadonlyPropertyException

from datetime import datetime

class PlatformConfigRepo() :

    def get_count(self):
        try:
            logger.debug(f"Entering get_count.")
            return PlatformConfiguration.query.count()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug(f"Exiting get_count.")

    
    def find_all(self, page=-1, per_page=-1, order='asc'):
        try:
            logger.debug(f"Entering find_all {page}, {per_page}.")
            if not pagination(page, per_page):
                return PlatformConfiguration.query.order_by(PlatformConfiguration.id).all()
            else:
                return PlatformConfiguration.query.order_by(PlatformConfiguration.id).paginate(page=page, per_page=per_page).items
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all.")


    def find_by_id(self, id):
        try:
            logger.debug(f"Entering find_by_id {id}.")
            item = PlatformConfiguration.query.get(id)
            if not item:
                raise ResourceNotFoundException(
                    f'Platform configuration {id} not found.', 404)
            return item
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_by_id.")


    def find_by_name(self, name):
        try:
            logger.debug(f"Entering find_by_name {name}.")
            item = PlatformConfiguration.query.filter_by(name=name).first()
            if not item:
                raise ResourceNotFoundException(
                    f'Platform configuration {name} not found.', 404)

            return item
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_by_name.")


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
            logger.debug("Entering modify.")
            item = self.find_by_id(id)
            #readonly property cannot be modified
            if(item.readonly):
                raise ReadonlyPropertyException

            item.name = config.name
            item.display_name = config.display_name
            item.type = config.type
            item.value = config.value
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
            

    def modify_by_name(self, name, config):
        try:
            logger.debug(f"Entering modify_by_name {name}.")
            item = self.find_by_name(name)
            #readonly property cannot be modified
            if(item.readonly):
                raise ReadonlyPropertyException

            item.name = config.name
            item.display_name = config.display_name
            item.type = config.type
            item.value = config.value
            item.is_active = config.is_active
            item.modified_by = config.modified_by
            item.updated_at = datetime.now()
            db.session.commit()
            return self.find_by_id(item.id)
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting modify_by_name.")


    def modify_status(self, id, status, user_id=None) :
        try:
            logger.debug("Entering modify_status.")
            item = PlatformConfiguration.query.get(id)
            if(item.readonly):
                raise ReadonlyPropertyException

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
            
            
    def delete_by_id(self, id):
        try:
            logger.debug("Entering delete_by_id.")
            item = PlatformConfiguration.query.get(id)
            if not item:
                raise ResourceNotFoundException(
                    f'Platform configuration {id} not found.', 404)

            if(item.readonly):
                raise ReadonlyPropertyException
            
            db.session.delete(item)
            db.session.commit()
            return
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting delete_by_id.")
