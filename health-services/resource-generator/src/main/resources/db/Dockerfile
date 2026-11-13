FROM egovio/flyway:10.7.1@sha256:42ecebdef3250cffd9f9e8cea0f2c279df10570aa07e8b3d3db41f00e4020779

# COPY ./migration/main /flyway/sql

COPY migrate.sh /usr/bin/migrate.sh

RUN chmod +x /usr/bin/migrate.sh

CMD ["/usr/bin/migrate.sh"]
