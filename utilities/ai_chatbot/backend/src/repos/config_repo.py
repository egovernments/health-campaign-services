"""confing_repo.py: Configuration repository functions"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from datetime import datetime
from error_handler.custom_exception import ResourceNotFoundException
from src.models import db
from src.models.config import Configuration
from src.models.tag import Tag
from src.utils.util import pagination
from src.config.logger_config import logger


class ConfigRepo() :

    def get_count(self):
        try:
            logger.debug(f"Entering get_count.")
            return Configuration.query.count()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug(f"Exiting get_count.")

    
    def find_all(self, page=-1, per_page=-1, order='asc'):
        try:
            logger.debug(f"Entering find_all {page} {per_page}.")
            if not pagination(page, per_page):
                return Configuration.query.order_by(Configuration.id).all()
            else:
                return Configuration.query.order_by(Configuration.id).paginate(page=page, per_page=per_page).items
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all.")


    def find_all_by_tags(self, tag_ids: list[int], page=-1, per_page=-1, order='asc'):
        try:
            logger.debug(f"Entering find_all_by_tags {tag_ids} {page} {per_page}.")
            if not pagination(page, per_page):
                return Configuration.query.join(Configuration.tags).filter(Tag.id.in_(tag_ids)).order_by(Configuration.id).all()
            else:
                return Configuration.query.join(Configuration.tags).filter(Tag.id.in_(tag_ids)).order_by(Configuration.id).paginate(page=page, per_page=per_page).items
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all_by_tags.")


    def find_by_id(self, id):
        try:
            logger.debug(f"Entering find_by_id {id}.")
            item = Configuration.query.get(id)
            if not item:
                raise ResourceNotFoundException(
                    f'Configuration {id} not found.', 404)
            return item
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_by_id.")


    def find_by_name(self, name):
        try:
            logger.debug(f"Entering find_by_name. {name}")
            item = Configuration.query.filter_by(name=name).first()
            if not item:
                raise ResourceNotFoundException(
                    f'Configuration {name} not found.', 404)

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


    def modify(self, id, config, new_examples=None, existing_examples=None, new_questions=None, existing_questions=None):
        try:
            logger.debug(f"Entering modify {id}.")
            item = self.find_by_id(id)
            item.name = config.name
            item.description = config.description
            item.data = config.data
            item.tags = config.tags
            item.is_active = config.is_active
            item.modified_by = config.modified_by
            item.updated_at = datetime.now()
            
            # code to delete examples
            updated_example_ids = {ex.id for ex in existing_examples}
            examples_to_delete = [
                ex for ex in item.examples if ex.id not in updated_example_ids
            ]

            for ex_to_delete in examples_to_delete:
                db.session.delete(ex_to_delete)

            # map of existing examples
            id_to_example_map = {e.id: e for e in item.examples}

            # code for updating examples
            for updated in existing_examples:
                if updated.id in id_to_example_map:
                    original = id_to_example_map[updated.id]
                    # updating example fields
                    original.key = updated.key
                    original.value = updated.value
                    original.type = updated.type

            # adding new examples
            if new_examples:
                item.examples.extend(new_examples)

            # code to delete questions
            updated_question_ids = {ex.id for ex in existing_questions}
            questions_to_delete = [
                ex for ex in item.questions if ex.id not in updated_question_ids
            ]

            for ex_to_delete in questions_to_delete:
                db.session.delete(ex_to_delete)

            # map of existing questions
            id_to_question_map = {e.id: e for e in item.questions}

            # code for updating questions
            for updated in existing_questions:
                if updated.id in id_to_question_map:
                    original = id_to_question_map[updated.id]
                    # updating question fields
                    original.detail = updated.detail
    
            # adding new questions
            if new_questions:
                item.questions.extend(new_questions)

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
            item = Configuration.query.get(id)
            if not item:
                raise ResourceNotFoundException(
                    f'Configuration {id} not found.', 404)

            db.session.delete(item)
            db.session.commit()
            return
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting delete_by_id.")
