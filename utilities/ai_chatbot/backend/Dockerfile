FROM tiangolo/uwsgi-nginx:python3.12

RUN apt-get update && apt-get install -y \
    build-essential \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*
    
ENV STATIC_URL /static
ENV STATIC_PATH /var/www/app/static

RUN python3 -m pip install 'setuptools<72.0.0'
RUN python3 -m pip install --no-cache-dir --upgrade \
    pip \
#    setuptools \
    wheel

COPY requirements.txt /var/www/requirements.txt
RUN pip install -r /var/www/requirements.txt

## COPY files
COPY . /var/www
WORKDIR /var/www

COPY entrypoint.sh /var/www/entrypoint.sh
RUN chmod +x /var/www/entrypoint.sh

# Install SQL Server drivers and tools
RUN apt-get update

# Set the entrypoint to the script
ENTRYPOINT ["/var/www/entrypoint.sh"]
