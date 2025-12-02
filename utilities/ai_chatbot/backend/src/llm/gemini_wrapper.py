"""gemini_wrapper.py: Gemini wrapper"""

__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

import google.generativeai as genai
from src.classes.token_info import TokenInfo
from src.utils.constants import LLMOutputType
from src.llm.llm_base import LLMProvider, GeminiSettings
from src.config.logger_config import logger


class GeminiWrapper(LLMProvider):

    def __init__(self, settings):
        super().__init__()
        # getting values from settings and not from env variable
        per_input_token_cost = settings.get('per_input_token_cost', 0.0)
        per_output_token_cost = settings.get('per_output_token_cost', 0.0)
        self.settings = GeminiSettings(settings['api_key'], settings['model_name'], 
                                          per_input_token_cost, per_output_token_cost)
        
        genai.configure(api_key = settings['api_key'])
        self.llm = genai.GenerativeModel(settings['model_name'])


    def validate(self):
        # validating Gemini keys
        self.settings.validate()

   
    def generate(self, system_prompt:str, user_prompt: str) -> tuple[str, TokenInfo]:
        logger.debug(f"Calling GeminiWrapper generate.")
       
        try:
            tkn_info = None
            # creating unified prompt for Gemini model
            input_prompt = system_prompt + '\n\n' + user_prompt
            llm_response = self.llm.generate_content(input_prompt)
            logger.info(f'########### LLM Response : {llm_response}')
            if hasattr(llm_response, "usage_metadata"):
                usage = llm_response.usage_metadata
                input_tokens = usage.prompt_token_count
                output_tokens = usage.candidates_token_count
                total_tokens = usage.total_token_count
            
                total_cost = round(self.calculate_cost(input_tokens, output_tokens), 6)
                tkn_info = TokenInfo(input_tokens, output_tokens, total_tokens, total_cost)
            return (llm_response.text, tkn_info)
        except Exception as ex:
            logger.error(f"LLM output generation failed. {ex.args}")
            raise ex
        finally:
            logger.debug("Exiting generate.")


    def process_json_query(self, query: str) -> str:
        """
        Remove triple backticks and `json` label from the query.

        Args:
            query (str): The JSON query returned by OpenAI.

        Returns:
            str: The cleaned query.
        """
        start = query.find("```")
        end = query.rfind("```")
        if(start != -1 and end != -1):
            query = query[start + 3:end]  # +3 to skip the opening ``` and ending ```
        
        # if query still has json in the beginning, remove it
        if query.startswith("json") or query.startswith("JSON"):
            query = query[4:].strip()
            
        return query
    

    def process_sql_query(self, query: str) -> str:
        """
        Remove triple backticks and `sql` label from the query.

        Args:
            query (str): The SQL query returned by OpenAI.

        Returns:
            str: The cleaned query.
        """
        start = query.find("```")
        end = query.rfind("```")
        if(start != -1 and end != -1):
            query = query[start + 3:end]  # +3 to skip the opening ``` and ending ```
        
        # if query still has sql in the beginning, remove it
        if query.startswith("sql") or query.startswith("SQL"):
            query = query[3:].strip()
            
        return query


    def process_llm_output(self, input_data: str, data_type: str) -> str:
        logger.debug(f"Calling GeminiWrapper process_llm_output: {input_data} {data_type}")
       
        try:
            if(data_type == LLMOutputType.JSON.value):
                return self.process_json_query(input_data)
            elif(data_type == LLMOutputType.SQL.value):
                return self.process_sql_query(input_data)
            
            return input_data
        except Exception as ex:
            logger.error(f"LLM output generation failed. {ex.args}")
            raise ex
        finally:
            logger.debug("Exiting post_process_data.")


    def calculate_cost(self, input_tokens: int, output_tokens: int) -> float:
        input_cost = input_tokens*self.settings.per_input_token_cost
        output_cost = output_tokens*self.settings.per_output_token_cost
        return input_cost + output_cost
    

    def generate_embedding(self, text:str):
        return None