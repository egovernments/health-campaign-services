# health-services-common

Should be used a dependency in HCM microservices. This module provides common services like, IdGenService, ServiceRequestClient and so on. This is majorly to avoid code duplication across services.

Some services like IdGenService will be available in the dependent service only if certain properties are declared for that dependent. This is to avoid creating the beans unnecessarily.

## Upload maven artifacts to nexus repository.

1. Add distributionManagement tag in pom.xml
    - Repository refers to release artifacts.
    - snapshotRepository refers to snapshot artifacts.
    ```
    <distributionManagement>
        <repository>
            <id>repo.digit.org</id>
            <name>eGov ERP Releases Repository</name>
            <url>https://nexus-repo.digit.org/nexus/content/repositories/releases/</url>
        </repository>
        <snapshotRepository>
            <id>repo.digit.org</id>
            <name>eGov ERP Snapshots Repository</name>
            <url>https://nexus-repo.digit.org/nexus/content/repositories/snapshots/</url>
        </snapshotRepository>
    </distributionManagement>
    ```

2. Add maven-deploy-plugin to pom.xml
    ```
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-deploy-plugin</artifactId>
        <version>2.8.2</version>
        <executions>
            <execution>
                <id>default-deploy</id>
                <phase>deploy</phase>
                <goals>
                    <goal>deploy</goal>
                </goals>
            </execution>
        </executions>
    </plugin>
    ```

3. Add servers tag in settings.xml
    - Default location for settings.xml is ```${user.home}/.m2/settings.xml```. If you do not want to use default settings.xml, you can create your own and use it using --settings or -s while running mvn commands.
   ```
   <servers>
        <server>
            <id>repo.digit.org</id>
            <username>${nexus.user}</username>
            <password>${nexus.password}</password>
        </server>
    </servers>
   ```
    - **NOTE: \<id> tag needs to be same in distributionManagement and server tag.**

4. Deploy to nexus.
   ```
   mvn clean deploy -Dmaven.test.skip=true [--settings customSettingXmlFilePath]
   ```
   --settings is optional. Default location for settings.xml is ```${user.home}/.m2/settings.xml```

# Add changes to libraries and push it onto repository.
1. Do your changes i.e create new classes, interfaces, testcases etc.
2. Change version number tag in pom.xml
    ```
    <version>1.0.1</version>
    ```
- **NOTE: if version is not changed/unique, nexus will not take the new changes and the command will fail**
3. Deploy to nexus.
    ```
        mvn clean deploy -Dmaven.test.skip=true [--settings customSettingXmlFilePath]
    ```
--settings is optional. Default location for settings.xml is ```${user.home}/.m2/settings.xml```

# How to use common libraries.
1. Add groupId, artifactId and version to dependency tag.
    ```
    <dependencies>
        <dependency>
           <groupId>org.digit.health</groupId>
           <artifactId>health-services-common</artifactId>
           <version>1.0.1</version>
        </dependency>
    </dependencies>
    ```
2. Add Repository tag in pom.xml
    ```
    <repositories>
        <repository>
            <id>repo.digit.org<</id>
            <name>eGov Public Repository Group</name>
            <url>https://nexus-repo.egovernments.org/nexus/content/groups/public/</url>
        </repository>
    </repositories>
    ```
