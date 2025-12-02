"""product_repo.py: ProductRepo repository functions"""

__author__ = "Umesh"
__copyright__ = "Copyright 2022, Prescience Decision Solutions"

from src.models import db
from src.models.product_info import ProductInfo
from src.config.logger_config import logger

class ProductRepo():

    def find_product_info(self):
        try:
            logger.debug("Entering find_product_info.")
            #getting first item from the the version info list
            return ProductInfo.query.first()
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        finally:
            logger.debug("Exiting find_product_info.")

