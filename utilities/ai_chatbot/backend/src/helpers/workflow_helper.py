"""workflow_helper.py: Workflow helper file"""

__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

import os
from src.classes.token_info import TokenInfo
from src.utils.constants import SupportedDBType
from src.agents.es_tasks import IndexMetadataRetrievalTask, ESQueryValidatorTask, TextToESQueryTask, ESQueryExecutionTask, ESQueryToTextGeneratorTask
from error_handler.custom_exception import UnsupportedPlatformException, InvalidQuerySyntaxException, QueryGenerationException, \
                                DatabaseConnectionException, QueryRunException, LLMContextLengthException
from src.config.logger_config import logger

MAX_RETRY_COUNT = int(os.getenv('MAX_RETRY_COUNT', 3))

def setup_and_run_workflow(config_id, data, conn, question, examples, user_feedback_prompt, session_id, llm_wrapper):
    answer = None
    json_data = None
    generated_query = None
    token_info = TokenInfo()
    
    try:
        logger.debug("Entering setup_and_run_workflow.")
        datasets = None
        metadata_retrieval_agent = None
        text_to_query_agent = None
        query_validator_agent = None
        query_execution_agent = None
        llm_text_generator_agent = None
        
        db_type = data['db_type']
        if(db_type == SupportedDBType.Elasticsearch.value):
            # Add ES workflow tasks
            metadata_retrieval_agent = IndexMetadataRetrievalTask(config_id)
            text_to_query_agent = TextToESQueryTask(llm_wrapper)
            query_validator_agent = ESQueryValidatorTask()
            query_execution_agent = ESQueryExecutionTask()
            llm_text_generator_agent = ESQueryToTextGeneratorTask(llm_wrapper)
        else:
            raise UnsupportedPlatformException(f'Unsupported database type {db_type}.')

        retval = metadata_retrieval_agent.invoke(data, conn, datasets)
        metadata = retval['metadata']
        
        query_validation_result = False
        retry_count = 0
        error_messages = []
        while retry_count <= MAX_RETRY_COUNT:
            try:
                logger.debug(f"Running query generation, retry count = {retry_count}")
                generated_query = None
                retval = text_to_query_agent.invoke(data, conn, session_id, metadata, question, examples, 
                                                    user_feedback_prompt, error_messages)
                token_info = token_info + retval['token_info']
                generated_query = retval['query']

                if(generated_query is not None and generated_query != ''):
                    query_validation_response = query_validator_agent.invoke(data, conn, generated_query)
                    query_validation_result = query_validation_response['validation_result']
                    logger.debug(f'Query validation result = {query_validation_result}')
                    if query_validation_result:
                        break
                    else:
                        current_error_message = query_validation_response['message']
                        logger.debug(f'Query validation failed with error: {current_error_message}')
                        # Log and accumulate the validation error for next retry
                        error_messages.append(current_error_message)

                # when query is correct break the loop
                retry_count += 1
            except (QueryGenerationException, InvalidQuerySyntaxException) as ex:
                logger.warning(f"Query generation or validation failed. {ex.args}")
                current_error_message = str(ex.args) if ex.args else "Unknown error occurred"
                error_messages.append(current_error_message)
                retry_count += 1
                
        # after retry also valid query is not generated
        if generated_query is None or generated_query == '':
            raise QueryGenerationException
        
        if not query_validation_result:
            raise InvalidQuerySyntaxException
        
        retval = query_execution_agent.invoke(data, conn, generated_query)
        logger.debug(f'Query execution json = {retval}')
        json_data = retval['json_data']
        
        retval = llm_text_generator_agent.invoke(data, conn, json_data, question)
        token_info = token_info + retval['token_info']
                    
        answer = retval['llm_output']
        return answer, json_data, generated_query, token_info
    except (LLMContextLengthException, InvalidQuerySyntaxException, QueryRunException, DatabaseConnectionException, UnsupportedPlatformException, QueryGenerationException) as ex:
        logger.error(f"{ex.args}")
        return str(ex.message), None, generated_query, token_info
    except Exception as ex:
        logger.error(f"Error in executing workflow tasks. {ex.args}")
        return str(ex), None, generated_query, token_info
    finally:
        logger.debug("Exiting setup_and_run_workflow.")
