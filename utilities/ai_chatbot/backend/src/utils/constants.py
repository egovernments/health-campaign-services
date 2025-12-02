"""constants.py: File contains constants and enums"""

__author__ = "Umesh"
__copyright__ = "Copyright 2022, Prescience Decision Solutions"

from enum import Enum

PROMPT_FILE_PATH = "./src/config/config.ini"
SCHEMA_FILE_PATH = f"/app/data/schema_INDEX.txt"

DEFAULT_SQL_QUERY_GEN_PROMPT = 'default_sql_query_gen_prompt'
SQL_QUERY_WITH_HISTORY_PROMPT = 'sql_query_with_history_prompt'
SQL_QUERY_SECURITY_PROMPT_NAME = 'sql_security_prompt'

DEFAULT_ELASTICSEARCH_QUERY_GEN_PROMPT = 'default_elasticsearch_query_gen_prompt'
ELASTICSEARCH_QUERY_WITH_HISTORY_PROMPT = 'elasticsearch_query_with_history_prompt'

TABLES_NAME_EXTRACT_PROMPT = 'tables_name_extract_prompt'

DEFAULT_ADMIN = 'Admin'

class TableNames(Enum):
    PRODUCT_INFO = 'productinfo'
    CONNECTION = 'connectioninfo'
    CONFIG = 'configuration'
    MODEL_METADATA = 'modelmetadata'
    CHAT_HISTORY = 'chathistory'
    PLATFORM_CONFIG = 'platformconfig'
    TAG = 'tag'
    USER_INFO = 'userinfo'
    USER_FAVORITE = 'userfavorite'
    CHAT_INFO = 'chatinfo'
    EXAMPLE = 'example'
    MACRO = 'macro'
    QUESTION = 'question'


class FavoriteTypes(Enum):
    Query = 'Query'
    Dashboard = 'Dashboard'    

# these all supported by backend but not necessary to allowed in the application
class SupportedDBType(Enum):
    Elasticsearch = 'Elasticsearch'
    

# Note
# though backend may support many DB types but may allow limited connection types for it's clients like UI, API etc
class AllowedConnectionType(Enum):
    Elasticsearch = 'Elasticsearch'
    

# Note
# Modify below to add a new LLM support
class SupportedLLM(Enum):
    OpenAI='OpenAI'
    AzureOpenAI='Azure OpenAI'
    Gemini='Google Gemini'


class ModelStatus(Enum):
    NOT_INIT='Not Initialized'
    INITIALIZING='Initializing'
    INIT_COMPLETE='Initialized'
    INIT_FAILED='Initialization Failed'


def pagination(page=-1, per_page=-1):
    if(page == -1 or per_page == -1):
        return False
    
    return True


class ConfigDataType(Enum):
    String = 'String'
    Integer = 'Integer'
    Float = 'Float'
    Boolean = 'Boolean'
    Json = 'Json'


class PlatformConfiguration(Enum):
    UISettings = 'UISettings'
    ChatHistoryRetentionPeriod = 'ChatHistoryRetentionPeriod'
    ChatHistoryCleanupJobSchedule = 'ChatHistoryCleanupJobSchedule'
    QueryMaxRowCount = 'QueryMaxRowCount'
    HistoryMaxRowCount = 'HistoryMaxRowCount'
    QueryDefaultRowCount = 'QueryDefaultRowCount'
    SimilaritySearchThresholdScore = 'SimilaritySearchThresholdScore'
    SimilaritySearchMaxCount = 'SimilaritySearchMaxCount'
    UserFeedbackSimilaritySearchThresholdScore = 'UserFeedbackSimilaritySearchThresholdScore'


class ProductFeature(Enum):
    AIChatbot = 'AIChatbot'
    Dashboards = 'Dashboards'


class SystemMacro(Enum):
    LOGIN_USER_ID = 'LOGIN_USER_ID'
    USER_EMAIL_ID = 'USER_EMAIL_ID'
    CURRENT_DATE = 'CURRENT_DATE'
    CURRENT_YEAR = 'CURRENT_YEAR'
    CURRENT_MONTH = 'CURRENT_MONTH'
    LAST_YEAR = 'LAST_YEAR'
    LAST_MONTH = 'LAST_MONTH'
    LAST_MONTH_START_TO_END = 'LAST_MONTH_START_TO_END'
    YESTERDAY_DATE = 'YESTERDAY_DATE'
    NEXT_YEAR = 'NEXT_YEAR'
    NEXT_MONTH = 'NEXT_MONTH'
    DB_TYPE = 'DB_TYPE'
    TABLE_SCHEMA = 'TABLE_SCHEMA'
    TABLE_NAME = 'TABLE_NAME'
    CHAT_HISTORY = 'CHAT_HISTORY'
    MAX_ROWS = 'MAX_ROWS'
    TOP_K = 'TOP_K'
    TABLE_INFO = 'TABLE_INFO'
    INDEX_NAME = 'INDEX_NAME'
    INDEX_INFO = 'INDEX_INFO'
        

class ExampleType(Enum):
    CORE_EXAMPLE = 'Core'
    SEMANTIC_EXAMPLE = 'Semantic'
    

class TestStatus(Enum):
    PENDING = 'Pending'
    SUCCESS = 'Success'
    FAILED = 'Failure'
    INPROGRESS = 'InProgress'


class LLMOutputType(Enum):
    SQL = 'SQL'
    JSON = 'JSON'


class SentenceMatchingEngine(Enum):
    SimilaritySearch = 'Similarity_search'
    VectorEmbedding = 'Vector_embedding'


class KeyMap() :
    def __init__(self, name, display_name):
        self.name = name
        self.display_name = display_name
