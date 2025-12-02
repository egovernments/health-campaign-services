"""app.py: flask app file"""

__author__ = "Umesh"
__copyright__ = "Copyright 2023, Prescience Decision Solutions"

import os
from dotenv import load_dotenv
from flask import Flask
from src.models import db
from flask_migrate import Migrate
from src.config.logger_config import logger
from src.helpers.job_scheduler import config_jobs, init_scheduler

load_dotenv()

def configure_db(app):
    try:
        logger.debug("Entering configure_db.")
        # service.config.from_object(config.StagingConfig)
        app.config['SQLALCHEMY_DATABASE_URI'] = os.environ['DATABASE_URL']

        app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
        #enabling sqlalchemy logs - True, False for disabling it
        app.config['SQLALCHEMY_ECHO'] = False

        db.init_app(app)
        migrate = Migrate(app, db)

        # models handling
        import src.models
        with app.app_context():
            db.create_all()

    except Exception as ex:
        logger.error(ex.args)
        raise Exception(f"DB configuration failed with error {ex.args}")
    finally:
        logger.debug("Exiting configure_db.")


def configure_cors(app):
    try:
        logger.debug("Entering configure_cors.")
        from flask_cors import CORS
        # CORS(app, resources={r"/api/*": {"origins": "*"}})
        CORS(app, origins=["*"])
    except Exception as ex:
        logger.error(ex.args)
        raise ex
    finally:
        logger.debug("Exiting configure_cors.")
    

def dump_environment_variables():
    for key, value in os.environ.items():
        logger.debug(f"{key}: {value}")


def create_app():
    try:
        logger.debug("Entering create_app.")
        app = Flask(__name__)

        # Configure cors
        configure_cors(app)
        
        # registering routes
        from src.routes import configure_routes
        configure_routes(app)
        
        from error_handler import configure_errors_handler
        # Configure exception handler
        configure_errors_handler(app)
        
        # Configure db
        configure_db(app)

        # init scheduler,
        with app.app_context():
            init_scheduler(app)

        #configuring jobs
        with app.app_context():
            config_jobs(app)
        
        return app
    except Exception as ex:
        logger.error(ex.args)
        raise ex
    finally:
        logger.debug("Exiting create_app.")


app = create_app()


@app.route('/')
def index():
    return app.send_static_file('index.html')


if __name__ == '__main__':
    host = '0.0.0.0'
    port = 5000
    app.run(host=host, port=port, debug=True)
