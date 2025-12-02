"""question_repo.py: Question repository functions"""

__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

from src.models.question import Question
from error_handler.custom_exception import ResourceNotFoundException
from src.config.logger_config import logger
from src.models import db


class QuestionRepo() :
    
    def find_by_id(self, id):
        try:
            logger.debug(f"Entering find_by_id {id}.")
            item = Question.query.get(id)
            if not item:
                raise ResourceNotFoundException(
                    f'Question {id} not found.', 404)
            return item
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_by_id.")


    def find_all_by_config_id(self, config_id):
        try:
            logger.debug(f"Entering find_all_by_config_id {config_id}.")
            return Question.query.filter_by(config_id=config_id).order_by(Question.id.asc()).all()
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
