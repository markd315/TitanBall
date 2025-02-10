sudo cp ../titanball.properties ./application.properties
sudo /opt/maven/bin/mvn clean install -Pshaded-client
sudo java -Xms128m -Xmx256m -jar target/loginloadbal.jar