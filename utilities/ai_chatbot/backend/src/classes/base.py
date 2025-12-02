"""base.py: Base class for all resources"""


__author__ = "Umesh"
__copyright__ = "Copyright 2022, Prescience Decision Solutions"

from abc import ABC, abstractmethod

class Base():
    
    def __init__(self, created_by, is_active, created_at, updated_at, deleted_at):
        self.created_by = created_by
        self.is_active = is_active
        self.created_at = created_at
        self.updated_at = updated_at
        self.deleted_at = deleted_at


    @abstractmethod
    def validate(self):
        pass


    @property
    def is_created(self):
        return False if self.created_at is None else True


    @property
    def is_deleted(self):
        return False if self.deleted_at is None else True

    @property
    def is_valid(self):
        return True if (self.deleted_at is None and self.is_active == True) else False