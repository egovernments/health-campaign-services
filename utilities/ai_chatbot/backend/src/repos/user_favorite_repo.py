"""user_favorite_repo.py: User favorite repository functions"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from error_handler.custom_exception import ResourceNotFoundException
from src.models import db
from src.models.user_favorite import UserFavorite
from src.utils.util import pagination
from datetime import datetime
from src.config.logger_config import logger


class UserFavoriteRepo() :

    def get_count(self):
        try:
            logger.debug(f"Entering get_count.")
            return UserFavorite.query.count()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug(f"Exiting get_count.")

    
    def find_all(self, page=-1, per_page=-1, order='asc'):
        try:
            logger.debug(f"Entering find_all {page}, {per_page}.")
            if not pagination(page, per_page):
                return UserFavorite.query.order_by(UserFavorite.id).all()
            else:
                return UserFavorite.query.order_by(UserFavorite.id).paginate(page=page, per_page=per_page).items
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all.")


    def find_all_by_user(self, user_id, page=-1, per_page=-1, order='asc'):
        try:
            logger.debug(f"Entering find_all_by_user {user_id}, {page}, {per_page}.")
            if not pagination(page, per_page):
                return UserFavorite.query.filter_by(user_id=user_id).order_by(UserFavorite.id).all()
            else:
                return UserFavorite.query.filter_by(user_id=user_id).order_by(UserFavorite.id).paginate(page=page, per_page=per_page).items
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all_by_user.")


    def find_by_id(self, id):
        try:
            logger.debug(f"Entering find_by_id {id}.")
            item = UserFavorite.query.get(id)
            if not item:
                raise ResourceNotFoundException(
                    f'User favorite {id} not found.', 404)
            return item
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_by_id.")


    def find_by_name(self, name):
        try:
            logger.debug(f"Entering find_by_name. {name}")
            item = UserFavorite.query.filter_by(name=name).first()
            if not item:
                raise ResourceNotFoundException(
                    f'User favorite {name} not found.', 404)

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


    def modify(self, id, fav):
        try:
            logger.debug(f"Entering modify {id}.")
            item = self.find_by_id(id)
            item.name = fav.name
            item.description = fav.description
            item.data = fav.data
            item.type = fav.type
            item.user_id = fav.user_id
            item.modified_by = fav.modified_by
            item.updated_at = datetime.now()
            db.session.commit()
            return self.find_by_id(item.id)
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting modify.")
            

    def delete_by_id(self, id):
        try:
            logger.debug(f"Entering delete_by_id {id}.")
            item = UserFavorite.query.get(id)
            if not item:
                raise ResourceNotFoundException(
                    f'User favorite {id} not found.', 404)
            
            db.session.delete(item)
            db.session.commit()
            return
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting delete_by_id.")


    def delete_by_user_and_id(self, user_id, id):
        try:
            logger.debug(f"Entering delete_by_user_and_id {id}.")
            item = UserFavorite.query.filter_by(user_id=user_id, id=id).first()
            if not item:
                raise ResourceNotFoundException(
                    f'User favorite {id} not found.', 404)
            
            db.session.delete(item)
            db.session.commit()
            return
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting delete_by_user_and_id.")