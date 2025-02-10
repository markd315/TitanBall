sudo rm -rf /var/www/html/download_old/res
sudo rm -rf /var/www/html/download_old/Titanball.jar
sudo mv /var/www/html/download/Titanball.jar /var/www/html/download_old/Titanball.jar
sudo mv /var/www/html/download/res /var/www/html/download_old
sudo cp ~/TitanBall/target/Titanball.jar /var/www/html/download/Titanball.jar
sudo cp -r ~/TitanBall/res /var/www/html/download
sudo /usr/lib/jvm/openlogic-openjdk-17.0.14+7-linux-x64/bin/jar cf /var/www/html/download/res/res.jar -C /var/www/html/download/res .
java -classpath /var/www/html/download/getdown.jar com.threerings.getdown.tools.Digester /var/www/html/download