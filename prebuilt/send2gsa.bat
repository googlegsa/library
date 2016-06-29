:: Copyright 2016 Google Inc. All Rights Reserved.
::
:: Licensed under the Apache License, Version 2.0 (the "License");
:: you may not use this file except in compliance with the License.
:: You may obtain a copy of the License at
::
::      http://www.apache.org/licenses/LICENSE-2.0
::
:: Unless required by applicable law or agreed to in writing, software
:: distributed under the License is distributed on an "AS IS" BASIS,
:: WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
:: See the License for the specific language governing permissions and
:: limitations under the License.
:: invoke send2gsa without keystore (the "--secure" option will fail)
java -jar send2gsa.jar %*
@Goto End
:: To enable secure sending of feeds, follow the instructions in chapter 4 of
:: the Administration Guide for Google Connectors at 
:: http://static.googleusercontent.com/media/www.google.com/en/us/support/enterprise/static/gsa/docs/admin/connectors/40/404/AdministrationGuideforGoogleConnectors.pdf
:: to create the cacerts.jks and keys.jks files.
:: Then replace the above java command with:
java ^
-Djavax.net.ssl.keyStore=keys.jks ^
-Djavax.net.ssl.keyStoreType=jks ^
-Djavax.net.ssl.keyStorePassword=changeit ^
-Djavax.net.ssl.trustStore=cacerts.jks ^
-Djavax.net.ssl.trustStoreType=jks ^
-Djavax.net.ssl.trustStorePassword=changeit ^
-Djsse.enableSNIExtension=false ^
-jar send2gsa.jar %*
:End
