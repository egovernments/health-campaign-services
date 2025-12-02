"""chat_info_repo.py: Chat repository functions"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from datetime import datetime, timedelta
from src.models import db
from src.utils.util import pagination
from src.models.chat_info import ChatInfo
from error_handler.custom_exception import ResourceNotFoundException
from src.config.logger_config import logger


class ChatInfoRepo() :
    
    def get_count(self):
        try:
            logger.debug(f"Entering get_count.")
            return ChatInfo.query.count()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug(f"Exiting get_count.")


    def get_count_by_user(self, user_id):
        try:
            logger.debug(f"Entering get_count_by_user.")
            return ChatInfo.query.filter_by(user_id=user_id).count()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug(f"Exiting get_count_by_user.")


    def find_by_id(self, id):
        try:
            logger.debug(f"Entering find_by_id {id}.")
            item = ChatInfo.query.get(id)
            if not item:
                raise ResourceNotFoundException(
                    f'Chat information {id} not found.', 404)
            return item
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_by_id.")


    def find_all(self, page=-1, per_page=-1, order='asc'):
        try:
            logger.debug(f"Entering find_all {page}, {per_page}.")
            if not pagination(page, per_page):
                return ChatInfo.query.order_by(ChatInfo.id.desc()).all()
            else:
                return ChatInfo.query.order_by(ChatInfo.id.desc()).paginate(page=page, per_page=per_page).items
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all.")


    def find_all_by_user(self, user_id, page=-1, per_page=-1, order='asc'):
        try:
            logger.debug(f"Entering find_all_by_user {user_id}, {page}, {per_page}.")
            if not pagination(page, per_page):
                return ChatInfo.query.filter_by(user_id=user_id).order_by(ChatInfo.id.desc()).all()
            else:
                return ChatInfo.query.filter_by(user_id=user_id).order_by(ChatInfo.id.desc()).paginate(page=page, per_page=per_page).items
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all_by_user.")


    def find_all_by_session_id(self, session_id):
        try:
            logger.debug(f"Entering find_all_by_session_id {session_id}.")
            return ChatInfo.query.filter_by(session_id=session_id).order_by(ChatInfo.id.asc()).all()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all_by_session_id.")


    def find_all_by_user_session_id(self, user_id, session_id):
        try:
            logger.debug(f"Entering find_all_by_user_session_id {user_id} {session_id}.")
            return ChatInfo.query.filter_by(user_id=user_id, session_id=session_id).order_by(ChatInfo.id.asc()).all()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all_by_user_session_id.")


    def find_all_by_config_and_feedback(self, conf_id, feedback):
        try:
            logger.debug(f"Entering find_all_by_config_and_feedback {conf_id} {feedback}.")
            return ChatInfo.query.filter_by(config_id=conf_id, is_correct=feedback).order_by(ChatInfo.id.desc()).all()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all_by_config_and_feedback.")

    # Note
    # this method returns all correct ones across all configs
    def find_all_by_feedback(self, feedback):
        try:
            logger.debug(f"Entering find_all_by_feedback {feedback}.")
            return ChatInfo.query.filter_by(is_correct=feedback).order_by(ChatInfo.id.desc()).all()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all_by_feedback.")


    def save(self, item):
        try:
            logger.debug("Entering save.")
            db.session.add(item)
            db.session.commit()
            return item.id
        except Exception as ex:
            logger.error(ex.args)
            # ignoring it if we aren't able to save it
            #raise ex
        finally:
            logger.debug("Exiting save.")


    def modify_feedback_status(self, id, is_correct, embedding=None, user_id=None) :
        try:
            logger.debug(f"Entering modify_feedback_status {id} {is_correct} {user_id}.")
            item = self.find_by_id(id)
            item.is_correct = is_correct
            item.embedding = embedding
            if(user_id is not None):
                item.modified_by = user_id

            item.updated_at = datetime.now()
            db.session.commit()
            return self.find_by_id(item.id)
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting modify_feedback_status.")
    

    def modify_cached_status(self, id, is_cached, user_id=None) :
        try:
            logger.debug(f"Entering modify_cached_status {id} {is_cached} {user_id}.")
            item = self.find_by_id(id)
            item.is_cached = is_cached
            if(user_id is not None):
                item.modified_by = user_id

            item.updated_at = datetime.now()
            db.session.commit()
            return self.find_by_id(item.id)
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting modify_cached_status.")
    

    def find_all_by_retention_days(self, retention_period_in_days):
        try:
            logger.debug(f"Entering find_all_by_retention_days {retention_period_in_days}.")
            old_date = datetime.now() - timedelta(days=retention_period_in_days)
            return ChatInfo.query.filter(ChatInfo.created_at < old_date).order_by(ChatInfo.id.asc()).all()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all_by_retention_days.")


    # Note
    # deleting only history items which are not used for feedback
    def delete_chats_by_retention_days(self, retention_days):
        try:
            logger.debug(f"Entering delete_chats_by_retention_days {retention_days}.")
            old_date = datetime.now() - timedelta(days=retention_days)

            old_records = ChatInfo.query.filter(ChatInfo.created_at < old_date, ChatInfo.is_correct==None).all()
            for record in old_records:
                db.session.delete(record)

            db.session.commit()
            return len(old_records)
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting delete_chats_by_retention_days.")