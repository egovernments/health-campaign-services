FROM egovio/apline-jre:8u121@sha256:e663151c5fdfa89bda49af65b3d477fa1433491290cd35c8f09afa16072f35c4

MAINTAINER eGov Foundation

COPY /target/egov-notification-push-1.0.0-SNAPSHOT.jar /opt/egov/egov-notification-push.jar

COPY start.sh /usr/bin/start.sh

RUN chmod +x /usr/bin/start.sh

CMD ["/usr/bin/start.sh"]
