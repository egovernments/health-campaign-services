"""util.py: Utility functions """

__author__ = "Aryaroop"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

import os
import numpy as np
import requests
from datetime import date, datetime
import base64, configparser
from json import JSONEncoder
import uuid
from sqlalchemy.ext.declarative import DeclarativeMeta
from rapidfuzz import process, fuzz
from src.classes.base import Base
from src.utils.constants import PROMPT_FILE_PATH, SCHEMA_FILE_PATH, SupportedDBType, AllowedConnectionType, SentenceMatchingEngine
from src.config.logger_config import logger

SEARCH_ENGINE = os.getenv('SENTENCE_MATCH_ENGINE', SentenceMatchingEngine.SimilaritySearch.value)

class CustomEncoder(JSONEncoder):
    def default(self, obj):
        if isinstance(obj, (date, datetime)):
            return obj.isoformat()
        
        if isinstance(obj, uuid.UUID):
            return str(obj)
        
        # binary data cannot be serialized
        if isinstance(obj, bytes):
            return None
        
        #if isinstance(obj, Base):        
            #return obj.__dict__
        
        if (isinstance(obj, Base)):
            if hasattr(obj, '__dict__'):
                return obj.__dict__
            
    
class AlchemyHelper:
        
    def __init__(self, obj=None):
        self.obj = obj
        
    def clean(self):
        if(self.obj is None):
            return None

        try:
            if(type(self.obj) == list):
                for item in self.obj:
                    self.clean_item(item)
            else:
                self.clean_item(self.obj)

            return self.obj
        except Exception as ex:
            print(ex.args)

        return None

    #currently it is one level only, we need to fix this later for n levels
    def clean_item(self, item):
        try:
            if(item is None):
                return

            #print(f"Type of item = {type(item)}")
            if isinstance(item.__class__, DeclarativeMeta):
                #logger.debug("SQLAlchemy object")
                del item.__dict__['_sa_instance_state']
                            
                #iterating in the object and checking for inner object, if it is also SQLAlchemy object
                #using vars() method not dir(), dir() method gives internal attributes also which aren't required
                for attrb in vars(item):
                    try:
                        #print(f"{mem}, type = {type(mem)}")
                        val = item.__getattribute__(attrb)

                        if isinstance(val.__class__, DeclarativeMeta):
                            #logger.debug(f"SQLAlchmey sub object found. {val}")
                            del val.__dict__['_sa_instance_state']
                    except Exception as ex:
                        print(ex.args)
        except Exception as ex:
            print(ex.args)


class PromptTemplate:
    
    def __init__(self, template, input_variables):
        self.template = template
        self.input_variables = input_variables

    def format(self, **kwargs):
        try:
            for var in self.input_variables:
                if var not in kwargs:
                    raise ValueError(f"Missing input variable {var}.")
            return self.template.format(**kwargs)
        except Exception as ex:
            logger.error(ex.args)
            raise ex


def read_config_file_data(key):
    try:
        section = "llm"
        config = configparser.ConfigParser()
        config.read(PROMPT_FILE_PATH)
        if section not in config:
            return None
        
        if key not in config[section]:
            return None
        
        value = config[section][key]
        return value
    except Exception as ex:
        logger.error(ex.args)
        return None


def read_schema_file_data(index):
    try:
        custom_schema = None
        file_path = SCHEMA_FILE_PATH.replace("INDEX", str(index))
        with open(file_path, "r", encoding="utf-8") as f:
            custom_schema = f.read()

        return custom_schema
    except Exception as ex:
        logger.error(ex.args)
        return None


def string_to_base64(s):
    # Encode the string to bytes using UTF-8 encoding
    bytes_string = s.encode('utf-8')
    # Encode the bytes to Base64
    base64_bytes = base64.b64encode(bytes_string)
    # Convert the Base64 bytes to a string
    base64_string = base64_bytes.decode('utf-8')
    return base64_string


def base64_to_string(base64_string):
    # Decode the Base64 string to bytes
    decoded_bytes = base64.b64decode(base64_string)
    # Convert bytes to string
    decoded_string = decoded_bytes.decode('utf-8')  # assuming utf-8 encoding
    return decoded_string


def pagination(page=-1, per_page=-1):
    if(page == -1 or per_page == -1):
        return False
    
    return True


def convert_to_int_list(id_string):
    if(id_string is None or id_string == ''):
        return []
    
    return [int(id.strip()) for id in id_string.split(',') if id.strip().isdigit()]


def make_http_request(url, method="GET", headers=None, payload=None, query=None):
    logger.debug("Entering make_http_request.")
    try:
        if method == "GET":
            if(query is None):
                response = requests.get(url, headers=headers)
            else:
                response = requests.get(url, headers=headers, params=query)
        elif method == "POST":
            response = requests.post(url, json=payload, headers=headers)

        response.raise_for_status()
        return response.json()
    except Exception as ex:
        logger.error(f"Failed to call api. {ex.args}")
        raise ex
    finally:
        logger.debug("Exiting make_http_request.")


def is_supported_db(db_type):
    if db_type in (item.value for item in SupportedDBType):
        return True

    return False


def is_allowed_connection(conn_type):
    if conn_type in (item.name for item in AllowedConnectionType):
        return True

    return False


def generate_prompt_from_examples(core_example_list, semantics_examples):
    if not semantics_examples and not core_example_list:
        return None
    
    prompt_parts = []
    if core_example_list:
        for ex in core_example_list:
            prompt_parts.append(f"User Question: {ex.key}\nQuery:\n{ex.value}")
    
    if semantics_examples:
        for ex in semantics_examples:
            prompt_parts.append(f"User Question: {ex.key}\nQuery:\n{ex.value}")

    return "\n\n".join(prompt_parts)


def generate_prompt_from_chat_history(chat_history):
    if not chat_history:
        return None
    
    prompt_parts = []
    if chat_history:
        for ex in chat_history:
            prompt_parts.append(f"User Question: {ex.question}\nUser-validated Correct Query:\n{ex.response}")

    return "\n\n".join(prompt_parts)


def messages_from_dict(examples):
    if not examples:
        return None

    history = []
    for ex in examples:
        role = ex.get("data", {}).get("type", ex.get("type"))
        content = ex.get("data", {}).get("content", "")
        history.append(f"{role}: {content}")

    return "\n\n".join(history)


def cosine_similarity(vec1, vec2):
    vec1, vec2 = np.array(vec1), np.array(vec2)
    return np.dot(vec1, vec2) / (np.linalg.norm(vec1) * np.linalg.norm(vec2))


def get_relevant_examples(query: str, examples: list, embedding=None, top_k: int = 5, min_score: int = 40):
    if not examples:
        return []
    
    logger.debug(f"Using search engine = {SEARCH_ENGINE}")
    if(SEARCH_ENGINE == SentenceMatchingEngine.VectorEmbedding.value):
        return get_examples_using_embeddings(embedding, examples, top_k, min_score)
    elif(SEARCH_ENGINE == SentenceMatchingEngine.SimilaritySearch.value):
        return get_examples_using_similarity_search(query, examples, top_k, min_score)
    else: # default is similarity search
        return get_examples_using_similarity_search(query, examples, top_k, min_score)


def get_examples_using_similarity_search(query: str, examples: list, top_k: int = 5, min_score: int = 40):
    # Extract the question text
    questions = [ex.key for ex in examples]

    # Rank them by similarity
    results = process.extract(
        query,
        questions,
        scorer=fuzz.token_set_ratio,  # or fuzz.partial_ratio
        limit=top_k
    )

    # Map back to example records
    top_examples = []
    for match, score, idx in results:
        if(min_score > 0 and score >= min_score):
            logger.debug(f"{match}  -->  Similarity: {score:.2f} --> {idx}")
            top_examples.append(examples[idx])
    
    return top_examples


def get_examples_using_embeddings(question_embedding, examples: list, top_k: int = 5, min_score: int = 40):
    ranked_results = []
    for record in examples:
        sim = cosine_similarity(question_embedding, record.embedding)
        if(min_score > 0 and sim*100 >= min_score):
            logger.debug(f"{record.id} --> {record.key} --> {sim*100}")
            ranked_results.append((record, sim))

    # Sort descending by similarity
    ranked_results.sort(key=lambda x: x[1], reverse=True)
    
    top_examples = [ex for ex, sim in ranked_results[:top_k]]
    return top_examples


def get_history_using_embeddings(question_embedding, chat_history: list, top_k: int = 5, min_score: int = 40):
    ranked_results = []
    for record in chat_history:
        sim = cosine_similarity(question_embedding, record.embedding)
        if(min_score > 0 and sim*100 >= min_score):
            logger.debug(f"{record.id} --> {record.question} --> {sim*100}")
            ranked_results.append((record, sim))

    # Sort descending by similarity
    ranked_results.sort(key=lambda x: x[1], reverse=True)
    
    top_history = [ex for ex, sim in ranked_results[:top_k]]
    return top_history


def get_history_using_similarity_search(query: str, chat_history: list, top_k: int = 5, min_score: int = 40):
    # Extract the question text
    questions = [ex.question for ex in chat_history]

    # Rank them by similarity
    results = process.extract(
        query,
        questions,
        scorer=fuzz.token_set_ratio,  # or fuzz.partial_ratio
        limit=top_k
    )

    # Map back to example records
    top_history = []
    for match, score, idx in results:
        if(min_score > 0 and score >= min_score):
            logger.debug(f"{match}  -->  Similarity: {score:.2f} --> {idx}")
            top_history.append(chat_history[idx])
    
    return top_history

# Note
# this method we want to make sure only very closed ones are sent so high min score is used here
def get_relevant_history(query: str, chat_history: list, embedding=None, top_k: int = 5, min_score: int = 80):
    if not chat_history:
        return []
    
    logger.debug(f"Using search engine = {SEARCH_ENGINE}")
    if(SEARCH_ENGINE == SentenceMatchingEngine.VectorEmbedding.value):
        return get_history_using_embeddings(embedding, chat_history, top_k, min_score)
    elif(SEARCH_ENGINE == SentenceMatchingEngine.SimilaritySearch.value):
        return get_history_using_similarity_search(query, chat_history, top_k, min_score)
    else: # default is similarity search
        return get_history_using_similarity_search(query, chat_history, top_k, min_score)
