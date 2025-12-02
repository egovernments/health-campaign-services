"""custom_exception.py: Custome exception handler. Status and error codes are defined as per
[#https://dev.pageseeder.com/api/http_codes.html"""

__author__ = "Bhuvan"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

def _to_str_(self):
    elements = [f"exception ='{self.__class__.__name__}'"]
    for key, value in self.__dict__.items():
        elements.append("{key}='{value}'".format(key=key, value=value))
    return ', '.join(elements)


class PlatformException(Exception):
    code = 500
    error_id = "0x0800"


# HTTP Exception
class InvalidRequestException(PlatformException):
    """Exception raised for 400.

    Attributes:
        message -- explanation of the error
        code -- status code
        error_id -- error ids
                    0x0103 - Bad tunnel
                    0x0201 - Missing parameter
                    0x0202 - Group not found
                    0x204 - Member not found
                    0x208 - URI not found
                    0x2xx - Other failed requirement
    """

    def __init__(self, message, code=400, error_id="0x208"):
        self.code = code
        self.error_id = error_id
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class ResourceNotFoundException(PlatformException):
    """Exception raised for errors.

    Attributes:
        message -- explanation of the error
        code -- status code
        error_id -- error ids
                    0x0102 - Service Not Found

    """

    def __init__(self, message, code=404, error_id="0x0102"):
        self.code = code
        self.error_id = error_id
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class ContentTypeException(PlatformException):
    """Exception raised for errors.

    Attributes:
        message -- explanation of the error
        code -- status code
        error_id -- error ids
                    0x0104 - Bad content type

    """

    def __init__(self, message, code=406, error_id="0x0104"):
        self.code = code
        self.error_id = error_id
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class ServerException(PlatformException):
    """Exception raised for errors.

    Attributes:
        message -- explanation of the error
        code -- status code
        error_id -- error ids
                    0x0800 - Exception caught
                    0x0801 - Generator Exception

    """

    def __init__(self, message, code=500, error_id="0x0800"):
        self.code = code
        self.error_id = error_id
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class DatabaseException(PlatformException):
    """Exception raised for errors 502.

    Attributes:
        message -- explanation of the error
        code -- status code
        error_id -- error ids
                    0x0802 - Database error

    """

    def __init__(self, message, code=502, error_id="0x0802"):
        self.code = code
        self.error_id = error_id
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class DatabaseConnectionException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="Database connection error."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class GeneratorException(PlatformException):
    """Exception raised for errors.

    Attributes:
        message -- explanation of the error
        code -- status code
        error_id -- error ids
                    0x0600 - Generator not found
                    0x0601 - Generator instantiation error
                    0x0602 - Generator initialization error

    """

    def __init__(self, message, code=503, error_id="0x0600"):
        self.code = code
        self.error_id = error_id
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class ResourceCreateException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, param1, param2=None):
        if(param2 is None):
            self.message = f"Cannot create {{0}}.".format(param1)
        else:    
            self.message = f"Cannot create {{0}} with name {{1}}.".format(param1, param2)
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class ResourceUpdateException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, param1, param2=None):
        if(param2 is None):
            self.message = f"Cannot update {{0}}.".format(param1)
        else:
            self.message = f"Cannot update {{0}} with name {{1}}.".format(param1, param2)
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)
    

class ResourceDeleteException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, param1, param2=None):
        if(param2 is None):
            self.message = f"Cannot delete {{0}}.".format(param1)
        else:
            self.message = f"Cannot delete {{0}} with id {{1}}.".format(param1, param2)
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class ResourceGetException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class GenericPlatformException(PlatformException):
    """Exception raised for generic errors.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class InvalidParamException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, param=None):
        if(param is None):
            self.message = "Invalid param(s)."
        else:
            self.message = f"Invalid param {{0}}.".format(param)
            
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class UnsupportedActionException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="Unsupported action."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class ActionNotAllowedException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="Action not allowed."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class ResourceDisabledException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, param):
        self.message = f"{{0}} is inactive.".format(param)
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class InvalidResourceException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, param):
        self.message = f"{{0}} is either invalid or deleted.".format(param)
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class ResourceDeletedException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, param):
        self.message = f"{{0}} is already deleted.".format(param)
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class ReadonlyPropertyException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="Cannot update readonly property."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class UnsupportedPlatformException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="Unsupported platform type."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)
    

class InvalidUserRoleException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, param):
        if(param is None):
            self.message = "Invalid user role."
        else:
            self.message = f"Invalid user role {{0}}.".format(param)
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)
    

class InvalidUserDetailsException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="Invalid student details (name or guid)."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class MethodNotImplementedException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, param):
        if(param is None):
            self.message = "Method not implemented."
        else:
            self.message = f"Method {{0}} not implemented.".format(param)
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class InvalidConfigurationException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, param=None):
        if(param is None):
            self.message = "Invalid configuration(s)."
        else:
            self.message = f"Invalid configuration {{0}}.".format(param)
            
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class InvalidDatabaseCredentialsException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self):
        self.message = f"Invalid database credentials."
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class MappingNotFoundException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="Mapping not found."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class InvalidPromptException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="Invalid prompt details."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class InvalidUserQueryException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="Invalid user query."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class FlaggedUserQueryException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="LLM flagged user query."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class UnsupportedModelException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="Unsupported model."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class InvalidModelConfigurationException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, param=None):
        if(param is None):
            self.message = "Cannot find model configuration."
        else:
            self.message = f"Cannot find {{0}} model configuration.".format(param)
            
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class ResourceAlreadyExistsException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, param):
        self.message = f"{{0}} already exists.".format(param)
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class InvalidConnInfoException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="Invalid connection(s) details."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class UndefinedMacroException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="Undefined macro."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class LLMConnectionException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="LLM connection error."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class LLMGenericException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="LLM generic error."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)
    

class LLMContextLengthException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="LLM context length exceeded. Reduce input size or use a model with a larger context window."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class InvalidQuerySyntaxException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="Invalid SQL/Elasticsearch query syntax."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)
    

class QueryGenerationException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="Cannot generate SQL/Elasticsearch query."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)
    

class TestConnectionException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self):
        self.message = f"Error occurred in testing connection."
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class LLMSettingsException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="Invalid LLM settings."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class QueryRunException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="Cannot run SQL/ES query."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class ResourceInUseException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, message="Resource in use."):
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)
