"""macro_repo.py: Macro repository functions"""

__author__ = "Praful Nigam"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

from src.models import db
from src.models.macro import Macro
from src.config.logger_config import logger


class MacroRepo() :
    def find_all(self):
        try:
            logger.debug(f"Entering find_all.")
            return Macro.query.order_by(Macro.id).all()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_all.")