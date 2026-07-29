apt-get update
apt-get install -y default-jdk unzip curl
wget https://services.gradle.org/distributions/gradle-8.0-bin.zip -P /tmp
unzip -d /opt/gradle /tmp/gradle-8.0-bin.zip
export PATH=$PATH:/opt/gradle/gradle-8.0/bin
gradle -v
