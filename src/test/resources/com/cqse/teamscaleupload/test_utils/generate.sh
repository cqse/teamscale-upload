#!/bin/bash
# regenerates the keystore.jks and truststore.jks files used in the tests
keytool -genkey -keyalg RSA -alias selfsigned -keystore keystore.jks -storepass password -validity 36500 -keysize 2048 -ext san=dns:localhost
keytool -export -alias selfsigned -file c.cer -keystore keystore.jks
keytool -importcert -file c.cer -keystore truststore.jks -alias selfsigned
rm c.cer
