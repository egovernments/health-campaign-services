"""connection_repo.py: Connection repository functions"""

__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

from datetime import datetime
from error_handler.custom_exception import ResourceNotFoundException
from src.models import db
from src.models.connection_info import ConnectionInfo
from src.utils.util import pagination
from src.config.logger_config import logger


class ConnectionRepo() :

    def get_count(self):
        try:
            logger.debug(f"Entering get_count.")
            return ConnectionInfo.query.count()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug(f"Exiting get_count.")

    
    def find_all(self, page=-1, per_page=-1, order='asc'):
        try:
            logger.debug(f"Entering find_all {page} {per_page}.")
            if not pagination(page, per_page):
                return ConnectionInfo.query.order_by(ConnectionInfo.id).all()
            else:
                return ConnectionInfo.query.order_by(ConnectionInfo.id).paginate(page=page, per_page=per_page).items
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all.")


    def find_by_id(self, id):
        try:
            logger.debug(f"Entering find_by_id {id}.")
            item = ConnectionInfo.query.get(id)
            if not item:
                raise ResourceNotFoundException(
                    f'Connection {id} not found.', 404)
            return item
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_by_id.")


    def find_by_name(self, name):
        try:
            logger.debug(f"Entering find_by_name. {name}")
            item = ConnectionInfo.query.filter_by(name=name).first()
            if not item:
                raise ResourceNotFoundException(
                    f'Connection {name} not found.', 404)

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


    def modify(self, id, conn):
        try:
            logger.debug(f"Entering modify {id}.")
            item = self.find_by_id(id)
            item.name = conn.name
            item.description = conn.description
            item.data = conn.data
            item.tested_at = conn.tested_at
            item.test_status = conn.test_status
            item.is_active = conn.is_active
            item.modified_by = conn.modified_by
            item.updated_at = datetime.now()
            db.session.commit()
            return self.find_by_id(item.id)
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting modify.")
            

    def modify_by_name(self, name, conn):
        try:
            logger.debug(f"Entering modify_by_name {name}.")
            item = self.find_by_name(name)
            item.name = conn.name
            item.description = conn.description
            item.data = conn.data
            item.tested_at = conn.tested_at
            item.test_status = conn.test_status
            item.is_active = conn.is_active
            item.modified_by = conn.modified_by
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
    

    def modify_test_result(self, id, tested_at, status, user_id) :
        try:
            logger.debug("Entering modify_test_result.")
            item = ConnectionInfo.query.get(id)
            item.tested_at = tested_at
            item.test_status = status
            item.modified_by = user_id
            item.updated_at = datetime.now()
            db.session.commit()
            return self.find_by_id(item.id)
        except Exception as ex:
            logger.error(ex.args)
            return False
        finally:
            logger.debug("Exiting modify_test_result.")


    def delete_by_id(self, id):
        try:
            logger.debug(f"Entering delete_by_id {id}.")
            item = ConnectionInfo.query.get(id)
            if not item:
                raise ResourceNotFoundException(
                    f'Connection {id} not found.', 404)

            db.session.delete(item)
            db.session.commit()
            return
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting delete_by_id.")

