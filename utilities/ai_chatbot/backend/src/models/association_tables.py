"""association_tables.py: mapping table"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from src.models import db
from sqlalchemy import Table, Column, Integer, ForeignKey

configuration_tag = Table(
    'configuration_tag_map', db.metadata,
    Column('configuration_id_fk', Integer, ForeignKey('configuration.id', ondelete='CASCADE'), primary_key=True),
    Column('tag_id_fk', Integer, ForeignKey('tag.id', ondelete='RESTRICT'), primary_key=True)
)

user_tag = Table(
    'user_tag_map', db.metadata,
    Column('user_id_fk', Integer, ForeignKey('userinfo.id', ondelete='CASCADE'), primary_key=True),
    Column('tag_id_fk', Integer, ForeignKey('tag.id', ondelete='RESTRICT'), primary_key=True)
)