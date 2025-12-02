"""
error_handler/__init__.py: api server configur error handler.
Usage : on your flask application instance, invoke configure_errors_handler(app) to configure error handler.
"""

__author__ = "Bhuvan"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from flask import jsonify, make_response
from error_handler.custom_exception import PlatformException
from src.auth.error_handler.auth_exception import PlatformException as AuthPlatformException
from src.config.logger_config import logger

def configure_errors_handler(app):
    try:
        logger.debug("Entering configure_errors_handler")
        @app.errorhandler(404)
        def handle_404_error(e):
            """Return a http 404 error to client"""
            return make_response(jsonify({'error_id': "0x0102", 'error': e.__str__()}), 404)

        @app.errorhandler(Exception)
        def handle_exception(e):
            if isinstance(e, PlatformException):
                return make_response(jsonify({'error_id': e.error_id, 'error': e.message}), e.code)
            elif isinstance(e, AuthPlatformException):
                return make_response(jsonify({'error_id': e.error_id, 'error': e.message}), e.code)
            return make_response(jsonify({'error_id': "0x0800", 'error': e.__str__()}), 500)

    except Exception as ex:
        logger.error(ex.args)
        raise ex
    finally:
        logger.debug("Exiting configure_custom_errors")
