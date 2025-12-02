"""token_info.py: Token info class"""


__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

import json

class TokenInfo():

    def __init__(self, prompt_tokens=0, completion_tokens=0, total_tokens=0, total_cost=0.0):
        self.prompt_tokens = prompt_tokens
        self.completion_tokens = completion_tokens
        self.total_tokens = total_tokens
        self.total_cost = total_cost


    def __str__(self):
        return json.dumps({
            "prompt_tokens": self.prompt_tokens,
            "completion_tokens": self.completion_tokens,
            "total_tokens" : self.total_tokens,
            "total_cost" : self.total_cost
        })
    

    def __add__(self, other):
        if(other is None):
            return self
        
        if isinstance(other, TokenInfo):
            return TokenInfo(self.prompt_tokens + other.prompt_tokens,
                             self.completion_tokens + other.completion_tokens,
                             self.total_tokens + other.total_tokens,
                             self.total_cost + other.total_cost)
        
        raise TypeError(f"Unsupported type for addition: {type(other)}")