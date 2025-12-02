"""version_info.py: Application service helper"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from src.models import db
from src.utils.constants import TableNames
from src.classes.base import Base

class ProductInfo(Base, db.Model):
    __tablename__ = TableNames.PRODUCT_INFO.value

    product_name = db.Column('product_name', db.String(256), primary_key=True)
    company = db.Column('company', db.String(256), nullable=False)
    version = db.Column('version', db.String(128), nullable=False)
    created_at = db.Column('created_at', db.DateTime(timezone=True), nullable=False)
    updated_at = db.Column('updated_at', db.DateTime(timezone=True), nullable=True)
    
    def __init__(self, product_name, company, version, created_at, updated_at):
        self.product_name = product_name
        self.company = company
        self.version = version
        self.created_at = created_at
        self.updated_at = updated_at

#db.create_all()