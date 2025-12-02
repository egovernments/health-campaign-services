"""tag_repo.py: Tag repository functions"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from error_handler.custom_exception import ResourceNotFoundException
from src.models import db
from src.models.tag import Tag
from src.utils.util import pagination
from datetime import datetime
from src.config.logger_config import logger


class TagRepo() :

    def get_count(self):
        try:
            logger.debug(f"Entering get_count.")
            return Tag.query.count()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug(f"Exiting get_count.")

    
    def find_all(self, page=-1, per_page=-1, order='asc'):
        try:
            logger.debug(f"Entering find_all {page}, {per_page}.")
            if not pagination(page, per_page):
                return Tag.query.order_by(Tag.id).all()
            else:
                return Tag.query.order_by(Tag.id).paginate(page=page, per_page=per_page).items
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all.")


    def find_by_id(self, id):
        try:
            logger.debug(f"Entering find_by_id {id}.")
            item = Tag.query.get(id)
            if not item:
                raise ResourceNotFoundException(
                    f'Tag {id} not found.', 404)
            return item
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_by_id.")


    def find_by_id_list(self, id_list:list):
        try:
            logger.debug(f"Entering find_by_id_list {id_list}.")
            return Tag.query.filter(Tag.id.in_(id_list)).order_by(Tag.id).all()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_by_id_list.")


    def find_by_name(self, name):
        try:
            logger.debug(f"Entering find_by_name. {name}")
            item = Tag.query.filter_by(name=name).first()
            if not item:
                raise ResourceNotFoundException(
                    f'Tag {name} not found.', 404)

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


    def modify(self, id, tag):
        try:
            logger.debug(f"Entering modify {id}.")
            item = self.find_by_id(id)
            item.name = tag.name
            item.description = tag.description
            item.is_active = tag.is_active
            item.modified_by = tag.modified_by
            item.updated_at = datetime.now()
            db.session.commit()
            return self.find_by_id(item.id)
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting modify.")
            

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
    

    def delete_by_id(self, id):
        try:
            logger.debug(f"Entering delete_by_id {id}.")
            item = Tag.query.get(id)
            if not item:
                raise ResourceNotFoundException(
                    f'Tag {id} not found.', 404)

            if(item.configurations):
                raise Exception('Tag is mapped to configurations, so cannot be deleted.')

            if(item.userinfos):
                raise Exception('Tag is mapped to users, so cannot be deleted.')
            
            db.session.delete(item)
            db.session.commit()
            return
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting delete_by_id.")
