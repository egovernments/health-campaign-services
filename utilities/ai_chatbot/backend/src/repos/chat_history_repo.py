"""chat_history_repo.py: Chat history repository functions"""

__author__ = "Bhuvan"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from src.models import db
from datetime import datetime, timedelta
from src.utils.constants import pagination
from src.models.chat_history import ChatHistory
from src.config.logger_config import logger


class ChatHistoryRepo() :
    
    def get_count(self):
        try:
            logger.debug(f"Entering get_count.")
            return ChatHistory.query.count()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug(f"Exiting get_count.")
    

    def find_all(self, page=-1, per_page=-1, order='asc'):
        try:
            logger.debug(f"Entering find_all {page}, {per_page}.")
            if not pagination(page, per_page):
                return ChatHistory.query.order_by(ChatHistory.id).all()
            else:
                return ChatHistory.query.order_by(ChatHistory.id).paginate(page=page, per_page=per_page).items
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all.")


    def find_all_by_session_id(self, session_id):
        try:
            logger.debug(f"Entering find_all_by_session_id {session_id}.")
            return ChatHistory.query.filter_by(session_id=session_id).order_by(ChatHistory.id.asc()).all()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all_by_session_id.")


    def find_all_messages_by_session_id(self, session_id, size=-1):
        try:
            logger.debug(f"Entering find_all_messages_by_session_id {session_id}.")
            history = None
            if size == -1:
                history = ChatHistory.query.filter_by(session_id=session_id).order_by(ChatHistory.id.asc()).all()
            else:
                history = ChatHistory.query.filter_by(session_id=session_id).order_by(ChatHistory.id.asc()).limit(size).all()

            messages = []
            for his in history:
                messages.append(his.message)
            
            return messages
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all_by_session_id.")


    def save(self, item):
        try:
            logger.debug("Entering save.")
            db.session.add(item)
            db.session.commit()
            return
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting save.") 


    def save_all(self, items):
        try:
            logger.debug("Entering save_all.")
            db.session.add_all(items)
            db.session.commit()
            return
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting save_all.") 
            

    def delete_history_by_retention_days(self, retention_days):
        try:
            logger.debug(f"Entering delete_history_by_retention_days {retention_days}.")
            old_date = datetime.now() - timedelta(days=retention_days)
            old_records = ChatHistory.query.filter(ChatHistory.created_at < old_date).all()

            for record in old_records:
                db.session.delete(record)

            db.session.commit()
            return len(old_records)
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting delete_history_by_retention_days.")
