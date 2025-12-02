"""macro_helper.py: helper for system macros"""

__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

import re
from datetime import datetime, date, timedelta
from dateutil.relativedelta import relativedelta
from src.helpers.platform_config_helper import get_config_value
from src.utils.constants import SystemMacro, PlatformConfiguration
from error_handler.custom_exception import UndefinedMacroException
from src.config.logger_config import logger


def get_last_month_details():
    today = date.today()

    # Get the first day of the current month
    first_day_this_month = today.replace(day=1)

    # Get the last day of the previous month by subtracting one day
    last_day_prev_month = first_day_this_month - timedelta(days=1)

    # Get the first day of the previous month
    first_day_prev_month = last_day_prev_month.replace(day=1)

    return first_day_prev_month, last_day_prev_month


def resolve_macro(macro, user=None, db_type=None, table_names: list[str]=None, table_schema=None, table_info=None, chat_history=None):
    try:
        match macro:
            case SystemMacro.LOGIN_USER_ID.value:
                return ''
            case SystemMacro.USER_EMAIL_ID.value:
                return ''
            case SystemMacro.CURRENT_YEAR.value:
                return str(datetime.now().year)
            case SystemMacro.CURRENT_MONTH.value:
                return str(datetime.now().month)
            case SystemMacro.CURRENT_DATE.value:
                return str(date.today())
            case SystemMacro.LAST_YEAR.value:
                return str(datetime.now().year - 1)
            case SystemMacro.LAST_MONTH.value:
                return str(date.today() - relativedelta(months=1))
            case SystemMacro.LAST_MONTH_START_TO_END.value:
                first_day_prev_month, last_day_prev_month = get_last_month_details()
                return f'from {first_day_prev_month} to {last_day_prev_month}'
            case SystemMacro.YESTERDAY_DATE.value:
                return str(date.today() - timedelta(days=1))
            case SystemMacro.NEXT_YEAR.value:
                return str(datetime.now().year + 1)
            case SystemMacro.NEXT_MONTH.value:
                return str(date.today() + relativedelta(months=1))
            case SystemMacro.DB_TYPE.value:
                return "" if db_type is None else db_type
            case SystemMacro.TABLE_NAME.value:
                if(table_names is None or len(table_names) <= 0):
                    return ""
                
                table_names_str = ", ".join(f'"{t}"' for t in table_names)
                return table_names_str
            case SystemMacro.TABLE_SCHEMA.value:
                return "" if table_schema is None else table_schema
            case SystemMacro.TABLE_INFO.value:
                return "" if table_info is None else table_info
            case SystemMacro.INDEX_NAME.value:
                if(table_names is None or len(table_names) <= 0):
                    return ""
                
                table_names_str = ", ".join(f'"{t}"' for t in table_names)
                return table_names_str
            case SystemMacro.INDEX_INFO.value:
                return "" if table_info is None else table_info
            case SystemMacro.TOP_K.value:
                return str(get_config_value(PlatformConfiguration.QueryDefaultRowCount))
            case SystemMacro.MAX_ROWS.value:
                return str(get_config_value(PlatformConfiguration.QueryMaxRowCount))
            case SystemMacro.CHAT_HISTORY.value:
                if(chat_history is None or len(chat_history) <= 0):
                    return ""
                
                return str(chat_history)
            case _:
                raise UndefinedMacroException(f'Undefined system macro found {macro}.')
    except (UndefinedMacroException) as ex:
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        return ''
    

def resolve_macros(macro_names, user, db_type, table_names: list[str]=None, table_schema=None, table_info=None, chat_history=None):
    macros_with_value = {}
    for macro_name in macro_names:
        try:
            macro_value = resolve_macro(macro_name, user, db_type, table_names, table_schema, table_info, chat_history)
            macros_with_value[macro_name] = macro_value
        except (UndefinedMacroException) as ex:
            logger.debug(f"Ignoring this error and continuing. {ex.args}")

    return macros_with_value


def remove_duplicates(lst):
    seen = set()
    result = []
    for item in lst:
        if item not in seen:
            seen.add(item)
            result.append(item)
    return result


def find_all_unique_macros(text_to_search : str):
    macros_lst = re.findall(r"\{([A-Z_][A-Z0-9_]*)\}", text_to_search, flags=re.IGNORECASE)
    macros_lst = remove_duplicates(macros_lst)
    return macros_lst


class SafeDict(dict):
    def __missing__(self, key):
        return '{' + key + '}'


def replace_all_macros(search_replace_text : str, macros_list, user, db_type, table_names: list[str]=None, table_schema=None, table_info=None, chat_history=None):
    try:
        if(search_replace_text is not None and macros_list is not None and len(macros_list) > 0):
            macros_with_value = resolve_macros(macros_list, user, db_type, table_names, table_schema, table_info, chat_history)
            search_replace_text = search_replace_text.format_map(SafeDict(macros_with_value))
        
        return search_replace_text
    except Exception as ex:
        logger.error(ex.args)
        return None