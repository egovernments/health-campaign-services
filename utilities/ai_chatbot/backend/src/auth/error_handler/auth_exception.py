"""custom_exception.py: Custome exception handler. Status and error codes are defined as per
[#https://dev.pageseeder.com/api/http_codes.html"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

def _to_str_(self):
    elements = [f"exception ='{self.__class__.__name__}'"]
    for key, value in self.__dict__.items():
        elements.append("{key}='{value}'".format(key=key, value=value))
    return ', '.join(elements)


class PlatformException(Exception):
    code = 500
    error_id = "0x0800"


class InvalidAuthProvider(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self):
        self.message = f"Invalid auth provider."
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class NoTokenProvidedException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, code=401):
        self.code = code
        self.message = f"No token provided."
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class InvalidTokenException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, code=401):
        self.code = code
        self.message = f"Invalid token provided."
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class InactiveTokenException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, code=401):
        self.code = code
        self.message = f"User session expired."
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class MissingRequiredRolesException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, required_roles=None, code=403):
        self.code = code
        if(required_roles is None):
            self.message = f"Required role not found."
        else:    
            self.message = f"Required role {required_roles} not found."
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class MissingRequiredPermissionsException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, resource_name, scope, code=403):
        self.code = code
        self.message = f"User doesn't have {scope} permissions to access {resource_name} resource."
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class LoginException(PlatformException):
    """Exception raised for 401.

        Attributes:
            message -- explanation of the error
            code -- status code
            error_id -- error ids
                        0x0101 - Failed permission check if the user is not logged in
        """

    def __init__(self, message, code=401, error_id="0x0101"):
        self.code = code
        self.error_id = error_id
        self.message = message
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)


class LogoutException(PlatformException):
    """Exception raised for error.

        Attributes:
            message -- explanation of the error
    """
    def __init__(self, param=None):
        if(param is None):
            self.message = "Failed to logout."
        else:
            self.message = f"Failed to logout user {{0}}.".format(param)
            
        super().__init__(self.message)

    def __str__(self):
        return _to_str_(self)
