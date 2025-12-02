"""es_tasks.py: Elasticsearch tasks"""

__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

import json
from datetime import date
from src.classes.workflow import WorkflowTask
from src.db.db_factory import get_db_factory
from src.helpers.platform_config_helper import get_config_value
from src.utils.constants import PlatformConfiguration, DEFAULT_ELASTICSEARCH_QUERY_GEN_PROMPT, ELASTICSEARCH_QUERY_WITH_HISTORY_PROMPT, LLMOutputType
from src.utils.util import read_config_file_data, read_schema_file_data, base64_to_string, messages_from_dict, PromptTemplate
from src.helpers.macro_helper import find_all_unique_macros, replace_all_macros
from src.repos.chat_history_repo import ChatHistoryRepo
from src.config.logger_config import logger
from error_handler.custom_exception import DatabaseConnectionException, InvalidPromptException, LLMConnectionException, LLMGenericException, \
    FlaggedUserQueryException, QueryRunException, LLMContextLengthException


class ESQueryExecutionTask(WorkflowTask):
    
    def __init__(self):
        super().__init__(task_name="ESQueryExecutionTask")

    def invoke(self, data, conn, query: str)-> dict:
        try:
            logger.debug(f"Executing {self.task_name} task.")
            json_data = get_db_factory(conn["db_type"]).exec_query_and_read_data_as_json(data, conn, query)
            return {"json_data" : json_data}
        except (DatabaseConnectionException) as ex:
            logger.error(f"Error occurred in DB connection. {ex.args}")
            raise ex
        except (QueryRunException) as ex:
            logger.error(f"{ex.args}")
            raise ex
        except Exception as ex:
            logger.error(f"Error occurred in ES query execution. {ex.args}")
            raise ex
        finally:
            logger.debug(f"Completed {self.task_name} task run.")


class IndexMetadataRetrievalTask(WorkflowTask):
    
    def __init__(self, config_id):
        super().__init__(task_name="IndexMetadataRetrievalTask")
        self.config_id = config_id

    def invoke(self, data, conn, index_names:list)-> dict:
        try:
            logger.debug(f"Executing {self.task_name} task.")
            # Note
            # first reading from the schema file and if not found then only retrieving from the DB
            metadata = read_schema_file_data(self.config_id)
            if(metadata is None or metadata == ''):
                logger.debug('Reading Elasticsearch metadata from DB dynamically.')
                metadata = get_db_factory(conn["db_type"]).get_metadata(data, conn)
            else:
                logger.debug('Using custom schema file to load Elasticsearch metadata.')

            return {"metadata" : metadata}
        except (DatabaseConnectionException) as ex:
            logger.error(f"Error occurred in DB connection. {ex.args}")
            raise ex
        except Exception as ex:
            logger.error(f"Error occurred in index schema retrieval. {ex.args}")
            raise ex
        finally:
            logger.debug(f"Completed {self.task_name} task run.")


class ESQueryValidatorTask(WorkflowTask):

    def __init__(self):
        super().__init__(task_name="ESQueryValidatorTask")

    def invoke(self, data, conn, query: str )-> dict:
        try:
            logger.debug(f"Executing {self.task_name} task.")
            is_valid, message = get_db_factory(conn["db_type"]).validate_query(conn, query)
            return {"validation_result" : is_valid, "message": message}
        except (DatabaseConnectionException) as ex:
            logger.error(f"Error occurred in DB connection. {ex.args}")
            raise ex
        except Exception as ex:
            logger.error(f"Error occurred in ES query validation. {ex.args}")
            raise ex
        finally:
            logger.debug(f"Completed {self.task_name} task run.")


class TextToESQueryTask(WorkflowTask):

    def __init__(self, llm_wrapper):
        super().__init__(task_name="TextToESQueryTask")
        self.llm = llm_wrapper

    def get_system_prompt(self, data, conn, index_metadata, examples, user_feedback, error_messages: list=None):
        db_type = conn['db_type']
        index_name = data['index_name']
        custom_prompt = data['custom_prompt']

        input_prompt = None
        if(custom_prompt is not None and custom_prompt != ''):
            input_prompt = base64_to_string(custom_prompt)
        else:
            # read default prompt from config file and use it
            input_prompt = read_config_file_data(DEFAULT_ELASTICSEARCH_QUERY_GEN_PROMPT)
        
        business_rules = data['business_rules']
        if(business_rules is not None and business_rules != ''):
            business_rules = base64_to_string(business_rules)

            if(business_rules is not None and business_rules != ''):
                input_prompt = input_prompt + "\n\nBUSINESS RULES:\n" + business_rules  
        
        # checking for final prompt
        if input_prompt is None or input_prompt == '':
            raise InvalidPromptException('Invalid input prompt.')
        
        indices = [item.strip() for item in index_name.split(",")]
        macro_list = find_all_unique_macros(input_prompt)
        input_prompt = replace_all_macros(input_prompt, macro_list, None, db_type, indices, None, index_metadata)

        # adding examples if given
        if(examples is not None and examples != ''):
            input_prompt = input_prompt + "\n\nFEW-SHOT EXAMPLES:\n" + examples

        # adding user feedback also in the prompt
        if(user_feedback is not None and user_feedback != ''):
            input_prompt = input_prompt + "\n\nPAST FEEDBACK (User-validated examples of correct queries):\n" + user_feedback

        # adding error message from previous validation attempt if given
        if(error_messages is not None and len(error_messages) > 0):
            error_message_for_prompt = "\n\n".join([f"Attempt {i+1} Error:\n{msg}" for i, msg in enumerate(error_messages)])
            input_prompt = input_prompt + "\n\nPREVIOUS ATTEMPT ERROR:\nThe previous query generation attempts failed with the following error. Please fix with these errors in the query:\n" + error_message_for_prompt

        #logger.debug(f"Final system prompt: {input_prompt}")
        return input_prompt


    # making sure we aren't asking full data from Query
    def fix_size_in_query(self, es_query):
        max_count = get_config_value(PlatformConfiguration.QueryMaxRowCount)
        if not isinstance(es_query, dict):
            es_query = json.loads(es_query)

        if "size" in es_query and es_query["size"] > max_count:
            es_query["size"] = max_count

        return json.dumps(es_query, indent=2)


    def get_user_prompt(self, session_id, question):
        # read default prompt from config file and use it
        final_history_prompt = read_config_file_data(ELASTICSEARCH_QUERY_WITH_HISTORY_PROMPT)

        user_prompt_template = PromptTemplate(
                                input_variables=["CHAT_HISTORY", "QUESTION"],
                                template=final_history_prompt)

        # getting session history from DB
        history_size = get_config_value(PlatformConfiguration.HistoryMaxRowCount)

        chat_history_db = ChatHistoryRepo().find_all_messages_by_session_id(session_id, history_size)
        #formatted_chat_history = "\n".join([f"{q}\n{a}" for q, a in chat_history])

        user_prompt = user_prompt_template.format(CHAT_HISTORY=messages_from_dict(chat_history_db), 
                                                QUESTION=question)
        return user_prompt


    def invoke(self, data, conn, session_id, metadata, question: str, examples, user_feedback, error_messages: list=None) -> dict:
        try:
            logger.debug(f"Executing {self.task_name} task.")
            system_prompt = self.get_system_prompt(data, conn, metadata, examples, user_feedback, error_messages)
            #logger.debug(f"LLM system prompt = {system_prompt}")
            
            user_prompt = self.get_user_prompt(session_id, question)
            #logger.debug(f"########## User prompt for Elasticsearch query generation with history is : {user_prompt}")

            llm_content, tkn_info = self.llm.generate(system_prompt, user_prompt)
            es_query = self.llm.process_llm_output(llm_content, LLMOutputType.JSON.value)
            
            # fix query size to make sure we don't query all data from DB
            fixed_es_query = self.fix_size_in_query(es_query)
            return {"query" : fixed_es_query, "token_info" : tkn_info}
        except(LLMContextLengthException, LLMConnectionException, LLMGenericException, FlaggedUserQueryException) as ex:
            logger.error(f"{ex.args}")
            raise ex
        except Exception as ex:
            logger.error(f"Error occurred in ES query generation. {ex.args}")
            raise ex
        finally:
            logger.debug(f"Completed {self.task_name} task run.")


class ESQueryToTextGeneratorTask(WorkflowTask):

    def __init__(self, llm_wrapper):
        super().__init__(task_name="ESQueryToTextGeneratorTask")
        self.llm = llm_wrapper

    
    def get_system_prompt(self, db_type, index_name):
        # Read prompt data for the final natural language response
        final_prompt = read_config_file_data("es_query_to_nl_response_system_prompt")
        
        final_prompt = final_prompt.replace("{DB_TYPE}", db_type)
        final_prompt = final_prompt.replace("{INDEX_NAME}", index_name)
        final_prompt = final_prompt.replace("{CURRENT_DATE}", str(date.today()))
        
        #logger.debug(f"Final NL prompt = {final_prompt}")
        return final_prompt


    def get_user_prompt(self, question, result):
        # Read prompt data for the final natural language response
        prompt = read_config_file_data("es_query_to_nl_response_user_prompt")
        final_prompt = prompt.format(QUESTION=question, RESULT=result)
        
        #logger.debug(f"Final NL prompt = {final_prompt}")
        return final_prompt


    def invoke(self, data, conn, json_data, question: str)-> dict:
        try:
            logger.debug(f"Executing {self.task_name} task.")
            db_type = conn['db_type']
            index_name = data['index_name']

            system_prompt = self.get_system_prompt(db_type, index_name)
            user_prompt = self.get_user_prompt(question, json_data)

            llm_content, tkn_info = self.llm.generate(system_prompt, user_prompt)
            return {"llm_output" : llm_content, "token_info" : tkn_info}
        except(LLMContextLengthException, LLMConnectionException, LLMGenericException, FlaggedUserQueryException) as ex:
            logger.error(f"{ex.args}")
            raise ex
        except Exception as ex:
            logger.error(f"Error occurred in LLM text generation. {ex.args}")
            raise ex
        finally:
            logger.debug(f"Completed {self.task_name} task run.")