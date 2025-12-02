"""azure_openai_wrapper.py: Azure OpenAI wrapper"""

__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

from openai import OpenAIError
from openai import APIConnectionError, APITimeoutError, BadRequestError, RateLimitError, AuthenticationError
from openai import AzureOpenAI
from src.classes.token_info import TokenInfo
from src.utils.constants import LLMOutputType
from src.llm.llm_base import LLMProvider, AzureOpenAISettings
from error_handler.custom_exception import LLMConnectionException, LLMGenericException, FlaggedUserQueryException
from src.config.logger_config import logger


class AzureOpenAIWrapper(LLMProvider):

    def __init__(self, settings):
        # getting values from settings and not from env variable
        per_input_token_cost = settings.get('per_input_token_cost', 0.0)
        per_output_token_cost = settings.get('per_output_token_cost', 0.0)
        self.settings = AzureOpenAISettings(settings['azure_openai_api_key'], settings['azure_openai_endpoint'], 
                                                       settings['azure_deployment_model'], settings['azure_openai_api_version'],
                                                       per_input_token_cost, per_output_token_cost)

        # Initialize Azure OpenAI client directly
        self.llm = AzureOpenAI(
            api_key=self.settings.azure_openai_api_key,
            azure_endpoint=self.settings.azure_openai_endpoint,
            api_version=self.settings.azure_openai_api_version,
            timeout=None,
            max_retries=2
        )
        self.temperature = 0


    def validate(self):
        # validating Azure OpenAI keys
        self.settings.validate()


    def generate(self, system_prompt:str, user_prompt: str) -> tuple[str, TokenInfo]:
        logger.debug(f"Calling AzureOpenAIWrapper generate")

        try:
            # Use Azure OpenAI API directly
            llm_response = self.llm.chat.completions.create(
                model=self.settings.azure_deployment_model,
                messages=[{"role": "system", "content": system_prompt},
                            {"role": "user", "content": user_prompt}
                    ],
                temperature=self.temperature
            )
            
            #logger.debug(f'########### LLM Response : {llm_response}')
            # Extract token usage from direct Azure OpenAI response
            input_tokens = llm_response.usage.prompt_tokens
            output_tokens = llm_response.usage.completion_tokens
            total_tokens = llm_response.usage.total_tokens
            total_cost = round(self.calculate_cost(input_tokens, output_tokens), 6)
            tkn_info = TokenInfo(input_tokens, output_tokens, total_tokens, total_cost)
            # Extract content from response
            response_content = llm_response.choices[0].message.content
            return (response_content, tkn_info)
        except APIConnectionError as ex: 
            logger.error(f'OpenAI connection error. {ex.args}')
            raise LLMConnectionException
        except (APITimeoutError, BadRequestError, RateLimitError, AuthenticationError) as ex:
            logger.error(f'OpenAI error. {ex.args}')
            raise LLMGenericException
        except OpenAIError as ex:
            error_message = str(ex)
            if "content_filter" in error_message or "ResponsibleAIPolicyViolation" in error_message:
                logger.error("⚠️ Azure OpenAI blocked this request due to content filtering.")
                raise FlaggedUserQueryException('Azure OpenAI blocked the request due to content filtering.')
            else:
                raise ex
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
        logger.debug(f"Calling AzureOpenAIWrapper process_llm_output: {input_data} {data_type}")
       
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