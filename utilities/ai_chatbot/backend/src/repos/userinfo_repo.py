"""userinfo_repo.py: Tag repository functions"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from error_handler.custom_exception import ResourceNotFoundException
from src.models import db
from src.models.user_info import UserInfo
from datetime import datetime
from src.utils.util import pagination
from src.config.logger_config import logger


class UserInfoRepo() :

    def find_all(self, page=-1, per_page=-1, order='asc'):
        try:
            logger.debug(f"Entering find_all {page} {per_page}.")
            if not pagination(page, per_page):
                return UserInfo.query.order_by(UserInfo.id).all()
            else:
                return UserInfo.query.order_by(UserInfo.id).paginate(page=page, per_page=per_page).items
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all.")


    def find_by_id(self, id):
        try:
            logger.debug(f"Entering find_by_id {id}.")
            item = UserInfo.query.get(id)
            if not item:
                raise ResourceNotFoundException(
                    f'User {id} not found.', 404)
            return item
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_by_id.")


    def find_by_guid(self, guid:str):
        try:
            logger.debug(f"Entering find_by_guid {guid}.")
            item = UserInfo.query.filter_by(guid=guid).first()
            if not item:
                    raise ResourceNotFoundException(
                        f'User {guid} not found.', 404)
            
            return item
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_by_guid.")


    def find_by_user_name(self, user_name:str):
        try:
            logger.debug(f"Entering find_by_user_name {user_name}.")
            item = UserInfo.query.filter_by(user_name=user_name).first()
            if not item:
                raise ResourceNotFoundException(
                    f'User {user_name} not found.', 404)
            
            return item
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_by_user_name.")


    def find_tag_ids_by_user_guid(self, guid:str):
        try:
            logger.debug(f"Entering find_tag_ids_by_user_guid {guid}.")
            userinfo = UserInfo.query.filter_by(guid=guid).first()
            
            tag_ids = []
            for tag in userinfo.tags:
                tag_ids.append(tag.id)

            return tag_ids
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_tag_ids_by_user_guid.")


    def find_tags_by_user_guid(self, guid:str):
        try:
            logger.debug(f"Entering find_tags_by_user_guid {guid}.")
            userinfo = UserInfo.query.filter_by(guid=guid).first()
            if(userinfo is None):
                return None
            
            return userinfo.tags
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_tags_by_user_guid.")


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


    def modify(self, id, userinfo, modified_by):
        try:
            logger.debug(f"Entering modify {id}.")
            item = self.find_by_id(id)
            item.user_name = userinfo.user_name
            item.tags = userinfo.tags
            item.is_active = userinfo.is_active
            item.modified_by = modified_by
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
            item = UserInfo.query.get(id)
            if not item:
                raise ResourceNotFoundException(
                    f'User {id} not found.', 404)

            db.session.delete(item)
            db.session.commit()
            return
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting delete_by_id.")


    def check_and_save(self, item):
        try:
            logger.debug("Entering check_and_save.")
            existing_item = UserInfo.query.filter_by(guid=item.guid).first()
            # if user is not found, add it
            if not existing_item:
                db.session.add(item)
                db.session.commit()
                return self.find_by_id(item.id)
            else:
                return self.find_by_id(existing_item.id)
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting check_and_save.")
