package de.adito.maven.plugin.jarsign;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.digest.*;
import org.codehaus.plexus.util.FileUtils;

import java.io.*;

/**
 * @author j.boesl, 27.08.15
 */
class SignChecksumHelper
{

  private Log log;
  private Digester digester;


  public SignChecksumHelper(Log pLog, Digester pDigester)
  {
    log = pLog;
    digester = pDigester;
  }

  boolean existingChecksumMatches(File pFile, String pChecksumPath) throws MojoExecutionException
  {
    String existingChecksum = readChecksum(pChecksumPath);
    return existingChecksum != null && calculateChecksum(pFile).equals(existingChecksum);
  }

  String readChecksum(String pChecksumPath)
  {
    try
    {
      File checksumFile = new File(pChecksumPath + _getPostfix());
      return FileUtils.fileRead(checksumFile);
    }
    catch (IOException e)
    {
      return null;
    }
  }

  void installSignChecksum(File pFile, String pChecksumPath) throws MojoExecutionException
  {
    String checksum = calculateChecksum(pFile);

    File checksumFile = new File(pChecksumPath + _getPostfix());
    log.debug("Installing checksum to " + checksumFile);
    try
    {
      checksumFile.getParentFile().mkdirs();
      FileUtils.fileWrite(checksumFile.getAbsolutePath(), "UTF-8", checksum);
    }
    catch (IOException e)
    {
      throw new MojoExecutionException("Failed to install checksum to " + checksumFile, e);
    }
  }

  String calculateChecksum(File pFile) throws MojoExecutionException
  {
    log.debug("Calculating " + digester.getAlgorithm() + " checksum for " + pFile);
    try
    {
      return digester.calc(pFile);
    }
    catch (DigesterException e)
    {
      throw new MojoExecutionException("Failed to calculate " + digester.getAlgorithm() + " checksum for "
                                           + pFile, e);
    }
  }

  private String _getPostfix()
  {
    return "." + digester.getAlgorithm().toLowerCase();
  }

}
