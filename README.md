# repository-jarsign-maven-plugin
[![Build Status](https://travis-ci.org/jboesl/repository-jarsign-maven-plugin.svg?branch=master)](https://travis-ci.org/jboesl/repository-jarsign-maven-plugin)


Overview
--------
This plugin speeds up jar signing especially when used together with tsa time stamping by a remote server.
To solve this problem signed jars are cached in local repository. This way each jar that hasn't changed is signed only once and the next time the signature is reused.

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
        <repack>true</repack>
        <pack200>true</pack200>
      </configuration>
    </plugin>
  </plugins>
  ...
</project>
```
