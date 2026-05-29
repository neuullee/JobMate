.\gradlew.bat clean bootJar -x test
copy .\build\libs\jobmate-0.0.1-SNAPSHOT.jar .\app.jar
"web: java -jar app.jar" | Out-File -Encoding ascii Procfile
Compress-Archive -Path .\app.jar, .\Procfile -DestinationPath .\jobmate-deploy.zip -Force 