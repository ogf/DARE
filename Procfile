web:    java -Djava.security.manager -Djava.security.policy=target/server.policy -jar target/DARE-web-1.0.0-SNAPSHOT-standalone.jar -w target/DARE.war -p $PORT --mongo-db local
worker: java -jar target/DARE-workers-0.1-SNAPSHOT-standalone.jar -p $PORT --mongo-db local --ip-to-run-on 127.0.0.1
