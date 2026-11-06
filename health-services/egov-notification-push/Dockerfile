FROM egovio/apline-jre:8u121

MAINTAINER eGov Foundation

COPY /target/egov-notification-push-1.0.0-SNAPSHOT.jar /opt/egov/egov-notification-push.jar

COPY start.sh /usr/bin/start.sh

RUN chmod +x /usr/bin/start.sh

CMD ["/usr/bin/start.sh"]
