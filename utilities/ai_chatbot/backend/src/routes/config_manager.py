"""config_manager.py: File contains logic for configs api """


__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

import json
from datetime import datetime
import threading
from flask import request, Blueprint, Response, current_app
from src.auth.auth_provider import check_user_role
from src.auth.base_auth_provider import UserRoles
from src.tasks.background_tasks import embedding_task
from src.utils.util import AlchemyHelper, CustomEncoder, read_config_file_data
from src.utils.util import base64_to_string, AlchemyHelper, CustomEncoder, convert_to_int_list
from src.utils.constants import DEFAULT_SQL_QUERY_GEN_PROMPT, DEFAULT_ELASTICSEARCH_QUERY_GEN_PROMPT, ExampleType, SupportedDBType
from src.models.config import Configuration
from src.models.example import Example
from src.repos.example_repo import ExampleRepo
from src.models.question import Question
from src.repos.tag_repo import TagRepo
from src.repos.config_repo import ConfigRepo
from src.repos.userinfo_repo import UserInfoRepo
from src.repos.macro_repo import MacroRepo
from src.helpers.superset_helper import get_dashboards
from error_handler.custom_exception import ResourceCreateException, ResourceUpdateException, ResourceNotFoundException, \
    InvalidDatabaseCredentialsException, InvalidParamException, DatabaseConnectionException, ResourceGetException, ResourceDeleteException, \
    GenericPlatformException

from src.config.logger_config import logger

configs_bp = Blueprint('configs', __name__)


# This method executes before any API request
@configs_bp.before_request
def before_request():
    logger.debug('Entering configs service.')


# This method executes after every API request.
@configs_bp.after_request
def after_request(response):
    logger.debug('Exiting configs service.')
    return response


@configs_bp.route('/configurations', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def get_all_configurations(user_info):
    try:
        logger.info("Entering get_all_configurations.")
        page = request.args.get('page', type=int, default=-1)
        per_page = request.args.get('per_page', type=int, default=-1)
        user = request.user
        is_admin = user['is_admin']
        user_id = user['user_id']

        items = None
        if is_admin :
            items = ConfigRepo().find_all(page, per_page)
        else:
            # get user tags and then confgurations associated with those tags
            tag_ids = UserInfoRepo().find_tag_ids_by_user_guid(user_id)
            items = ConfigRepo().find_all_by_tags(tag_ids, page, per_page)
        
        if not items:
            return Response("", status=200, mimetype='application/json')
        
        items = AlchemyHelper(obj=items).clean()
        jsonstr = json.dumps(items, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException("Cannot get all configurations.")
    finally:
        logger.info("Exiting get_all_configurations.")   


@configs_bp.route('/configurations/<id>', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def get_configuration_by_id(user_info, id):
    try:
        logger.info(f"Entering get_configuration_by_id {id}.")
        item = ConfigRepo().find_by_id(id)
        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except (ResourceNotFoundException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException(f"Cannot get configuration by {id}.")
    finally:
        logger.info("Exiting get_configuration_by_id.")   


@configs_bp.route('/configurations/defaultPrompt', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def get_default_prompt_template(user_info):
    try:
        logger.info(f"Entering get_default_prompt_template.")
        db_type = request.args.get('db_type', type=str, default='SQL')

        input_prompt = ''
        if(db_type == SupportedDBType.Elasticsearch.value):
            input_prompt = read_config_file_data(DEFAULT_ELASTICSEARCH_QUERY_GEN_PROMPT)
        else:
            input_prompt = read_config_file_data(DEFAULT_SQL_QUERY_GEN_PROMPT)

        return Response(input_prompt, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException("Cannot get default prompt.")
    finally:
        logger.info("Exiting get_default_prompt_template.")   


def load_examples(examples):
    new_examples = []
    existing_examples = []

    if not examples:
        return new_examples, existing_examples

    examples_list = json.loads(base64_to_string(examples))
    for ex in examples_list:
        id = ex['id']
        example = Example(
                key=ex['key'],
                value=ex['value'],
                type=ex['type']
            )
        
        if(id > 0):
            example.id = id
            existing_examples.append(example)
        else:
            new_examples.append(example)
    
    return new_examples, existing_examples


def load_questions(questions):
    new_questions = []
    existing_questions = []

    if not questions:
        return new_questions, existing_questions

    questions_list = json.loads(base64_to_string(questions))
    for qu in questions_list:
        id = qu['id']
        question = Question(
                detail=qu['detail']
            )
        
        if(id > 0):
            question.id = id
            existing_questions.append(question)
        else:
            new_questions.append(question)
    
    return new_questions, existing_questions


def __get_ds_config(data, user_name):
    name = data['name'].strip()
    description = data['description'].strip()
    is_active = data['is_enabled']
    tag_ids = data['tag_ids']
    conn_id = data['conn_id']
    model_id = data['model_id']
    examples_json =  data['examples']
    questions_json =  data['questions']
    
    # for SQL tables
    table_name = data.get('table_name', None)
    table_schema = data.get('table_schema', None)

    # for Elasticsearch index
    index_name = data.get('index_name', None)
    
    new_examples = []
    existing_examples = []
    try:
        new_examples, existing_examples = load_examples(examples_json)
        logger.debug(f"New examples {new_examples}")
        logger.debug(f"Existing examples {existing_examples}")
    except Exception as e:
        logger.error(f"Error parsing examples: {e.args}")
        raise InvalidParamException('examples')
    
    new_questions = []
    existing_questions = []
    try:
        new_questions, existing_questions = load_questions(questions_json)
        logger.debug(f"New questions {new_questions}")
        logger.debug(f"Existing questions {existing_questions}")
    except Exception as e:
        logger.error(f"Error parsing questions: {e.args}")
        raise InvalidParamException('questions')
    
    custom_prompt = data['custom_prompt']
    # code to check if we got valid base64 schema. This is getting in base64 to make sure special character are
    # taken care during UI to backend transfer
    if(custom_prompt is not None and custom_prompt != ''):
        base64_to_string(custom_prompt)

    business_rules = data['business_rules']
    # code to check if we got valid base64 schema. This is getting in base64 to make sure special character are
    if(business_rules is not None and business_rules != ''):
        base64_to_string(business_rules)

    # get tag ids in the list
    tag_list = convert_to_int_list(tag_ids)
    tags = TagRepo().find_by_id_list(tag_list)

    logger.debug(f"Conf name = {name}, user name = {user_name}, Table name = {table_name}, Index name = {index_name}")
    conf = Configuration(name, description, json.dumps(data), tags, user_name, is_active, datetime.now())
    #validating config before saving it
    conf.validate()
    return conf, new_examples, existing_examples, new_questions, existing_questions


def read_uploaded_file_content(req: request):
    if 'file' not in req.files:
        raise GenericPlatformException(f'No file found to upload data.')
    
    file = req.files['file']
    if file.filename == '':
        raise GenericPlatformException(f'Invalid file name.')
    
    try:
        content = file.read().decode('utf-8')

        # Parse JSON data
        data = json.loads(content)
        return data
    except json.JSONDecodeError as e:
        raise GenericPlatformException(f"Invalid JSON file format.")


@configs_bp.route('/configurations', methods=['POST'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def create_configuration(user_info):
    try:
        # Note
        # commented below code to read from body becasue sometime due to big datasize there is crash happening
        # so now reading data from the file and not from body
        data = request.json

        #data = read_uploaded_file_content(request)
        if(data is None or data == ''):
            raise InvalidParamException('Invalid configuration data.')
        
        logger.debug(data)

        user = request.user
        user_name = user['user_name']
        
        # currently all settings are enabled
        data['is_enabled'] = True

        config, new_examples, existing_examples, new_questions, existing_questions = __get_ds_config(data, user_name)
        config.examples = new_examples
        config.questions = new_questions

        # writing to DB connection details
        item = ConfigRepo().save(config)
        if not item:
            raise ResourceCreateException("configuration", config.name)
        
        # creating embedding thread
        thread = threading.Thread(target=embedding_task, args=(
            current_app._get_current_object(), item.id, item.examples,))
        thread.start()

        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=201, mimetype='application/json')
    except (InvalidParamException, ResourceCreateException, InvalidDatabaseCredentialsException, DatabaseConnectionException, GenericPlatformException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceCreateException('configuration')
    finally:
        logger.info("Exiting create_configuration.")


@configs_bp.route('/configurations/<id>', methods=['PUT'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def update_configuration(user_info, id):
    try:
        logger.info(f"Entering update_configuration {id}.")
        # Note
        # commented below code to read from body becasue sometime due to big datasize there is crash happening
        # so now reading data from the file and not from body
        data = request.json
        
        # data = read_uploaded_file_content(request)
        if(data is None or data == ''):
            raise Exception('Invalid configuration data.')
        
        user = request.user
        user_name = user['user_name']

        config, new_examples, existing_examples, new_questions, existing_questions = __get_ds_config(data, user_name)
        config.modified_by = user_name
        
        item = ConfigRepo().modify(id, config, new_examples, existing_examples, new_questions, existing_questions)
        if not item:
            raise ResourceUpdateException("configuration", config.name)
        
        # creating embedding thread
        thread = threading.Thread(target=embedding_task, args=(
            current_app._get_current_object(), item.id, item.examples,))
        thread.start()

        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except (InvalidParamException, ResourceNotFoundException, ResourceUpdateException, InvalidDatabaseCredentialsException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceUpdateException('configuration', id)
    finally:
        logger.info("Exiting update_configuration.")


@configs_bp.route('/configurations/<id>/updateStatus', methods=['PUT'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def update_status(user_info, id):
    try:
        logger.info(f"Entering update_status {id}.")
        data = request.json
        if(data is None or data == ''):
            raise Exception('Invalid configuration data.')
        
        user = request.user
        user_name = user['user_name']
        status = data['status']
        item = ConfigRepo().modify_status(id, status, user_name)
        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceUpdateException("configuration", id)
    finally:
        logger.info("Exiting update_status.")


@configs_bp.route('/configurations/<id>', methods=['DELETE'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def delete_configuration(user_info, id):
    try:
        logger.info(f"Entering delete_configuration {id}.")
        ConfigRepo().delete_by_id(id)
        return Response("", status=204, mimetype='application/json')
    except (ResourceNotFoundException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceDeleteException('configuration', id)
    finally:
        logger.info("Exiting delete_configuration.")


@configs_bp.route("/configurations/<id>/dashboards", methods=["GET"])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def get_dataset_dashboards(user_info, id):
    try:
        logger.info(f"Entering get_dataset_dashboards {id}.")
        
        user = request.user
        is_admin = user['is_admin']
        user_id = user['user_id']

        item = ConfigRepo().find_by_id(id)
        conf_tag_names = [tag.name for tag in item.tags]
        
        final_tag_names = None
        if is_admin:
            final_tag_names = conf_tag_names
        else:
            items = UserInfoRepo().find_tags_by_user_guid(user_id)
            if not items:
                return Response("", status=200, mimetype='application/json')

            user_tag_names = [item.name for item in items]
            final_tag_names = [name for name in user_tag_names if name in conf_tag_names]
        
        if(final_tag_names is None or len(final_tag_names) <= 0):
            return Response("", status=200, mimetype='application/json')
        
        items = get_dashboards(final_tag_names)
        jsonstr = json.dumps(items, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException("Cannot get dashboards.")
    finally:
        logger.info("Exiting get_dataset_dashboards.")


@configs_bp.route('/configurations/macros', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def get_all_macros(user_info):
    try:
        logger.info("Entering get_all_macros.")
        macros = MacroRepo().find_all()
        if not macros:
            return Response("", status=200, mimetype='application/json')
            
        macros = AlchemyHelper(obj=macros).clean()
        jsonstr = json.dumps(macros, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException(f"Cannot get macros.")
    finally:
        logger.info("Exiting get_all_macros.")


@configs_bp.route('/configurations/<id>/examples', methods=['POST'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def add_config_example(user_info, id):
    try:
        logger.info(f"Entering add_config_example {id}.")
        data = request.json
        if(data is None or data == ''):
            raise Exception('Invalid example data.')
        
        key = data.get('key')
        value = data.get('value')
        ex_type = data.get('type', ExampleType.SEMANTIC_EXAMPLE.value)
                
        config = ConfigRepo().find_by_id(id)
        if not config:
            raise ResourceNotFoundException(f"Cannot find configuration with ID {id}.")

        example = Example(key=key, value=value, type=ex_type)
        example.config_id = id
        item = ExampleRepo().save(example)

        # creating embedding thread
        thread = threading.Thread(target=embedding_task, args=(
            current_app._get_current_object(), id, [example],))
        thread.start()
        
        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=201, mimetype='application/json')
    except (ResourceNotFoundException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(f"Cannot add example to the configuration. {ex.args}")
        raise ResourceCreateException("example")
    finally:
        logger.info("Exiting add_config_example.")