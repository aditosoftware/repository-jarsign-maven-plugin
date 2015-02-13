Overview
--------
This plugin tries to speed up jar signing especially when used together with tsa timestamping by a remote server.
To solve the problems jars are signed in local repository and not in the target folder thus each jar is signed only once and the next time the signature is reused.

Common usage
------------
```
<project>
  ...
  <plugins>
    <plugin>
      <groupId>de.adito</groupId>
      <artifactId>repository-jarsign-maven-plugin</artifactId>
      <version>1.0</version>
      <configuration>
        <alias>test</alias>
        <keystore>~/.testkeystore</keystore>
        <keypass>testing</keypass>
        <storepass>testing</storepass>
        <tsa>https://timestamp.geotrust.com/tsa</tsa>
        <forceSign>false</forceSign>
        <additionalManifestEntries>
          <TestEntry>testValue</TestEntry>
          <Codebase>just.a.test</Codebase>
        </additionalManifestEntries>
      </configuration>
    </plugin>
  </plugins>
  ...
</project>
```
