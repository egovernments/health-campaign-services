"""workflow.py: Base class for all workflow resources"""


__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

from abc import ABC, abstractmethod

# workflow task
class WorkflowTask(ABC):

    def __init__(self, task_name: str):
        self.task_name = task_name

    @abstractmethod
    def invoke(self, **kwargs) -> dict:
        """Takes a shared context dict, processes, and updates it."""
        pass

