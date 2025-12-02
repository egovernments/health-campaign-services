"""chat_manager.py: APIs for chats """

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

import json
import uuid
from datetime import datetime
from flask import jsonify, request, Response, Blueprint
from src.models.chat_history import ChatHistory
from src.repos.chat_history_repo import ChatHistoryRepo
from src.models.chat_info import ChatInfo
from src.repos.config_repo import ConfigRepo
from src.repos.connection_repo import ConnectionRepo
from src.repos.chat_info_repo import ChatInfoRepo
from src.repos.userinfo_repo import UserInfoRepo
from src.repos.example_repo import ExampleRepo
from src.helpers.llm_helper import get_llm, get_embedding
from src.helpers.platform_config_helper import get_config_value
from src.utils.constants import PlatformConfiguration, ExampleType
from src.utils.util import AlchemyHelper, CustomEncoder, generate_prompt_from_examples, get_relevant_examples, get_relevant_history, generate_prompt_from_chat_history
from src.auth.auth_provider import check_user_role
from src.auth.base_auth_provider import UserRoles
from src.helpers.workflow_helper import setup_and_run_workflow
from error_handler.custom_exception import ResourceNotFoundException, ResourceGetException, InvalidConfigurationException, \
    ResourceDisabledException, MappingNotFoundException, InvalidPromptException, InvalidParamException, InvalidUserQueryException, \
    InvalidModelConfigurationException, ResourceUpdateException, InvalidDatabaseCredentialsException, DatabaseConnectionException,\
        InvalidConnInfoException, LLMConnectionException, LLMGenericException, FlaggedUserQueryException, LLMSettingsException, \
        UnsupportedPlatformException, LLMContextLengthException
from src.config.logger_config import logger

chats_bp = Blueprint('chats', __name__)


@chats_bp.before_request
def before_request():
    logger.debug('Entering chat service.')
    #logger.debug(f"Before request executed for = {request.path}")


# This method executes after every API request.
@chats_bp.after_request
def after_request(response):
    logger.debug('Exiting chat service.')
    return response


@chats_bp.route('/chats/count', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def get_count(user_info):
    try:
        logger.debug("Entering get_count.")
        user = request.user
        user_id = user['user_id']
        is_admin = user['is_admin']
        logger.debug(f"User id = {user_id} is_admin = {is_admin}")
        count = -1
        if is_admin:
            count = ChatInfoRepo().get_count()
        else:
            item = UserInfoRepo().find_by_guid(user_id)
            count = ChatInfoRepo().get_count_by_user(item.id)
        return Response(str(count), status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException(f'Cannot get count.')
    finally:
        logger.debug("Exiting get_count.")


@chats_bp.route('/chats', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def get_all_sessions(user_info):
    try:
        logger.info("Entering get_all_sessions.")
        page = request.args.get('page', type=int, default=-1)
        per_page = request.args.get('per_page', type=int, default=-1)

        user = request.user
        user_id = user['user_id']
        is_admin = user['is_admin']
        logger.debug(f"User id = {user_id} is_admin = {is_admin}")

        items = None
        if is_admin:
            items = ChatInfoRepo().find_all(page, per_page)
        else:
            item = UserInfoRepo().find_by_guid(user_id)
            items = ChatInfoRepo().find_all_by_user(item.id, page, per_page)

        if not items:
            return Response("", status=200, mimetype='application/json')

        items = AlchemyHelper(obj=items).clean()
        jsonstr = json.dumps(items, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException(f'Cannot get chat history.')
    finally:
        logger.info("Exiting get_all_sessions.")


@chats_bp.route('/chats/<session_id>', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def get_by_session_id(user_info, session_id):
    try:
        logger.info(f"Entering get_by_session_id {session_id}.")
        user = request.user
        user_id = user['user_id']
        is_admin = user['is_admin']
        logger.debug(f"User id = {user_id} is_admin = {is_admin}")

        items = None
        if is_admin:
            items = ChatInfoRepo().find_all_by_session_id(session_id)
        else:
            item = UserInfoRepo().find_by_guid(user_id)
            items = ChatInfoRepo().find_all_by_user_session_id(item.id, session_id)

        if not items:
            return Response("", status=200, mimetype='application/json')
        
        items = AlchemyHelper(obj=items).clean()
        jsonstr = json.dumps(items, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException(f'Cannot get chat history with session id {session_id}.')
    finally:
        logger.info("Exiting get_by_session_id.")


@chats_bp.route('/chats/<id>/updateFeedback', methods=['PUT'])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def update_feedback(user_info, id):
    try:
        logger.info(f"Entering update_feedback {id}.")
        data = request.json
        if(data is None or data == ''):
            raise Exception('Invalid configuration data.')
        
        user = request.user
        user_name = user['user_name']
        status = data['response_status']
        question_embedding = None

        chat_info = ChatInfoRepo().find_by_id(id)

        if status is not None and status:
            question_embedding = get_embedding(chat_info.question)
                    
        item = ChatInfoRepo().modify_feedback_status(id, status, question_embedding, user_name)
        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceUpdateException("chat information", id)
    finally:
        logger.info("Exiting update_feedback.")


def __check_user_config_mapping(user_id, config):
    try:
        # this will have all tag information
        config_tags = config.tags
        if(config_tags is None or not config_tags):
            raise MappingNotFoundException('Configuration to tag mapping not found.')
        
        tag_ids = UserInfoRepo().find_tag_ids_by_user_guid(user_id)
        if not tag_ids:
            raise MappingNotFoundException('User to tag mapping not found.')
        
        # Now match if user tag id is in config tags or not
        for user_tag_id in tag_ids:
            for conf_tag in config_tags:
                if(conf_tag.id == user_tag_id):
                    logger.debug('User to configuration mapping found.')
                    return True

        raise MappingNotFoundException('User to configuration mapping not found.')
    except Exception as ex:
        logger.error(ex.args)
        raise ex


# saving token info to DB
def __save_chat_info(token_info, user_id, config_id, model_config_id, session_id, 
                   question, rephrased_answer, sql_query, started_at, completed_at):

    if(token_info is not None):
        input_tokens = token_info.prompt_tokens
        output_tokens = token_info.completion_tokens
        total_tokens = token_info.total_tokens
        total_cost_usd = token_info.total_cost
    else:
        input_tokens = output_tokens = total_tokens = total_cost_usd = 'NA'

    token_details = {
            "input_tokens" : input_tokens, 
            "output_tokens" : output_tokens, 
            "total_tokens" : total_tokens, 
            "total_cost_USD" : total_cost_usd
        }
    
    logger.debug(f"########## LLM token usage is : {token_info}")

    json_data = {"user_question": question, "ai_response" : rephrased_answer, "sql_query" : sql_query}

    # saving chat info into the table for analysis
    id = ChatInfoRepo().save(ChatInfo(user_id, config_id, model_config_id, session_id, question, sql_query, json_data,
                                 json.dumps(token_details), 
                                    started_at, completed_at, datetime.now()))
    return id


@chats_bp.route('/chats/sendQuery', methods=['POST'])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def send_query_v2(user_info):
    try:
        logger.info("Entering send_query_v2.")

        question = request.json['question']
        session_id = request.json['session_id']
        config_name = request.json['ds_name']
        
        # when no session id is passed by client, we are generating it
        # otherwise reusing one, which is sent by client
        if(session_id is None or session_id == ''):
            session_id = str(uuid.uuid4())

        user = request.user
        user_name = user['user_name']
        user_id = user['user_id']
        is_admin = user['is_admin']

        logger.debug(f"User name = {user_name}, Question = {question}, Configuration name = {config_name}")

        if(question is None or question == '' or question.strip() == ''):
            raise InvalidUserQueryException

        config = ConfigRepo().find_by_name(config_name)
        if config is None:
            raise InvalidConfigurationException
        
        if not config.is_active:
            raise ResourceDisabledException('Configuration')
                        
        # checking if user is mapped to the config
        # admin will have permission to chat against all datasets
        # this check should only be done for USER role not Admin role
        if(not is_admin):
            __check_user_config_mapping(user_id, config)
        
        # reading json saved data from DB
        data = json.loads(config.data)

        conn_id = data['conn_id']
        if(conn_id is None or conn_id == '' or int(conn_id) <= 0):
            raise InvalidConnInfoException

        conn = ConnectionRepo().find_by_id(conn_id)
        if not conn.is_active:
            raise ResourceDisabledException('Connection')

        user_info = UserInfoRepo().find_by_guid(user_id)

        # getting all examples for the config and creating prompt text
        semantic_example_list = ExampleRepo().find_all_by_config_id(config.id, ExampleType.SEMANTIC_EXAMPLE.value)
        core_example_list = ExampleRepo().find_all_by_config_id(config.id, ExampleType.CORE_EXAMPLE.value)
        logger.debug(f"Core examples found = {len(core_example_list)}")

        min_score = get_config_value(PlatformConfiguration.SimilaritySearchThresholdScore)
        max_count = get_config_value(PlatformConfiguration.SimilaritySearchMaxCount)

        # getting configured LLM
        llm_wrapper, model_id = get_llm()

        question_embedding = llm_wrapper.generate_embedding(question)
        # filtering examples based on similarity search
        example_list = get_relevant_examples(question, semantic_example_list, question_embedding, max_count, min_score)
        logger.debug(f"Examples found after similarity search = {len(example_list)}")
       
        examples_prompt = generate_prompt_from_examples(core_example_list, example_list)
        logger.debug(f"Final example prompt = {examples_prompt}")

        correct_history_list = ChatInfoRepo().find_all_by_config_and_feedback(config.id, True)
        #logger.debug(f"Total correct history items found for the dataset = {len(correct_history_list)}")

        feedback_min_score = get_config_value(PlatformConfiguration.UserFeedbackSimilaritySearchThresholdScore)
        
        relevant_history = get_relevant_history(question, correct_history_list, question_embedding, max_count, feedback_min_score)
        #logger.debug(f"Total relevant history items found = {len(relevant_history)}")
        user_feedback_prompt = generate_prompt_from_chat_history(relevant_history)
        #logger.debug(f"Final user feedback prompt = {user_feedback_prompt}")

        start_time = datetime.now()
        rephrased_answer, json_data, generated_query, token_info = setup_and_run_workflow(config.id, data, 
                                                                                            json.loads(conn.data), 
                                                                                            question, examples_prompt, 
                                                                                            user_feedback_prompt, 
                                                                                            session_id, 
                                                                                            llm_wrapper)
        # writing history to DB
        end_time = datetime.now()

        logger.debug(f'Answer = {rephrased_answer} Generated query = {generated_query}')

        humanMessage = {"data":
            {"id": None, "name": None, "type": "human", "content": question},
            "type":"human" 
        }

        aiMessage = {"data":
            {"id": None, "name": None, "type": "ai", "content": rephrased_answer},
            "type":"ai"
        }
        
        user_msg = ChatHistory(session_id, humanMessage, end_time)
        ai_msg = ChatHistory(session_id, aiMessage, end_time)

        messages = [user_msg, ai_msg]
        ChatHistoryRepo().save_all(messages)
        
        # saving chat info for cost analysis
        chat_id = __save_chat_info(token_info, user_info.id, config.id, model_id, session_id, 
                                            question, rephrased_answer, generated_query, start_time, end_time)
        
        json_response = {
            "text": rephrased_answer,
            "data" : json_data,
            "query" : generated_query
        }
        
        #logger.debug(f"answer = {answer}")
        return jsonify({"question": question, "answer": json.dumps(json_response),
                        "session_id" : session_id, "chat_id" : chat_id})
    except (InvalidParamException, InvalidUserQueryException, ResourceNotFoundException, InvalidConfigurationException, 
            ResourceDisabledException, MappingNotFoundException, InvalidPromptException, InvalidModelConfigurationException,
            InvalidDatabaseCredentialsException, DatabaseConnectionException, InvalidConnInfoException, LLMConnectionException,
            LLMGenericException, FlaggedUserQueryException, LLMSettingsException, UnsupportedPlatformException, LLMContextLengthException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise Exception(f'Error in sending user query. {ex.args}')
    finally:
        logger.info("Exiting send_query_v2.")
